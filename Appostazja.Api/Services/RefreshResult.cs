namespace ApostasyMap.Api.Services;

public sealed record RefreshResult(
    string Outcome,
    bool Changed,
    bool DownloadedFullPayload,
    int PlaceCount,
    string? Version,
    DateTimeOffset CheckedAtUtc,
    DateTimeOffset? ForceRefreshAllowedAtUtc = null);
