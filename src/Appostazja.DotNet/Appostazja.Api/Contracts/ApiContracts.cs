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
    RatingBreakdownDto Ratings);

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
    string? CoordinatesUrl);

public sealed record ApiError(string Error);
