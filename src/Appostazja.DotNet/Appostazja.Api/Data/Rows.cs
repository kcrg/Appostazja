namespace ApostasyMap.Api.Data;

public sealed class PlaceRow
{
    public string Id { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string Address { get; set; } = string.Empty;
    public double Latitude { get; set; }
    public double Longitude { get; set; }
    public double Score { get; set; }
    public int RatingsCount { get; set; }
    public int PositiveRatings { get; set; }
    public int NeutralRatings { get; set; }
    public int NegativeRatings { get; set; }
}

public sealed record PlaceReportsRow(string PlaceId, string[] Aliases, Contracts.PlaceReportsDto Details);

public sealed record SourceInputRow(Uri Url, byte[] Payload, string? Etag, string? LastModified);

public sealed record DatasetStateRow
{
    public const int SingletonId = 1;

    public int Id { get; init; } = SingletonId;
    public string SourceUrl { get; init; } = string.Empty;
    public string? CoordinatesUrl { get; init; }
    public string? Version { get; init; }
    public long LastCheckedAtUnixSeconds { get; init; }
    public long LastChangedAtUnixSeconds { get; init; }
    public int PlaceCount { get; init; }

    /// <summary>Last refresh that contacted the sources, successful or not.</summary>
    public long LastAttemptAtUnixSeconds { get; init; }
    public string? LastAttemptOutcome { get; init; }
    public string? LastError { get; init; }

    /// <summary>Invalid CSV rows and GeoJSON features skipped by the last successful import.</summary>
    public int SkippedRows { get; init; }
    public int UnmatchedReports { get; init; }
}

public readonly record struct BoundingBox(
    double MinLatitude,
    double MaxLatitude,
    double MinLongitude,
    double MaxLongitude);
