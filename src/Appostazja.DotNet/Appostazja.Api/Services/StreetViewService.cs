using System.Collections.Concurrent;
using System.Globalization;
using System.Security.Cryptography;
using System.Text;
using System.Net.Http.Json;
using System.Text.Json.Serialization;
using ApostasyMap.Api.Configuration;
using ApostasyMap.Api.Contracts;
using ApostasyMap.Api.Data;
using ApostasyMap.Api.Json;

namespace ApostasyMap.Api.Services;

public enum StreetViewOutcome
{
    Found,

    /// <summary>No Google street-level panorama near the parish.</summary>
    None,

    /// <summary>Not configured, or Google refused or failed; clients can still try their own panorama lookup.</summary>
    Unavailable
}

public sealed record StreetViewLookup(StreetViewOutcome Outcome, StreetViewDto? Preview = null);

public enum ThumbnailOutcome
{
    Found,

    /// <summary>No Google panorama near the parish, or Google no longer has its imagery.</summary>
    None,

    /// <summary>Not configured, or Google refused, and no stored copy to fall back on.</summary>
    Unavailable,

    /// <summary>Google could not be reached or failed, and no stored copy to fall back on.</summary>
    Failed
}

/// <param name="Content">The image when found, positioned at its start; whoever writes the response disposes it.</param>
/// <param name="ETag">Changes with the panorama, heading and every new download.</param>
public sealed record ThumbnailResult(
    ThumbnailOutcome Outcome, Stream? Content = null, string? ContentType = null, string? ETag = null);

