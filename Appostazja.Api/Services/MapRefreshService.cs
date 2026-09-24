using System.Net;
using System.Security.Cryptography;
using ApostasyMap.Api.Configuration;
using ApostasyMap.Api.Data;

namespace ApostasyMap.Api.Services;

public sealed class MapRefreshService(
    AppSettings settings,
    MapStore store,
    MapMemoryCache memoryCache,
    HttpClient httpClient,
    ILogger<MapRefreshService> logger)
{
    private readonly SemaphoreSlim _gate = new(1, 1);
    private DateTimeOffset _lastForcedRefreshAttemptUtc = DateTimeOffset.MinValue;

    public async Task<RefreshResult> RefreshAsync(bool force, CancellationToken cancellationToken)
    {
        await _gate.WaitAsync(cancellationToken);
        try
        {
            var now = DateTimeOffset.UtcNow;
            var state = memoryCache.GetState();

            if (force)
            {
                var forceAllowedAt = GetForceRefreshAllowedAtUtc(state);
                if (forceAllowedAt > now)
                {
                    logger.LogInformation(
                        "Forced source refresh skipped because the global cooldown is active until {ForceAllowedAtUtc}.",
                        forceAllowedAt);

                    return Result(
                        "force-throttled",
                        changed: false,
                        downloadedFullPayload: false,
                        state,
                        now,
                        forceAllowedAt);
                }

                // Set before touching the upstream so failed forced attempts are rate-limited too.
                _lastForcedRefreshAttemptUtc = now;
            }
            else if (IsFresh(state, now))
            {
                return Result("fresh", changed: false, downloadedFullPayload: false, state, now);
            }

            using var request = new HttpRequestMessage(HttpMethod.Get, settings.Source.Url);
            if (!force)
            {
                if (!string.IsNullOrWhiteSpace(state.SourceEtag))
                {
                    request.Headers.TryAddWithoutValidation("If-None-Match", state.SourceEtag);
                }

                if (!string.IsNullOrWhiteSpace(state.SourceLastModified))
                {
                    request.Headers.TryAddWithoutValidation("If-Modified-Since", state.SourceLastModified);
                }
            }

            using var response = await httpClient.SendAsync(
                request,
                HttpCompletionOption.ResponseHeadersRead,
                cancellationToken);

            var responseEtag = response.Headers.ETag?.ToString();
            var responseLastModified = response.Content.Headers.LastModified?.ToString("R");

            if (response.StatusCode == HttpStatusCode.NotModified)
            {
                var updatedState = MarkChecked(state, now, responseEtag, responseLastModified);
                return Result("not-modified", changed: false, downloadedFullPayload: false, updatedState, now);
            }

            response.EnsureSuccessStatusCode();

            var payload = await response.Content.ReadAsByteArrayAsync(cancellationToken);
            if (payload.Length == 0)
            {
                throw new InvalidDataException("Source returned an empty response.");
            }

            var version = Convert.ToHexString(SHA256.HashData(payload)).ToLowerInvariant();
            if (string.Equals(version, state.Version, StringComparison.Ordinal))
            {
                var updatedState = MarkChecked(state, now, responseEtag, responseLastModified);
                return Result("unchanged", changed: false, downloadedFullPayload: true, updatedState, now);
            }

            var places = MapSourceParser.Parse(payload);
            store.ReplaceDataset(places, version, responseEtag, responseLastModified, now);

            var replacedState = new DatasetStateRow
            {
                SourceUrl = settings.Source.Url.ToString(),
                Version = version,
                SourceEtag = responseEtag,
                SourceLastModified = responseLastModified,
                LastCheckedAtUnixSeconds = now.ToUnixTimeSeconds(),
                LastChangedAtUnixSeconds = now.ToUnixTimeSeconds(),
                PlaceCount = places.Length
            };
            memoryCache.Replace(replacedState, places);

            logger.LogInformation(
                "Map dataset updated to {Version} with {PlaceCount} places.",
                version,
                places.Length);

            return new RefreshResult(
                "updated",
                Changed: true,
                DownloadedFullPayload: true,
                PlaceCount: places.Length,
                Version: version,
                CheckedAtUtc: now);
        }
        finally
        {
            _gate.Release();
        }
    }

    public bool IsFresh(DatasetStateRow state, DateTimeOffset nowUtc)
    {
        if (state.PlaceCount <= 0 || state.LastCheckedAtUnixSeconds <= 0)
        {
            return false;
        }

        var checkedAt = DateTimeOffset.FromUnixTimeSeconds(state.LastCheckedAtUnixSeconds);
        return nowUtc - checkedAt < settings.Source.FreshFor;
    }

    private DatasetStateRow MarkChecked(
        DatasetStateRow state,
        DateTimeOffset checkedAtUtc,
        string? sourceEtag,
        string? sourceLastModified)
    {
        store.MarkChecked(checkedAtUtc, sourceEtag, sourceLastModified);

        var updatedState = state with
        {
            SourceEtag = sourceEtag ?? state.SourceEtag,
            SourceLastModified = sourceLastModified ?? state.SourceLastModified,
            LastCheckedAtUnixSeconds = checkedAtUtc.ToUnixTimeSeconds()
        };
        memoryCache.UpdateState(updatedState);
        return updatedState;
    }

    private DateTimeOffset GetForceRefreshAllowedAtUtc(DatasetStateRow state)
    {
        var lastSuccessfulCheckUtc = state.LastCheckedAtUnixSeconds > 0
            ? DateTimeOffset.FromUnixTimeSeconds(state.LastCheckedAtUnixSeconds)
            : DateTimeOffset.MinValue;

        var lastRelevantAttemptUtc = _lastForcedRefreshAttemptUtc > lastSuccessfulCheckUtc
            ? _lastForcedRefreshAttemptUtc
            : lastSuccessfulCheckUtc;

        return lastRelevantAttemptUtc == DateTimeOffset.MinValue
            ? DateTimeOffset.MinValue
            : lastRelevantAttemptUtc + settings.Source.ForceRefreshCooldown;
    }

    private static RefreshResult Result(
        string outcome,
        bool changed,
        bool downloadedFullPayload,
        DatasetStateRow state,
        DateTimeOffset checkedAtUtc,
        DateTimeOffset? forceRefreshAllowedAtUtc = null) =>
        new(
            outcome,
            changed,
            downloadedFullPayload,
            state.PlaceCount,
            state.Version,
            checkedAtUtc,
            forceRefreshAllowedAtUtc);
}
