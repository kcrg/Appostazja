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
    string? LastActionDate = null);

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
