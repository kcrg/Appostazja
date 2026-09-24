using System.Globalization;

namespace ApostasyMap.Api.Configuration;

public sealed record AppSettings(
    SourceSettings Source,
    DatabaseSettings Database)
{
    public static AppSettings FromConfiguration(IConfiguration configuration, string contentRootPath)
    {
        var sourceUrl = configuration["Source:Url"]
            ?? "https://mapaapostazji.pl/data/map-data.json";

        if (!Uri.TryCreate(sourceUrl, UriKind.Absolute, out var uri) ||
            (uri.Scheme != Uri.UriSchemeHttps && uri.Scheme != Uri.UriSchemeHttp))
        {
            throw new InvalidOperationException("Source:Url must be an absolute HTTP/HTTPS URL.");
        }

        var databasePath = configuration["Database:Path"] ?? "data/apostasy-map.db";
        if (!Path.IsPathRooted(databasePath))
        {
            databasePath = Path.GetFullPath(Path.Combine(contentRootPath, databasePath));
        }

        return new AppSettings(
            new SourceSettings(
                uri,
                TimeSpan.FromHours(ReadInt(configuration, "Source:FreshForHours", 20, 1, 168)),
                TimeSpan.FromMinutes(ReadInt(configuration, "Source:ForceRefreshCooldownMinutes", 360, 5, 10080)),
                ReadInt(configuration, "Source:NightlyLocalHour", 3, 0, 23),
                ReadInt(configuration, "Source:NightlyLocalMinute", 15, 0, 59),
                configuration["Source:TimeZoneId"] ?? "Europe/Warsaw",
                ReadInt(configuration, "Source:RequestTimeoutSeconds", 30, 5, 300)),
            new DatabaseSettings(databasePath));
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
    TimeSpan FreshFor,
    TimeSpan ForceRefreshCooldown,
    int NightlyLocalHour,
    int NightlyLocalMinute,
    string TimeZoneId,
    int RequestTimeoutSeconds);

public sealed record DatabaseSettings(string Path);
