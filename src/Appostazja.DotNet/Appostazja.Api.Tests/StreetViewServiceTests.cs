using System.Globalization;
using System.Net;
using System.Text;
using ApostasyMap.Api.Configuration;
using ApostasyMap.Api.Contracts;
using ApostasyMap.Api.Data;
using ApostasyMap.Api.Endpoints;
using ApostasyMap.Api.Services;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Http.HttpResults;
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
    public async Task Google_panorama_is_returned_facing_the_church_with_a_thumbnail_on_our_api()
    {
        _google.Nearest = Metadata("google-pano", 50.0618, 19.9396, "© 2026 Google");

        var lookup = await _service.LookupAsync(Church, CancellationToken.None);

        Assert.Equal(StreetViewOutcome.Found, lookup.Outcome);
        var preview = lookup.Preview!;
        Assert.Equal("google-pano", preview.PanoId);
        Assert.Equal(StreetViewGeometry.Bearing(50.0618, 19.9396, Church.Latitude, Church.Longitude), preview.Heading);
        Assert.StartsWith("api/v1/places/mariacki/street-view/thumbnail?v=", preview.ThumbnailUrl);
        Assert.DoesNotContain("key=", preview.ThumbnailUrl);
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

    [Fact]
    public async Task A_thumbnail_is_downloaded_once_with_a_signed_url_and_then_served_from_disk()
    {
        _google.Nearest = Metadata("google-pano", 50.0618, 19.9396, "© Google");
        _google.Image = () => ImageResponse(Jpeg(7));

        var first = await _service.ThumbnailAsync(Church, CancellationToken.None);
        // A restarted server finds it on disk too.
        var again = await CreateService(apiKey: "test-key").ThumbnailAsync(Church, CancellationToken.None);

        Assert.Equal(ThumbnailOutcome.Found, first.Outcome);
        Assert.Equal(Jpeg(7), Read(first));
        Assert.Equal("image/jpeg", first.ContentType);
        Assert.Equal(Jpeg(7), Read(again));
        Assert.Equal(first.ETag, again.ETag);
        var request = Assert.Single(_google.ImageRequests);
        Assert.Contains("pano=google-pano", request.Query);
        var unsigned = request.PathAndQuery[..request.PathAndQuery.IndexOf("&signature=", StringComparison.Ordinal)];
        Assert.EndsWith($"&signature={UrlSigner.Signature(unsigned, UrlSigner.DecodeSecret(GoogleExampleSecret))}",
            request.PathAndQuery);
    }

    [Fact]
    public async Task An_expired_thumbnail_is_downloaded_again_and_served_stale_while_google_refuses()
    {
        _google.Nearest = Metadata("google-pano", 50.0618, 19.9396, "© Google");
        _google.Image = () => ImageResponse(Jpeg(1));
        var original = await _service.ThumbnailAsync(Church, CancellationToken.None);

        _time.Advance(TimeSpan.FromDays(31));
        _google.Image = () => ImageResponse(Jpeg(2));
        var refreshed = await _service.ThumbnailAsync(Church, CancellationToken.None);
        Assert.Equal(Jpeg(2), Read(refreshed));
        Assert.NotEqual(original.ETag, refreshed.ETag);

        _time.Advance(TimeSpan.FromDays(31));
        _google.Image = () => new HttpResponseMessage(HttpStatusCode.Forbidden);
        var stale = await _service.ThumbnailAsync(Church, CancellationToken.None);
        Assert.Equal(ThumbnailOutcome.Found, stale.Outcome);
        Assert.Equal(Jpeg(2), Read(stale));
        Assert.Equal(3, _google.ImageRequests.Count);
    }

    [Fact]
    public async Task A_new_download_is_served_while_another_request_is_still_sending_the_old_copy()
    {
        _google.Nearest = Metadata("google-pano", 50.0618, 19.9396, "© Google");
        _google.Image = () => ImageResponse(Jpeg(1));
        await _service.ThumbnailAsync(Church, CancellationToken.None);
        using var beingSent = (await _service.ThumbnailAsync(Church, CancellationToken.None)).Content;

        _time.Advance(TimeSpan.FromDays(31));
        _google.Image = () => ImageResponse(Jpeg(2));
        var refreshed = await _service.ThumbnailAsync(Church, CancellationToken.None);

        Assert.Equal(Jpeg(2), Read(refreshed));
        Assert.Equal(Jpeg(1)[0], beingSent!.ReadByte());
    }

    [Theory]
    [InlineData(HttpStatusCode.Forbidden, ThumbnailOutcome.Unavailable)]
    [InlineData(HttpStatusCode.TooManyRequests, ThumbnailOutcome.Unavailable)]
    [InlineData(HttpStatusCode.NotFound, ThumbnailOutcome.None)]
    [InlineData(HttpStatusCode.InternalServerError, ThumbnailOutcome.Failed)]
    public async Task Without_a_stored_copy_google_errors_map_to_what_the_client_can_do(
        HttpStatusCode google, ThumbnailOutcome expected)
    {
        _google.Nearest = Metadata("google-pano", 50.0618, 19.9396, "© Google");
        _google.Image = () => new HttpResponseMessage(google);

        var thumbnail = await _service.ThumbnailAsync(Church, CancellationToken.None);

        Assert.Equal(expected, thumbnail.Outcome);
        Assert.Null(thumbnail.Content);
    }

    [Fact]
    public async Task A_parish_without_a_panorama_never_downloads_an_image()
    {
        _google.Nearest = Status("ZERO_RESULTS");

        Assert.Equal(ThumbnailOutcome.None, (await _service.ThumbnailAsync(Church, CancellationToken.None)).Outcome);
        Assert.Empty(_google.ImageRequests);
    }

    [Fact]
    public async Task Simultaneous_requests_share_one_download()
    {
        _google.Nearest = Metadata("google-pano", 50.0618, 19.9396, "© Google");
        await _service.LookupAsync(Church, CancellationToken.None);
        _google.HoldImages = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);

        var requests = Enumerable.Range(0, 5).Select(_ => _service.ThumbnailAsync(Church, CancellationToken.None)).ToList();
        _google.HoldImages.SetResult();
        var thumbnails = await Task.WhenAll(requests);

        Assert.All(thumbnails, thumbnail => Assert.Equal(Jpeg(1), Read(thumbnail)));
        Assert.Single(_google.ImageRequests);
    }

    [Fact]
    public void Thumbnail_responses_carry_the_etag_and_answer_a_matching_request_with_304()
    {
        ThumbnailResult Thumbnail(Stream content) => new(ThumbnailOutcome.Found, content, "image/jpeg", "abc-123");

        var fresh = new DefaultHttpContext();
        var served = Assert.IsType<FileStreamHttpResult>(MapEndpoints.ThumbnailResponse(fresh, Thumbnail(new MemoryStream(Jpeg(1)))));
        Assert.Equal("image/jpeg", served.ContentType);
        Assert.Equal("\"abc-123\"", fresh.Response.Headers.ETag.ToString());
        Assert.Equal(MapEndpoints.StreetViewCacheControl, fresh.Response.Headers.CacheControl.ToString());

        var revalidating = new DefaultHttpContext();
        revalidating.Request.Headers.IfNoneMatch = "\"abc-123\"";
        var unread = new MemoryStream(Jpeg(1));
        var notModified = Assert.IsType<StatusCodeHttpResult>(MapEndpoints.ThumbnailResponse(revalidating, Thumbnail(unread)));
        Assert.Equal(StatusCodes.Status304NotModified, notModified.StatusCode);
        Assert.False(unread.CanRead); // the file is closed without being sent
    }

    [Theory]
    [InlineData(ThumbnailOutcome.None, StatusCodes.Status404NotFound)]
    [InlineData(ThumbnailOutcome.Unavailable, StatusCodes.Status503ServiceUnavailable)]
    [InlineData(ThumbnailOutcome.Failed, StatusCodes.Status502BadGateway)]
    public void Missing_thumbnails_get_statuses_the_app_tells_apart(ThumbnailOutcome outcome, int status)
    {
        var context = new DefaultHttpContext();

        var result = Assert.IsAssignableFrom<IStatusCodeHttpResult>(
            MapEndpoints.ThumbnailResponse(context, new ThumbnailResult(outcome)));

        Assert.Equal(status, result.StatusCode);
        if (outcome != ThumbnailOutcome.None)
        {
            Assert.Equal("no-store", context.Response.Headers.CacheControl.ToString());
        }
    }

    private static byte[] Read(ThumbnailResult thumbnail)
    {
        Assert.Equal(ThumbnailOutcome.Found, thumbnail.Outcome);
        using var content = thumbnail.Content!;
        using var copy = new MemoryStream();
        content.CopyTo(copy);
        return copy.ToArray();
    }

    private static byte[] Jpeg(byte marker) => [0xFF, 0xD8, 0xFF, 0xE0, marker, 0xFF, 0xD9];

    private static HttpResponseMessage ImageResponse(byte[] bytes) =>
        new(HttpStatusCode.OK) { Content = new ByteArrayContent(bytes) { Headers = { ContentType = new("image/jpeg") } } };

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
        var thumbnails = new StreetViewThumbnailStore(settings);
        thumbnails.Initialize();
        return new StreetViewService(
            settings, new HttpClient(_google), store, thumbnails, _time, NullLogger<StreetViewService>.Instance);
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

    /// <summary>
    /// Answers the 120 m lookup with <see cref="Nearest"/>, the 50 m probes with <see cref="Probe"/> and thumbnail
    /// downloads with <see cref="Image"/>.
    /// </summary>
    private sealed class FakeGoogle : HttpMessageHandler
    {
        public string Nearest { get; set; } = Status("ZERO_RESULTS");
        public Func<(double Latitude, double Longitude), string> Probe { get; set; } = _ => Status("ZERO_RESULTS");
        public Func<HttpResponseMessage> Image { get; set; } = () => ImageResponse(Jpeg(1));
        public TaskCompletionSource? HoldImages { get; set; }
        public int Requests { get; private set; }
        public List<Uri> ImageRequests { get; } = [];

        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            lock (this)
            {
                Requests++;
            }

            if (!request.RequestUri!.AbsolutePath.EndsWith("/metadata", StringComparison.Ordinal))
            {
                lock (this)
                {
                    ImageRequests.Add(request.RequestUri);
                }

                if (HoldImages is not null)
                {
                    await HoldImages.Task;
                }

                return Image();
            }

            var query = System.Web.HttpUtility.ParseQueryString(request.RequestUri!.Query);
            var location = query["location"]!.Split(',').Select(value => double.Parse(value, CultureInfo.InvariantCulture)).ToArray();
            var body = query["radius"] == "120" ? Nearest : Probe((location[0], location[1]));
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(body, Encoding.UTF8, "application/json")
            };
        }
    }

    private sealed class ManualTime(DateTimeOffset start) : TimeProvider
    {
        private DateTimeOffset _now = start;

        public override DateTimeOffset GetUtcNow() => _now;

        public void Advance(TimeSpan by) => _now += by;
    }
}
