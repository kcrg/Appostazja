using System.Globalization;

using ApostasyMap.Api.Services;

namespace ApostasyMap.Api.Configuration;

public sealed record AppSettings(
    SourceSettings Source,
    DatabaseSettings Database,
    ImportSettings Import,
    StreetViewSettings StreetView)
{
    public static AppSettings FromConfiguration(IConfiguration configuration, string contentRootPath)
    {
        var responses = ReadUrl(configuration, "Source:Url", "https://mapaapostazji.pl/data/responses.csv");
        var coordinates = ReadUrl(configuration, "Source:CoordinatesUrl", "https://mapaapostazji.pl/data/map-data.json");
        if (responses == coordinates)
        {
            throw new InvalidOperationException("Source:Url and Source:CoordinatesUrl must be different URLs.");
        }

        var databasePath = configuration["Database:Path"] ?? "data/apostasy-map.db";
        if (!Path.IsPathRooted(databasePath))
        {
            databasePath = Path.GetFullPath(Path.Combine(contentRootPath, databasePath));
        }

        return new AppSettings(
            new SourceSettings(
                responses,
                coordinates,
                TimeSpan.FromHours(ReadInt(configuration, "Source:FreshForHours", 20, 1, 168)),
                TimeSpan.FromMinutes(ReadInt(configuration, "Source:ForceRefreshCooldownMinutes", 360, 5, 10080)),
                ReadInt(configuration, "Source:NightlyLocalHour", 3, 0, 23),
                ReadInt(configuration, "Source:NightlyLocalMinute", 15, 0, 59),
                configuration["Source:TimeZoneId"] ?? "Europe/Warsaw",
                ReadInt(configuration, "Source:RequestTimeoutSeconds", 30, 5, 300),
                ReadInt(configuration, "Source:MaxResponseMegabytes", 32, 1, 512) * 1024L * 1024L),
            new DatabaseSettings(databasePath),
            new ImportSettings(
                ReadInt(configuration, "Import:MaxSkippedRowPercent", 5, 0, 100) / 100.0,
                ReadInt(configuration, "Import:MaxPlaceDropPercent", 30, 0, 100) / 100.0,
                TimeSpan.FromHours(ReadInt(configuration, "Import:ReadyMaxAgeHours", 48, 1, 720))),
            ReadStreetView(configuration, contentRootPath, databasePath));
    }

    /// <summary>Street View is optional: without an API key the endpoint reports it as unavailable.</summary>
    private static StreetViewSettings ReadStreetView(IConfiguration configuration, string contentRootPath, string databasePath)
    {
        var apiKey = configuration["StreetView:ApiKey"];
        var secret = configuration["StreetView:SigningSecret"];
        byte[]? signingSecret = null;
        if (!string.IsNullOrWhiteSpace(secret))
        {
            try
            {
                signingSecret = UrlSigner.DecodeSecret(secret);
            }
            catch (FormatException)
            {
                throw new InvalidOperationException("StreetView:SigningSecret must be the URL-safe base64 secret from the Cloud console.");
            }
        }

        var thumbnails = configuration["StreetView:ThumbnailDirectory"];
        thumbnails = string.IsNullOrWhiteSpace(thumbnails)
            ? Path.Combine(Path.GetDirectoryName(databasePath)!, "street-view-thumbnails")
            : Path.GetFullPath(Path.Combine(contentRootPath, thumbnails));

        return new StreetViewSettings(
            string.IsNullOrWhiteSpace(apiKey) ? null : apiKey,
            signingSecret,
            TimeSpan.FromDays(ReadInt(configuration, "StreetView:CacheDays", 30, 1, 365)),
            thumbnails);
    }

    private static Uri ReadUrl(IConfiguration configuration, string key, string fallback)
    {
        if (!Uri.TryCreate(configuration[key] ?? fallback, UriKind.Absolute, out var uri) ||
            (uri.Scheme != Uri.UriSchemeHttps && uri.Scheme != Uri.UriSchemeHttp))
        {
            throw new InvalidOperationException($"{key} must be an absolute HTTP/HTTPS URL.");
        }

        return uri;
    }

    private static int ReadInt(
        IConfiguration configuration,
        string key,
        int fallback,
        int minimum,
        int maximum)
    {
        var raw = configuration[key];
        if (raw is null)
        {
            return fallback;
        }

        if (!int.TryParse(raw, NumberStyles.Integer, CultureInfo.InvariantCulture, out var value) ||
            value < minimum || value > maximum)
        {
            throw new InvalidOperationException($"{key} must be between {minimum} and {maximum}.");
        }

        return value;
    }
}

public sealed record SourceSettings(
    Uri Url,
    Uri CoordinatesUrl,
    TimeSpan FreshFor,
    TimeSpan ForceRefreshCooldown,
    int NightlyLocalHour,
    int NightlyLocalMinute,
    string TimeZoneId,
    int RequestTimeoutSeconds,
    long MaxResponseBytes);

public sealed record DatabaseSettings(string Path);

/// <param name="ApiKey">Server key for the Street View Static API; null disables Street View.</param>
/// <param name="SigningSecret">URL signing secret; image URLs are signed when it is set.</param>
/// <param name="CacheFor">How long a looked-up panorama and its thumbnail are reused before they are fetched again.</param>
/// <param name="ThumbnailDirectory">Where downloaded thumbnails are kept; next to the database by default.</param>
public sealed record StreetViewSettings(string? ApiKey, byte[]? SigningSecret, TimeSpan CacheFor, string ThumbnailDirectory);

/// <param name="MaxSkippedRowRatio">Import is rejected when more invalid source rows than this are skipped.</param>
/// <param name="MaxPlaceDropRatio">Import is rejected when the place count shrinks by more than this.</param>
/// <param name="ReadyMaxAge">Readiness fails when the last successful source check is older than this.</param>
public sealed record ImportSettings(
    double MaxSkippedRowRatio,
    double MaxPlaceDropRatio,
    TimeSpan ReadyMaxAge);
