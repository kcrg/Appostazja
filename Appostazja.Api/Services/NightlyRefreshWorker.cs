using ApostasyMap.Api.Configuration;

namespace ApostasyMap.Api.Services;

public sealed class NightlyRefreshWorker(
    AppSettings settings,
    MapRefreshService refreshService,
    ILogger<NightlyRefreshWorker> logger) : BackgroundService
{
    private readonly TimeZoneInfo _timeZone = ResolveTimeZone(settings.Source.TimeZoneId);

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        await TryRefresh(stoppingToken);

        while (!stoppingToken.IsCancellationRequested)
        {
            var now = DateTimeOffset.UtcNow;
            var next = GetNextRunUtc(now);
            var delay = next - now;

            logger.LogInformation("Next scheduled source refresh: {NextRefreshUtc}.", next);
            await Task.Delay(delay, stoppingToken);
            await TryRefresh(stoppingToken);
        }
    }

    private async Task TryRefresh(CancellationToken cancellationToken)
    {
        try
        {
            var result = await refreshService.RefreshAsync(force: false, cancellationToken);
            logger.LogInformation(
                "Scheduled refresh finished with outcome {Outcome}; version {Version}; places {PlaceCount}.",
                result.Outcome,
                result.Version,
                result.PlaceCount);
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
        }
        catch (Exception exception)
        {
            logger.LogError(exception, "Scheduled source refresh failed. Existing cached data is kept.");
        }
    }

    private DateTimeOffset GetNextRunUtc(DateTimeOffset nowUtc)
    {
        var localNow = TimeZoneInfo.ConvertTime(nowUtc, _timeZone);
        var localCandidate = new DateTime(
            localNow.Year,
            localNow.Month,
            localNow.Day,
            settings.Source.NightlyLocalHour,
            settings.Source.NightlyLocalMinute,
            0,
            DateTimeKind.Unspecified);

        if (localCandidate <= localNow.DateTime)
        {
            localCandidate = localCandidate.AddDays(1);
        }

        var utc = TimeZoneInfo.ConvertTimeToUtc(localCandidate, _timeZone);
        return new DateTimeOffset(utc, TimeSpan.Zero);
    }

    private static TimeZoneInfo ResolveTimeZone(string id)
    {
        try
        {
            return TimeZoneInfo.FindSystemTimeZoneById(id);
        }
        catch (TimeZoneNotFoundException) when (OperatingSystem.IsWindows() && id == "Europe/Warsaw")
        {
            return TimeZoneInfo.FindSystemTimeZoneById("Central European Standard Time");
        }
    }
}
