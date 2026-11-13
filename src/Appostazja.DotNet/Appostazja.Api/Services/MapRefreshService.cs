using System.Globalization;
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
    ILogger<MapRefreshService> logger,
    TimeProvider timeProvider)
{
    internal static readonly TimeSpan RetryDelay = TimeSpan.FromHours(1);
    private readonly TimeProvider _time = timeProvider;
    private readonly SemaphoreSlim _gate = new(1, 1);
    private DateTimeOffset _lastAttemptUtc = DateTimeOffset.MinValue;

    public async Task<RefreshResult> RefreshAsync(bool force, CancellationToken cancellationToken)
    {
        await _gate.WaitAsync(cancellationToken);
        try
        {
            var now = _time.GetUtcNow();
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
            try
            {
                return await RefreshFromSourcesAsync(state, cancellationToken);
            }
            catch (Exception exception) when (!cancellationToken.IsCancellationRequested &&
                                              DescribeFailure(exception) is { } failure)
            {
                RecordFailure(state, failure.Outcome, failure.Error);
                throw;
            }
        }
        finally
        {
            _gate.Release();
        }
    }

    private async Task<RefreshResult> RefreshFromSourcesAsync(DatasetStateRow state, CancellationToken cancellationToken)
    {
        var responses = await FetchAsync(settings.Source.Url, ResponsesCsv.Sanitize, cancellationToken);
        var coordinates = await FetchAsync(settings.Source.CoordinatesUrl, transform: null, cancellationToken);
        var now = _time.GetUtcNow();

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
        if (UsesConfiguredSources(state) && state.PlaceCount > 0 &&
            string.Equals(version, state.Version, StringComparison.Ordinal))
        {
            var outcome = downloaded ? "unchanged" : "not-modified";
            store.MarkChecked(now, inputs, outcome);
            var checkedState = state with
            {
                LastCheckedAtUnixSeconds = now.ToUnixTimeSeconds(),
                LastAttemptAtUnixSeconds = now.ToUnixTimeSeconds(),
                LastAttemptOutcome = outcome,
                LastError = null
            };
            memoryCache.UpdateState(checkedState);
            return Result(outcome, checkedState, now, downloaded);
        }

        var dataset = MapSourceParser.Parse(responses.Input.Payload, coordinates.Input.Payload);
        Validate(dataset, state);

        if (dataset.SkippedRows > 0)
        {
            logger.LogWarning("Skipped {SkippedReportRows} invalid CSV rows and {SkippedFeatures} invalid features.",
                dataset.SkippedReportRows, dataset.SkippedFeatures);
        }

        if (dataset.UnmatchedReportCount > 0)
        {
            logger.LogWarning("{UnmatchedReportCount} CSV reports have no matching place in the coordinate feed.",
                dataset.UnmatchedReportCount);
        }

        cancellationToken.ThrowIfCancellationRequested();
        store.ReplaceDataset(dataset.Places, dataset.Reports, inputs, version, now,
            dataset.SkippedRows, dataset.UnmatchedReportCount);

        var replacedState = new DatasetStateRow
        {
            SourceUrl = settings.Source.Url.AbsoluteUri,
            CoordinatesUrl = settings.Source.CoordinatesUrl.AbsoluteUri,
            Version = version,
            LastCheckedAtUnixSeconds = now.ToUnixTimeSeconds(),
            LastChangedAtUnixSeconds = now.ToUnixTimeSeconds(),
            PlaceCount = dataset.Places.Length,
            LastAttemptAtUnixSeconds = now.ToUnixTimeSeconds(),
            LastAttemptOutcome = "updated",
            SkippedRows = dataset.SkippedRows,
            UnmatchedReports = dataset.UnmatchedReportCount
        };
        memoryCache.Replace(replacedState, dataset.Places, dataset.Reports);

        logger.LogInformation("Map dataset updated to {Version} with {PlaceCount} places.",
            version, dataset.Places.Length);

        return new RefreshResult("updated", Changed: true, DownloadedFullPayload: downloaded,
            dataset.Places.Length, version, now);
    }

    /// <summary>Rejects imports that look like an upstream format change rather than real data changes.</summary>
    private void Validate(ParsedDataset dataset, DatasetStateRow previous)
    {
        var import = settings.Import;
        if (dataset.TotalRows > 0 && (double)dataset.SkippedRows / dataset.TotalRows > import.MaxSkippedRowRatio)
        {
            throw new InvalidDataException(string.Create(CultureInfo.InvariantCulture,
                $"Too many invalid source rows: {dataset.SkippedRows} of {dataset.TotalRows} " +
                $"({dataset.SkippedReportRows} CSV rows, {dataset.SkippedFeatures} features)."));
        }

        if (previous.PlaceCount > 0 &&
            dataset.Places.Length < previous.PlaceCount * (1 - import.MaxPlaceDropRatio))
        {
            throw new InvalidDataException(string.Create(CultureInfo.InvariantCulture,
                $"Place count dropped from {previous.PlaceCount} to {dataset.Places.Length}."));
        }
    }

    private void RecordFailure(DatasetStateRow state, string outcome, string error)
    {
        var now = _time.GetUtcNow();
        try
        {
            store.RecordFailure(now, outcome, error);
            memoryCache.UpdateState(state with
            {
                LastAttemptAtUnixSeconds = now.ToUnixTimeSeconds(),
                LastAttemptOutcome = outcome,
                LastError = error
            });
        }
        catch (Exception exception)
        {
            logger.LogError(exception, "Could not persist the failed refresh status.");
        }
    }

    /// <summary>Publicly visible failure summary; avoids leaking exception details beyond what is useful.</summary>
    private static (string Outcome, string Error)? DescribeFailure(Exception exception) => exception switch
    {
        InvalidDataException invalid => ("invalid-data", invalid.Message),
        HttpRequestException { StatusCode: { } status } => ("failed", $"Source returned HTTP {(int)status}."),
        HttpRequestException => ("failed", "Source could not be reached."),
        OperationCanceledException => ("failed", "Source request timed out."),
        _ => null
    };

    public bool IsFresh(DatasetStateRow state, DateTimeOffset nowUtc) =>
        state.PlaceCount > 0 &&
        UsesConfiguredSources(state) &&
        nowUtc - DateTimeOffset.FromUnixTimeSeconds(state.LastCheckedAtUnixSeconds) < settings.Source.FreshFor;

    private bool UsesConfiguredSources(DatasetStateRow state) =>
        string.Equals(state.SourceUrl, settings.Source.Url.AbsoluteUri, StringComparison.Ordinal) &&
        string.Equals(state.CoordinatesUrl, settings.Source.CoordinatesUrl.AbsoluteUri, StringComparison.Ordinal);

    private async Task<(SourceInputRow Input, bool Changed, bool Downloaded)> FetchAsync(
        Uri url, Func<byte[], byte[]>? transform, CancellationToken cancellationToken)
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

        using var response = await httpClient.SendAsync(
            request, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
        var responseEtag = response.Headers.ETag?.ToString();
        var responseLastModified = response.Content.Headers.LastModified?.ToString("R");

        if (response.StatusCode == HttpStatusCode.NotModified && cached is not null)
        {
            // Older databases may still hold an unsanitized payload; transforming is idempotent.
            var payload = transform is null ? cached.Payload : transform(cached.Payload);
            var updated = new SourceInputRow(url, payload,
                responseEtag ?? cached.Etag, responseLastModified ?? cached.LastModified);
            var changed = updated.Etag != cached.Etag || updated.LastModified != cached.LastModified ||
                !payload.AsSpan().SequenceEqual(cached.Payload);
            return (updated, changed, false);
        }

        response.EnsureSuccessStatusCode();
        var raw = await ReadLimitedAsync(response.Content, url, cancellationToken);
        if (raw.Length == 0)
        {
            throw new InvalidDataException("A source returned an empty response.");
        }

        var input = new SourceInputRow(url, transform is null ? raw : transform(raw), responseEtag, responseLastModified);
        var inputChanged = cached is null ||
            input.Etag != cached.Etag || input.LastModified != cached.LastModified ||
            !input.Payload.AsSpan().SequenceEqual(cached.Payload);
        return (input, inputChanged, true);
    }

    private async Task<byte[]> ReadLimitedAsync(HttpContent content, Uri url, CancellationToken cancellationToken)
    {
        var limit = settings.Source.MaxResponseBytes;
        if (content.Headers.ContentLength > limit)
        {
            throw TooLarge(url, limit);
        }

        await using var stream = await content.ReadAsStreamAsync(cancellationToken);
        using var buffer = new MemoryStream();
        var chunk = new byte[81920];
        int read;
        while ((read = await stream.ReadAsync(chunk, cancellationToken)) > 0)
        {
            if (buffer.Length + read > limit)
            {
                throw TooLarge(url, limit);
            }

            buffer.Write(chunk, 0, read);
        }

        return buffer.ToArray();
    }

    private static InvalidDataException TooLarge(Uri url, long limit) =>
        new(string.Create(CultureInfo.InvariantCulture,
            $"Source {url.AbsolutePath} is larger than {limit / (1024 * 1024)} MB; raise Source:MaxResponseMegabytes."));

    private static string ComputeVersion(byte[] responses, byte[] coordinates)
    {
        using var hash = IncrementalHash.CreateHash(HashAlgorithmName.SHA256);
        // Bump when import or sanitizing rules change so the next refresh rebuilds the dataset.
        hash.AppendData("responses-v3"u8);
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
