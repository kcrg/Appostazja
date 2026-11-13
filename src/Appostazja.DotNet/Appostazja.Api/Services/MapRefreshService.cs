using System.Net;
using System.Security.Cryptography;
using System.Text.Json;
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
    internal static readonly TimeSpan RetryDelay = TimeSpan.FromHours(1);
    private readonly SemaphoreSlim _gate = new(1, 1);
    private DateTimeOffset _lastAttemptUtc = DateTimeOffset.MinValue;

    public async Task<RefreshResult> RefreshAsync(bool force, CancellationToken cancellationToken)
    {
        await _gate.WaitAsync(cancellationToken);
        try
        {
            var now = DateTimeOffset.UtcNow;
            var state = memoryCache.GetState();
            var lastCheck = UsesConfiguredSources(state)
                ? DateTimeOffset.FromUnixTimeSeconds(state.LastCheckedAtUnixSeconds)
                : DateTimeOffset.MinValue;

            if (force)
            {
                var allowedAt = (_lastAttemptUtc > lastCheck ? _lastAttemptUtc : lastCheck)
                    + settings.Source.ForceRefreshCooldown;
                if (allowedAt > now)
                {
                    return Result("force-throttled", state, now, forceRefreshAllowedAtUtc: allowedAt);
                }
            }
            else if (IsFresh(state, now))
            {
                return Result("fresh", state, now);
            }
            else if (_lastAttemptUtc + RetryDelay > now)
            {
                return Result("retry-throttled", state, now);
            }

            _lastAttemptUtc = now;
            var responses = await FetchAsync(settings.Source.Url, cancellationToken);
            var coordinates = await FetchAsync(settings.Source.CoordinatesUrl, cancellationToken);
            now = DateTimeOffset.UtcNow;

            var version = ComputeVersion(responses.Input.Payload, coordinates.Input.Payload);
            var inputs = new List<SourceInputRow>(2);
            if (responses.Changed)
            {
                inputs.Add(responses.Input);
            }

            if (coordinates.Changed)
            {
                inputs.Add(coordinates.Input);
            }

            var downloaded = responses.Downloaded || coordinates.Downloaded;
            if (UsesConfiguredSources(state) &&
                string.Equals(version, state.Version, StringComparison.Ordinal))
            {
                store.MarkChecked(now, inputs);
                var checkedState = state with { LastCheckedAtUnixSeconds = now.ToUnixTimeSeconds() };
                memoryCache.UpdateState(checkedState);
                return Result(downloaded ? "unchanged" : "not-modified", checkedState, now, downloaded);
            }

            (PlaceRow[] Places, PlaceReportsRow[] Reports, int UnmatchedReportCount) dataset;
            try
            {
                dataset = MapSourceParser.Parse(responses.Input.Payload, coordinates.Input.Payload);
            }
            catch (JsonException)
            {
                throw new InvalidDataException("The coordinate feed is not valid GeoJSON.");
            }

            if (dataset.UnmatchedReportCount > 0)
            {
                logger.LogWarning("{UnmatchedReportCount} CSV reports have no matching place in the coordinate feed.",
                    dataset.UnmatchedReportCount);
            }

            cancellationToken.ThrowIfCancellationRequested();
            store.ReplaceDataset(dataset.Places, dataset.Reports, inputs, version, now);

            var replacedState = new DatasetStateRow
            {
                SourceUrl = settings.Source.Url.AbsoluteUri,
                CoordinatesUrl = settings.Source.CoordinatesUrl.AbsoluteUri,
                Version = version,
                LastCheckedAtUnixSeconds = now.ToUnixTimeSeconds(),
                LastChangedAtUnixSeconds = now.ToUnixTimeSeconds(),
                PlaceCount = dataset.Places.Length
            };
            memoryCache.Replace(replacedState, dataset.Places, dataset.Reports);

            logger.LogInformation("Map dataset updated to {Version} with {PlaceCount} places.",
                version, dataset.Places.Length);

            return new RefreshResult("updated", Changed: true, DownloadedFullPayload: downloaded,
                dataset.Places.Length, version, now);
        }
        finally
        {
            _gate.Release();
        }
    }

    public bool IsFresh(DatasetStateRow state, DateTimeOffset nowUtc) =>
        state.PlaceCount > 0 &&
        UsesConfiguredSources(state) &&
        nowUtc - DateTimeOffset.FromUnixTimeSeconds(state.LastCheckedAtUnixSeconds) < settings.Source.FreshFor;

    private bool UsesConfiguredSources(DatasetStateRow state) =>
        string.Equals(state.SourceUrl, settings.Source.Url.AbsoluteUri, StringComparison.Ordinal) &&
        string.Equals(state.CoordinatesUrl, settings.Source.CoordinatesUrl.AbsoluteUri, StringComparison.Ordinal);

    private async Task<(SourceInputRow Input, bool Changed, bool Downloaded)> FetchAsync(
        Uri url, CancellationToken cancellationToken)
    {
        var cached = store.LoadInput(url);
        using var request = new HttpRequestMessage(HttpMethod.Get, url);
        if (cached?.Etag is { } etag)
        {
            request.Headers.TryAddWithoutValidation("If-None-Match", etag);
        }

        if (cached?.LastModified is { } lastModified)
        {
            request.Headers.TryAddWithoutValidation("If-Modified-Since", lastModified);
        }

        using var response = await httpClient.SendAsync(request, cancellationToken);
        var responseEtag = response.Headers.ETag?.ToString();
        var responseLastModified = response.Content.Headers.LastModified?.ToString("R");

        if (response.StatusCode == HttpStatusCode.NotModified && cached is not null)
        {
            var updated = cached with
            {
                Etag = responseEtag ?? cached.Etag,
                LastModified = responseLastModified ?? cached.LastModified
            };
            return (updated, updated != cached, false);
        }

        response.EnsureSuccessStatusCode();
        var payload = await response.Content.ReadAsByteArrayAsync(cancellationToken);
        if (payload.Length == 0)
        {
            throw new InvalidDataException("A source returned an empty response.");
        }

        var input = new SourceInputRow(url, payload, responseEtag, responseLastModified);
        var changed = cached is null ||
            input.Etag != cached.Etag || input.LastModified != cached.LastModified ||
            !payload.AsSpan().SequenceEqual(cached.Payload);
        return (input, changed, true);
    }

    private static string ComputeVersion(byte[] responses, byte[] coordinates)
    {
        using var hash = IncrementalHash.CreateHash(HashAlgorithmName.SHA256);
        hash.AppendData("responses-v1"u8);
        hash.AppendData(SHA256.HashData(responses));
        hash.AppendData(SHA256.HashData(coordinates));
        return Convert.ToHexStringLower(hash.GetHashAndReset());
    }

    private static RefreshResult Result(
        string outcome,
        DatasetStateRow state,
        DateTimeOffset checkedAtUtc,
        bool downloaded = false,
        DateTimeOffset? forceRefreshAllowedAtUtc = null) =>
        new(outcome, Changed: false, downloaded, state.PlaceCount, state.Version,
            checkedAtUtc, forceRefreshAllowedAtUtc);
}
