using System.Globalization;
using System.Net;
using System.Text;
using ApostasyMap.Api.Configuration;
using ApostasyMap.Api.Contracts;
using ApostasyMap.Api.Data;
using ApostasyMap.Api.Services;
using Microsoft.Data.Sqlite;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging.Abstractions;

namespace Appostazja.Api.Tests;

public sealed class StreetViewServiceTests : IDisposable
{
    // The worked example from Google's URL signing documentation.
    private const string GoogleExampleSecret = "vNIXE0xscrmjlyV-12Nj_BvUPaw=";

    private static readonly PlaceDto Church = new(
        "mariacki", "Bazylika Mariacka", "Plac Mariacki 5, Kraków", 50.0616, 19.9394, 0,
        new RatingBreakdownDto(0, 0, 0, 0));

    private readonly string _directory = Path.Combine(Path.GetTempPath(), "appostazja-tests", Guid.NewGuid().ToString("N"));
    private readonly FakeGoogle _google = new();
    private readonly ManualTime _time = new(new DateTimeOffset(2026, 11, 14, 12, 0, 0, TimeSpan.Zero));
    private readonly StreetViewService _service;

    public StreetViewServiceTests()
    {
        _service = CreateService(apiKey: "test-key");
    }

    [Fact]
    public void Signature_matches_googles_documented_example()
    {
        var signature = UrlSigner.Signature(
            "/maps/api/geocode/json?address=New+York&client=clientID",
            UrlSigner.DecodeSecret(GoogleExampleSecret));

        Assert.Equal("chaRF2hTJKOScPr-RQCEhZbSzIE=", signature);
    }

    [Fact]
    public async Task Google_panorama_is_returned_with_a_signed_thumbnail_facing_the_church()
    {
        _google.Nearest = Metadata("google-pano", 50.0618, 19.9396, "© 2026 Google");

        var lookup = await _service.LookupAsync(Church, CancellationToken.None);

        Assert.Equal(StreetViewOutcome.Found, lookup.Outcome);
        var preview = lookup.Preview!;
        Assert.Equal("google-pano", preview.PanoId);
        Assert.Equal(StreetViewGeometry.Bearing(50.0618, 19.9396, Church.Latitude, Church.Longitude), preview.Heading);
        var url = new Uri(preview.ThumbnailUrl);
        var unsigned = url.PathAndQuery[..url.PathAndQuery.IndexOf("&signature=", StringComparison.Ordinal)];
        Assert.EndsWith($"&signature={UrlSigner.Signature(unsigned, UrlSigner.DecodeSecret(GoogleExampleSecret))}",
            preview.ThumbnailUrl);
        Assert.Contains("pano=google-pano", preview.ThumbnailUrl);
    }

    [Fact]
    public async Task A_closer_user_photo_sphere_is_replaced_by_the_nearest_google_panorama_from_the_probes()
    {
        _google.Nearest = Metadata("inside-the-church", 50.06161, 19.93941, "© Michał Kot");
        _google.Probe = location => location.Latitude > Church.Latitude
            ? Metadata("street-north", 50.0619, 19.9394, "© Google")
            : Metadata("street-south", 50.0610, 19.9394, "© Google");

        var lookup = await _service.LookupAsync(Church, CancellationToken.None);

        Assert.Equal("street-north", lookup.Preview!.PanoId);
    }

    [Fact]
    public async Task Lookups_are_reused_until_they_expire_and_then_looked_up_again()
    {
        _google.Nearest = Metadata("google-pano", 50.0618, 19.9396, "© Google");
        await _service.LookupAsync(Church, CancellationToken.None);
        var afterFirst = _google.Requests;

        await CreateService(apiKey: "test-key").LookupAsync(Church, CancellationToken.None);
        Assert.Equal(afterFirst, _google.Requests);

        _time.Advance(TimeSpan.FromDays(31));
        await _service.LookupAsync(Church, CancellationToken.None);
        Assert.True(_google.Requests > afterFirst);
    }

