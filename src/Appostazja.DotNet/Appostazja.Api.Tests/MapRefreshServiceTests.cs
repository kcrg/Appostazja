using System.Net;
using System.Text;
using ApostasyMap.Api.Configuration;
using ApostasyMap.Api.Data;
using ApostasyMap.Api.Services;
using Microsoft.Data.Sqlite;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging.Abstractions;
using static Appostazja.Api.Tests.SourceData;

namespace Appostazja.Api.Tests;

public sealed class MapRefreshServiceTests : IDisposable
{
    private static readonly Uri ResponsesUrl = new("https://source.test/responses.csv");
    private static readonly Uri CoordinatesUrl = new("https://source.test/map-data.json");

    private readonly string _directory = Path.Combine(Path.GetTempPath(), "appostazja-tests", Guid.NewGuid().ToString("N"));
    private readonly FakeSources _sources = new();
    private readonly ManualTime _time = new(new DateTimeOffset(2026, 11, 13, 12, 0, 0, TimeSpan.Zero));
    private readonly AppSettings _settings;
    private readonly MapStore _store;
    private readonly MapMemoryCache _cache = new();
    private readonly MapRefreshService _service;

    public MapRefreshServiceTests()
    {
        var configuration = new ConfigurationBuilder().AddInMemoryCollection(new Dictionary<string, string?>
        {
            ["Source:Url"] = ResponsesUrl.AbsoluteUri,
            ["Source:CoordinatesUrl"] = CoordinatesUrl.AbsoluteUri,
            ["Source:MaxResponseMegabytes"] = "1",
            ["Database:Path"] = Path.Combine(_directory, "test.db")
        }).Build();
        _settings = AppSettings.FromConfiguration(configuration, _directory);
        _store = new MapStore(_settings);
        _store.Initialize();
        _cache.Replace(_store.GetState(), _store.LoadPlaces(), _store.LoadReports());
        _service = new MapRefreshService(_settings, _store, _cache, new HttpClient(_sources),
            NullLogger<MapRefreshService>.Instance, _time);
    }

    [Fact]
    public async Task First_import_publishes_places_and_stores_only_the_sanitized_csv()
    {
        var (csv, geo) = Parishes(3);
        _sources.Serve(ResponsesUrl, csv, etag: "\"r1\"");
        _sources.Serve(CoordinatesUrl, geo);

        var result = await _service.RefreshAsync(force: false, CancellationToken.None);

        Assert.Equal("updated", result.Outcome);
        Assert.Equal(3, _cache.GetState().PlaceCount);
        Assert.Equal(3, _store.GetState().PlaceCount);
        var stored = Encoding.UTF8.GetString(_store.LoadInput(ResponsesUrl)!.Payload);
        Assert.DoesNotContain("submitted_at", stored);
        Assert.DoesNotContain("25-34", stored);
        Assert.Equal("updated", _store.GetState().LastAttemptOutcome);
    }

    [Fact]
    public async Task Unchanged_sources_answering_304_only_mark_the_dataset_as_checked()
    {
        var (csv, geo) = Parishes(3);
        _sources.Serve(ResponsesUrl, csv, etag: "\"r1\"");
        _sources.Serve(CoordinatesUrl, geo, etag: "\"g1\"");
        await _service.RefreshAsync(force: false, CancellationToken.None);
        var version = _cache.GetState().Version;

        _time.Advance(TimeSpan.FromDays(1));
        _sources.NotModifiedWhenEtagMatches = true;
        var result = await _service.RefreshAsync(force: false, CancellationToken.None);

        Assert.Equal("not-modified", result.Outcome);
        Assert.Equal(version, _cache.GetState().Version);
        Assert.Equal("\"r1\"", _sources.LastIfNoneMatch[ResponsesUrl]);
        Assert.Equal(_time.GetUtcNow().ToUnixTimeSeconds(), _store.GetState().LastCheckedAtUnixSeconds);
    }