/// <summary>
/// Finds a street-level Google panorama for a parish with the free metadata endpoint and serves its thumbnail
/// from our server: each one is downloaded from Google once (with the signed URL, so the key and signing secret
/// stay here) and kept for <see cref="StreetViewSettings.CacheFor"/>, like the lookups. Google's Street View
/// policies only allow storing panorama IDs, not images; keeping the thumbnails was a deliberate choice of the
/// project owner, see the README.
/// </summary>
public sealed class StreetViewService(
    AppSettings settings,
    HttpClient http,
    StreetViewStore store,
    StreetViewThumbnailStore thumbnails,
    TimeProvider time,
    ILogger<StreetViewService> logger)
{
    internal const string BaseUrl = "https://maps.googleapis.com/maps/api/streetview";
    private const int SearchRadiusMeters = 120;
    private const int ProbeRadiusMeters = 50;
    private const string ThumbnailParameters = "size=640x400&fov=80&pitch=8";
    private const long MaxThumbnailBytes = 4 * 1024 * 1024;

    private readonly ConcurrentDictionary<string, Lazy<Task<GoogleImage>>> _downloads = new();
    private DateTimeOffset _lastPrune = DateTimeOffset.MinValue;

    public async Task<StreetViewLookup> LookupAsync(PlaceDto place, CancellationToken cancellationToken)
    {
        var options = settings.StreetView;
        if (options.ApiKey is null)
        {
            return new StreetViewLookup(StreetViewOutcome.Unavailable);
        }

        var now = time.GetUtcNow();
        var cached = store.Get(place.Id);
        // A moved parish needs a new lookup; otherwise the stored panorama is reused until it expires.
        var cachedHere = cached is not null && cached.Latitude == place.Latitude && cached.Longitude == place.Longitude;
        if (cachedHere && now - DateTimeOffset.FromUnixTimeSeconds(cached!.CheckedAtUnixSeconds) < options.CacheFor)
        {
            return ToLookup(cached, place);
        }

        var found = await FindGoogleImageryAsync(place.Latitude, place.Longitude, cancellationToken);
        if (found.Failed)
        {
            // Better an expired panorama than none while Google is refusing or unreachable.
            return cachedHere ? ToLookup(cached!, place) : new StreetViewLookup(StreetViewOutcome.Unavailable);
        }

        var row = new StreetViewRow(
            place.Id,
            place.Latitude,
            place.Longitude,
            found.Panorama?.PanoId,
            found.Panorama?.Location?.Lat ?? 0,
            found.Panorama?.Location?.Lng ?? 0,
            found.Panorama?.Copyright,
            found.Panorama?.Date,
            now.ToUnixTimeSeconds());
        store.Save(row);
        return ToLookup(row, place);
    }

    private StreetViewLookup ToLookup(StreetViewRow row, PlaceDto place)
    {
        if (row.PanoId is null)
        {
            return new StreetViewLookup(StreetViewOutcome.None);
        }

        var heading = StreetViewGeometry.Bearing(row.PanoLatitude, row.PanoLongitude, place.Latitude, place.Longitude);
        return new StreetViewLookup(
            StreetViewOutcome.Found,
            new StreetViewDto(
                row.PanoId, heading, row.Copyright ?? "© Google", row.Date, ThumbnailPath(place.Id, row.PanoId, heading)));
    }

    /// <summary>Relative to the API base URL; the version changes with the image, so clients can cache by URL.</summary>
    internal static string ThumbnailPath(string placeId, string panoId, int heading) =>
        $"api/v1/places/{Uri.EscapeDataString(placeId)}/street-view/thumbnail?v={ThumbnailKey(panoId, heading)[..12]}";

    /// <summary>One file per panorama, heading and image parameters (a change of size or framing refetches all).</summary>
    internal static string ThumbnailKey(string panoId, int heading) =>
        Convert.ToHexStringLower(SHA256.HashData(Encoding.UTF8.GetBytes($"{panoId}|{heading}|{ThumbnailParameters}")));

    internal string GoogleThumbnailUrl(string panoId, int heading) =>
        UrlSigner.Sign(
            $"{BaseUrl}?{ThumbnailParameters}&pano={Uri.EscapeDataString(panoId)}" +
            $"&heading={heading.ToString(CultureInfo.InvariantCulture)}&return_error_code=true" +
            $"&key={Uri.EscapeDataString(settings.StreetView.ApiKey!)}",
            settings.StreetView.SigningSecret);

    /// <summary>
    /// The parish's thumbnail: the stored copy while it is fresh, otherwise downloaded from Google again. An
    /// expired copy is still served while Google refuses or is unreachable.
    /// </summary>
    public async Task<ThumbnailResult> ThumbnailAsync(PlaceDto place, CancellationToken cancellationToken)
    {
        var lookup = await LookupAsync(place, cancellationToken);
        if (lookup.Outcome != StreetViewOutcome.Found)
        {
            return new ThumbnailResult(
                lookup.Outcome == StreetViewOutcome.None ? ThumbnailOutcome.None : ThumbnailOutcome.Unavailable);
        }

        var preview = lookup.Preview!;
        var key = ThumbnailKey(preview.PanoId, preview.Heading);
        var stored = thumbnails.Get(key);
        if (stored is not null && time.GetUtcNow() - stored.FetchedAt < settings.StreetView.CacheFor)
        {
            return Found(key, stored);
        }

        // Closed while downloading: Windows cannot replace a file that is open, even for reading.
        var hadCopy = stored is not null;
        stored?.Dispose();
        var image = await DownloadOnceAsync(key, preview.PanoId, preview.Heading).WaitAsync(cancellationToken);
        if (image.Status is GoogleImageStatus.Refused or GoogleImageStatus.Failed && hadCopy &&
            thumbnails.Get(key) is { } stale)
        {
            return Found(key, stale);
        }

        return image.Status switch
        {
            GoogleImageStatus.Ok =>
                Found(key, new StoredThumbnail(new MemoryStream(image.Bytes!, writable: false), image.FetchedAt)),
            // Google removed the panorama; the next lookup after expiry will find another one, if any.
            GoogleImageStatus.NoImagery => new ThumbnailResult(ThumbnailOutcome.None),
            GoogleImageStatus.Refused => new ThumbnailResult(ThumbnailOutcome.Unavailable),
            _ => new ThumbnailResult(ThumbnailOutcome.Failed)
        };
    }

    private static ThumbnailResult Found(string key, StoredThumbnail thumbnail) =>
        new(
            ThumbnailOutcome.Found,
            thumbnail.Content,
            ImageContentType(thumbnail.Content),
            $"{key[..16]}-{thumbnail.FetchedAt.ToUnixTimeSeconds()}");

    private static string ImageContentType(Stream image)
    {
        Span<byte> head = stackalloc byte[4];
        var read = image.ReadAtLeast(head, head.Length, throwOnEndOfStream: false);
        image.Position = 0;
        return head[..read] is [0x89, 0x50, 0x4E, 0x47] ? "image/png" : "image/jpeg";
    }

    /// <summary>
    /// Parishes opened at the same moment share one download per thumbnail. It runs to completion on its own
    /// (bounded by the HttpClient timeout), so one client giving up does not fail the others.
    /// </summary>
    private Task<GoogleImage> DownloadOnceAsync(string key, string panoId, int heading)
    {
        var download = _downloads.GetOrAdd(key, _ => new Lazy<Task<GoogleImage>>(async () =>
        {
            try
            {
                var image = await DownloadAsync(panoId, heading);
                if (image.Status == GoogleImageStatus.Ok)
                {
                    SaveQuietly(key, image);
                    PruneDaily(image.FetchedAt);
                }

                return image;
            }
            finally
            {
                _downloads.TryRemove(key, out Lazy<Task<GoogleImage>>? _);
            }
        }));
        return download.Value;
    }

    /// <summary>
    /// The download is served either way. Saving fails on Windows while another request is still sending the
    /// old copy; it is then downloaded again on a later request.
    /// </summary>
    private void SaveQuietly(string key, GoogleImage image)
    {
        try
        {
            thumbnails.Save(key, image.Bytes!, image.FetchedAt);
        }
        catch (Exception exception) when (exception is IOException or UnauthorizedAccessException)
        {
            logger.LogWarning(exception, "Could not store a Street View thumbnail");
        }
    }

    private void PruneDaily(DateTimeOffset now)
    {
        if (now - _lastPrune < TimeSpan.FromDays(1))
        {
            return;
        }

        _lastPrune = now;
        var deleted = thumbnails.Prune(now - 2 * settings.StreetView.CacheFor);
        if (deleted > 0)
        {
            logger.LogInformation("Deleted {Count} Street View thumbnails no parish uses any more", deleted);
        }
    }

    private async Task<GoogleImage> DownloadAsync(string panoId, int heading)
    {
        try
        {
            using var response = await http.GetAsync(
                GoogleThumbnailUrl(panoId, heading), HttpCompletionOption.ResponseHeadersRead);
            var status = (int)response.StatusCode;
            if (response.IsSuccessStatusCode && response.Content.Headers.ContentType?.MediaType?.StartsWith("image/") == true)
            {
                if (response.Content.Headers.ContentLength > MaxThumbnailBytes)
                {
                    logger.LogWarning("Street View thumbnail too large: {Bytes} bytes", response.Content.Headers.ContentLength);
                    return new GoogleImage(GoogleImageStatus.Failed);
                }

                var bytes = await response.Content.ReadAsByteArrayAsync();
                return bytes.Length is > 0 and <= (int)MaxThumbnailBytes
                    ? new GoogleImage(GoogleImageStatus.Ok, bytes, time.GetUtcNow())
                    : new GoogleImage(GoogleImageStatus.Failed);
            }

            logger.LogWarning("Street View thumbnail HTTP {Status}", status);
            return status switch
            {
                404 => new GoogleImage(GoogleImageStatus.NoImagery),
                // Billing, API restrictions, a bad signature, quota or rate limits.
                >= 400 and < 500 => new GoogleImage(GoogleImageStatus.Refused),
                _ => new GoogleImage(GoogleImageStatus.Failed)
            };
        }
        catch (Exception exception) when (exception is HttpRequestException or OperationCanceledException)
        {
            logger.LogWarning(exception, "Street View thumbnail request failed");
            return new GoogleImage(GoogleImageStatus.Failed);
        }
    }

    private enum GoogleImageStatus { Ok, NoImagery, Refused, Failed }

    private sealed record GoogleImage(GoogleImageStatus Status, byte[]? Bytes = null, DateTimeOffset FetchedAt = default);

    private async Task<(StreetViewMetadata? Panorama, bool Failed)> FindGoogleImageryAsync(
        double latitude, double longitude, CancellationToken cancellationToken)
    {
        var nearest = await MetadataAsync(latitude, longitude, SearchRadiusMeters, cancellationToken);
        if (nearest is null || IsFailure(nearest.Status))
        {
            return (null, true);
        }

        if (nearest.Status != "OK")
        {
            return (null, false);
        }

        if (nearest.PanoId is not null && StreetViewGeometry.IsGoogleImagery(nearest.Copyright))
        {
            return (nearest, false);
        }

        // The nearest panorama is a user photo sphere: probe a ring around the parish for Google's own imagery.
        var probes = await Task.WhenAll(StreetViewGeometry.ProbePoints(latitude, longitude)
            .Select(point => MetadataAsync(point.Latitude, point.Longitude, ProbeRadiusMeters, cancellationToken)));
        var usable = probes
            .Where(probe => probe is { Status: "OK", PanoId: not null, Location: not null })
            .Select(probe => probe!)
            .ToList();
        var chosen = StreetViewGeometry.PickGoogleImagery(
            latitude,
            longitude,
            usable.Select(probe => new PanoramaCandidate(probe.PanoId!, probe.Location!.Lat, probe.Location.Lng, probe.Copyright)));
        if (chosen is not null)
        {
            return (usable.First(probe => probe.PanoId == chosen.PanoId), false);
        }

        // Do not remember "no panorama" when some probes could not be answered.
        return (null, probes.Any(probe => probe is null || IsFailure(probe.Status)));
    }

    private static bool IsFailure(string status) => status is not ("OK" or "ZERO_RESULTS" or "NOT_FOUND");

    private async Task<StreetViewMetadata?> MetadataAsync(
        double latitude, double longitude, int radiusMeters, CancellationToken cancellationToken)
    {
        var url = UrlSigner.Sign(
            $"{BaseUrl}/metadata?location={latitude.ToString("0.#######", CultureInfo.InvariantCulture)}," +
            $"{longitude.ToString("0.#######", CultureInfo.InvariantCulture)}&radius={radiusMeters}&source=outdoor" +
            $"&key={Uri.EscapeDataString(settings.StreetView.ApiKey!)}",
            settings.StreetView.SigningSecret);
        try
        {
            using var response = await http.GetAsync(url, cancellationToken);
            if (!response.IsSuccessStatusCode)
            {
                logger.LogWarning("Street View metadata HTTP {Status}", (int)response.StatusCode);
                return null;
            }

            var metadata = await response.Content.ReadFromJsonAsync(
                AppJsonSerializerContext.Default.StreetViewMetadata, cancellationToken);
            if (metadata is not null && IsFailure(metadata.Status))
            {
                logger.LogWarning("Street View metadata {Status}: {Message}", metadata.Status, metadata.ErrorMessage);
            }

            return metadata;
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            throw;
        }
        catch (Exception exception) when (exception is HttpRequestException or OperationCanceledException or System.Text.Json.JsonException)
        {
            logger.LogWarning(exception, "Street View metadata request failed");
            return null;
        }
    }
}

internal sealed record StreetViewMetadata(
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("pano_id")] string? PanoId = null,
    [property: JsonPropertyName("location")] StreetViewLocation? Location = null,
    [property: JsonPropertyName("copyright")] string? Copyright = null,
    [property: JsonPropertyName("date")] string? Date = null,
    [property: JsonPropertyName("error_message")] string? ErrorMessage = null);

internal sealed record StreetViewLocation(
    [property: JsonPropertyName("lat")] double Lat,
    [property: JsonPropertyName("lng")] double Lng);