    [Fact]
    public async Task No_panorama_is_remembered_but_a_refusal_is_not()
    {
        _google.Nearest = Status("OVER_QUERY_LIMIT");
        Assert.Equal(StreetViewOutcome.Unavailable, (await _service.LookupAsync(Church, CancellationToken.None)).Outcome);

        _google.Nearest = Status("ZERO_RESULTS");
        Assert.Equal(StreetViewOutcome.None, (await _service.LookupAsync(Church, CancellationToken.None)).Outcome);

        var requests = _google.Requests;
        Assert.Equal(StreetViewOutcome.None, (await _service.LookupAsync(Church, CancellationToken.None)).Outcome);
        Assert.Equal(requests, _google.Requests);
    }

    [Fact]
    public async Task An_expired_panorama_is_served_while_google_refuses()
    {
        _google.Nearest = Metadata("google-pano", 50.0618, 19.9396, "© Google");
        await _service.LookupAsync(Church, CancellationToken.None);

        _time.Advance(TimeSpan.FromDays(31));
        _google.Nearest = Status("REQUEST_DENIED");

        Assert.Equal("google-pano", (await _service.LookupAsync(Church, CancellationToken.None)).Preview!.PanoId);
    }

    [Fact]
    public async Task Without_an_api_key_google_is_never_called()
    {
        var lookup = await CreateService(apiKey: null).LookupAsync(Church, CancellationToken.None);

        Assert.Equal(StreetViewOutcome.Unavailable, lookup.Outcome);
        Assert.Equal(0, _google.Requests);
    }

    [Fact]
    public void Probe_points_ring_the_parish_at_40_and_80_metres()
    {
        var distances = StreetViewGeometry.ProbePoints(Church.Latitude, Church.Longitude)
            .Select(point => StreetViewGeometry.DistanceMeters(Church.Latitude, Church.Longitude, point.Latitude, point.Longitude))
            .ToList();

        Assert.Equal(16, distances.Count);
        Assert.All(distances.Take(8), distance => Assert.Equal(40, distance, 0.5));
        Assert.All(distances.Skip(8), distance => Assert.Equal(80, distance, 0.5));
    }

    private StreetViewService CreateService(string? apiKey)
    {
        var configuration = new ConfigurationBuilder().AddInMemoryCollection(new Dictionary<string, string?>
        {
            ["Database:Path"] = Path.Combine(_directory, "test.db"),
            ["StreetView:ApiKey"] = apiKey,
            ["StreetView:SigningSecret"] = GoogleExampleSecret
        }).Build();
        var settings = AppSettings.FromConfiguration(configuration, _directory);
        Directory.CreateDirectory(_directory);
        var store = new StreetViewStore(settings);
        store.Initialize();
        return new StreetViewService(settings, new HttpClient(_google), store, _time, NullLogger<StreetViewService>.Instance);
    }

    private static string Metadata(string panoId, double lat, double lng, string copyright) =>
        $$"""{"status":"OK","pano_id":"{{panoId}}","location":{"lat":{{lat.ToString(CultureInfo.InvariantCulture)}},"lng":{{lng.ToString(CultureInfo.InvariantCulture)}}},"copyright":"{{copyright}}","date":"2025-07"}""";

    private static string Status(string status) => $$"""{"status":"{{status}}"}""";

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

    /// <summary>Answers the 120 m lookup with <see cref="Nearest"/> and the 50 m probes with <see cref="Probe"/>.</summary>
    private sealed class FakeGoogle : HttpMessageHandler
    {
        public string Nearest { get; set; } = Status("ZERO_RESULTS");
        public Func<(double Latitude, double Longitude), string> Probe { get; set; } = _ => Status("ZERO_RESULTS");
        public int Requests { get; private set; }

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            lock (this)
            {
                Requests++;
            }

            var query = System.Web.HttpUtility.ParseQueryString(request.RequestUri!.Query);
            var location = query["location"]!.Split(',').Select(value => double.Parse(value, CultureInfo.InvariantCulture)).ToArray();
            var body = query["radius"] == "120" ? Nearest : Probe((location[0], location[1]));
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(body, Encoding.UTF8, "application/json")
            });
        }
    }

    private sealed class ManualTime(DateTimeOffset start) : TimeProvider
    {
        private DateTimeOffset _now = start;

        public override DateTimeOffset GetUtcNow() => _now;

        public void Advance(TimeSpan by) => _now += by;
    }
}