    [Fact]
    public async Task Import_with_too_many_invalid_rows_is_rejected_and_old_data_is_kept()
    {
        await ImportParishes(20);
        var rows = Enumerable.Range(1, 20)
            .Select(i => Row($"Parafia {i}", $"Ulica {i}, Miasto", rating: i <= 2 ? "easy" : "new_rating"))
            .ToArray();
        _sources.Serve(ResponsesUrl, Csv(rows));
        _time.Advance(TimeSpan.FromDays(1));

        var error = await Assert.ThrowsAsync<InvalidDataException>(
            () => _service.RefreshAsync(force: false, CancellationToken.None));

        Assert.Contains("Too many invalid source rows", error.Message);
        Assert.Equal(20, _cache.GetState().PlaceCount);
        var state = _store.GetState();
        Assert.Equal("invalid-data", state.LastAttemptOutcome);
        Assert.Equal(error.Message, state.LastError);
        Assert.Equal(_time.GetUtcNow().ToUnixTimeSeconds(), state.LastAttemptAtUnixSeconds);
        Assert.Equal("invalid-data", _cache.GetState().LastAttemptOutcome);
    }

    [Fact]
    public async Task A_few_invalid_rows_are_tolerated_and_reported()
    {
        var rows = Enumerable.Range(1, 40).Select(i => Row($"Parafia {i}", $"Ulica {i}, Miasto"))
            .Append(Row("Parafia 1", "Ulica 1, Miasto", rating: "new_rating"))
            .ToArray();
        var (_, geo) = Parishes(40);
        _sources.Serve(ResponsesUrl, Csv(rows));
        _sources.Serve(CoordinatesUrl, geo);

        var result = await _service.RefreshAsync(force: false, CancellationToken.None);

        Assert.Equal("updated", result.Outcome);
        Assert.Equal(1, _store.GetState().SkippedRows);
    }

    [Fact]
    public async Task Import_that_loses_too_many_places_is_rejected()
    {
        await ImportParishes(20);
        var (csv, geo) = Parishes(10);
        _sources.Serve(ResponsesUrl, csv);
        _sources.Serve(CoordinatesUrl, geo);
        _time.Advance(TimeSpan.FromDays(1));

        var error = await Assert.ThrowsAsync<InvalidDataException>(
            () => _service.RefreshAsync(force: false, CancellationToken.None));

        Assert.Contains("dropped from 20 to 10", error.Message);
        Assert.Equal(20, _cache.GetState().PlaceCount);
    }

    [Fact]
    public async Task Http_failure_is_recorded_without_touching_the_dataset()
    {
        await ImportParishes(5);
        _sources.FailWith(ResponsesUrl, HttpStatusCode.BadGateway);
        _time.Advance(TimeSpan.FromDays(1));

        await Assert.ThrowsAsync<HttpRequestException>(
            () => _service.RefreshAsync(force: false, CancellationToken.None));

        var state = _store.GetState();
        Assert.Equal("failed", state.LastAttemptOutcome);
        Assert.Equal("Source returned HTTP 502.", state.LastError);
        Assert.Equal(5, state.PlaceCount);
    }

    [Fact]
    public async Task Oversized_source_is_rejected_with_a_clear_reason()
    {
        _sources.Serve(ResponsesUrl, new byte[2 * 1024 * 1024]);
        _sources.Serve(CoordinatesUrl, Parishes(1).GeoJson);

        var error = await Assert.ThrowsAsync<InvalidDataException>(
            () => _service.RefreshAsync(force: false, CancellationToken.None));

        Assert.Contains("MaxResponseMegabytes", error.Message);
    }

