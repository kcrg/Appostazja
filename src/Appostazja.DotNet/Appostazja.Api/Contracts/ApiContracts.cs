namespace ApostasyMap.Api.Contracts;

public sealed record RatingBreakdownDto(
    int Positive,
    int Neutral,
    int Negative,
    int Total);

public sealed record PlaceDto(
    string Id,
    string Name,
    string Address,
    double Latitude,
    double Longitude,
    double Score,
    RatingBreakdownDto Ratings,
    StageRatingsDto? Stages = null,
    string? LastActionDate = null,
    /// <summary>Score on a 0-5 scale rounded to one decimal, for display; null without reports.</summary>
    double? Rating = null,
    /// <summary>positive, mixed, negative, or none without reports. Clients colour pins by it.</summary>
    string? Tone = null);

/// <summary>Ratings split by the stage the report describes; they add up to <see cref="PlaceDto.Ratings"/>.</summary>
public sealed record StageRatingsDto(
    RatingBreakdownDto BeforeApostasy,
    RatingBreakdownDto Apostasy,
    RatingBreakdownDto AfterApostasy);

public sealed record ReportDto(string Stage, string Rating, string? Date, string? Comment);

public sealed record PlaceReportsDto(string? Website, ReportDto[] Reports);

public sealed record DatasetStatusDto(
    string SourceUrl,
    string? Version,
    int PlaceCount,
    DateTimeOffset? LastCheckedAtUtc,
    DateTimeOffset? LastChangedAtUtc,
    bool IsFresh,
    int FreshForHours,
    string? CoordinatesUrl,
    ImportStatusDto LastImport);

/// <param name="LastAttemptAtUtc">Last refresh that contacted the sources.</param>
/// <param name="Outcome">updated, unchanged, not-modified, failed or invalid-data.</param>
/// <param name="Error">Why the last attempt did not update the data; null after a successful check.</param>
/// <param name="SkippedRows">Invalid CSV rows and GeoJSON features skipped by the published dataset.</param>
/// <param name="UnmatchedReports">CSV reports without a matching coordinate in the published dataset.</param>
public sealed record ImportStatusDto(
    DateTimeOffset? LastAttemptAtUtc,
    string? Outcome,
    string? Error,
    int SkippedRows,
    int UnmatchedReports);

public sealed record HealthDto(string Status, string? Reason = null);

public sealed record ApiError(string Error);

/// <param name="Total">All places matching the query and filters, before paging.</param>
public sealed record PlaceSearchResultDto(int Total, int Offset, int Limit, PlaceDto[] Items);
