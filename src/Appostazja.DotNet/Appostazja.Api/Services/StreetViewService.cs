using System.Globalization;
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

/// <summary>
/// Finds a street-level Google panorama for a parish with the free metadata endpoint and hands out a signed
/// thumbnail URL, so the signing secret stays on the server. Lookups are stored for
/// <see cref="StreetViewSettings.CacheFor"/>; the images are not, as Google's policies do not allow it, so
/// clients load them from Google and cache them per Google's HTTP headers.
/// </summary>
public sealed class StreetViewService(
    AppSettings settings,
    HttpClient http,
    StreetViewStore store,
    TimeProvider time,
    ILogger<StreetViewService> logger)
{
    internal const string BaseUrl = "https://maps.googleapis.com/maps/api/streetview";
    private const int SearchRadiusMeters = 120;
    private const int ProbeRadiusMeters = 50;

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
            new StreetViewDto(row.PanoId, heading, row.Copyright ?? "© Google", row.Date, ThumbnailUrl(row.PanoId, heading)));
    }

    internal string ThumbnailUrl(string panoId, int heading) =>
        UrlSigner.Sign(
            $"{BaseUrl}?size=640x400&pano={Uri.EscapeDataString(panoId)}" +
            $"&heading={heading.ToString(CultureInfo.InvariantCulture)}&fov=80&pitch=8&return_error_code=true" +
            $"&key={Uri.EscapeDataString(settings.StreetView.ApiKey!)}",
            settings.StreetView.SigningSecret);

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