    [Fact]
    public void Upgrading_an_old_database_strips_private_columns_and_forces_a_rebuild()
    {
        var raw = Csv(Row("Parafia", "Adres 1", age: "65+"));
        using (var connection = new SqliteConnection($"Data Source={_settings.Database.Path}"))
        {
            connection.Open();
            using var command = connection.CreateCommand();
            command.CommandText = """
                INSERT INTO source_inputs (url, payload) VALUES ($url, $payload);
                UPDATE dataset_state SET last_checked_at_unix_seconds = 1000 WHERE id = 1;
                """;
            command.Parameters.AddWithValue("$url", ResponsesUrl.AbsoluteUri);
            command.Parameters.AddWithValue("$payload", raw);
            command.ExecuteNonQuery();
        }

        Assert.True(_store.RewriteInput(ResponsesUrl, ResponsesCsv.Sanitize));
        Assert.False(_store.RewriteInput(ResponsesUrl, ResponsesCsv.Sanitize));

        Assert.DoesNotContain("65+", Encoding.UTF8.GetString(_store.LoadInput(ResponsesUrl)!.Payload));
        Assert.Equal(0, _store.GetState().LastCheckedAtUnixSeconds);
    }

    [Fact]
    public void Initialize_adds_status_columns_to_a_database_from_an_older_version()
    {
        var path = Path.Combine(_directory, "old.db");
        using (var connection = new SqliteConnection($"Data Source={path};Pooling=False"))
        {
            connection.Open();
            using var command = connection.CreateCommand();
            command.CommandText = """
                CREATE TABLE dataset_state (
                    id INTEGER PRIMARY KEY NOT NULL CHECK (id = 1),
                    source_url TEXT NOT NULL,
                    version TEXT NULL,
                    last_checked_at_unix_seconds INTEGER NOT NULL,
                    last_changed_at_unix_seconds INTEGER NOT NULL,
                    place_count INTEGER NOT NULL);
                INSERT INTO dataset_state VALUES (1, 'https://source.test/responses.csv', 'v1', 10, 10, 7);
                """;
            command.ExecuteNonQuery();
        }

        var store = new MapStore(_settings with { Database = new DatabaseSettings(path) });
        store.Initialize();

        var state = store.GetState();
        Assert.Equal(7, state.PlaceCount);
        Assert.Null(state.LastAttemptOutcome);
        Assert.Equal(0, state.SkippedRows);
    }

    private async Task ImportParishes(int count)
    {
        var (csv, geo) = Parishes(count);
        _sources.Serve(ResponsesUrl, csv);
        _sources.Serve(CoordinatesUrl, geo);
        Assert.Equal("updated", (await _service.RefreshAsync(force: false, CancellationToken.None)).Outcome);
    }

    public void Dispose()
    {
        SqliteConnection.ClearAllPools();
        try
        {
            Directory.Delete(_directory, recursive: true);
        }
        catch (IOException)
        {
        }
    }

    private sealed class FakeSources : HttpMessageHandler
    {
        private readonly Dictionary<Uri, Func<HttpResponseMessage>> _responses = [];
        private readonly Dictionary<Uri, string?> _etags = [];

        public bool NotModifiedWhenEtagMatches { get; set; }
        public Dictionary<Uri, string?> LastIfNoneMatch { get; } = [];

        public void Serve(Uri url, byte[] payload, string? etag = null)
        {
            _etags[url] = etag;
            _responses[url] = () =>
            {
                var response = new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(payload) };
                if (etag is not null)
                {
                    response.Headers.TryAddWithoutValidation("ETag", etag);
                }

                return response;
            };
        }

        public void FailWith(Uri url, HttpStatusCode status) =>
            _responses[url] = () => new HttpResponseMessage(status);

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            var url = request.RequestUri!;
            var ifNoneMatch = request.Headers.IfNoneMatch.FirstOrDefault()?.ToString();
            LastIfNoneMatch[url] = ifNoneMatch;
            if (NotModifiedWhenEtagMatches && ifNoneMatch is not null && ifNoneMatch == _etags.GetValueOrDefault(url))
            {
                return Task.FromResult(new HttpResponseMessage(HttpStatusCode.NotModified));
            }

            return Task.FromResult(_responses[url]());
        }
    }

    private sealed class ManualTime(DateTimeOffset start) : TimeProvider
    {
        private DateTimeOffset _now = start;

        public override DateTimeOffset GetUtcNow() => _now;

        public void Advance(TimeSpan by) => _now += by;
    }
}
