using ApostasyMap.Api.Configuration;
using ApostasyMap.Api.Contracts;
using ApostasyMap.Api.Data;
using ApostasyMap.Api.Json;
using ApostasyMap.Api.Services;
using Microsoft.Net.Http.Headers;
using static ApostasyMap.Api.Endpoints.HttpCaching;

namespace ApostasyMap.Api.Endpoints;

public static class MapEndpoints
{
    public static IEndpointRouteBuilder MapMapEndpoints(this IEndpointRouteBuilder endpoints)
    {
        var api = endpoints.MapGroup("/api/v1");

        api.MapGet("/places", GetPlaces)
            .WithName("GetPlaces")
            .WithSummary("All places, or the places inside a bounding box.")
            .Produces<PlaceDto[]>(contentType: "application/json")
            .Produces(StatusCodes.Status304NotModified)
            .Produces<ApiError>(StatusCodes.Status400BadRequest)
            .Produces<ApiError>(StatusCodes.Status503ServiceUnavailable);
        api.MapGet("/places/{id}", GetPlace)
            .WithName("GetPlace")
            .Produces<PlaceDto>()
            .Produces(StatusCodes.Status304NotModified)
            .Produces<ApiError>(StatusCodes.Status404NotFound);
        api.MapGet("/places/{id}/reports", GetReports)
            .WithName("GetPlaceReports")
            .WithSummary("Parish website and its reports.")
            .Produces<PlaceReportsDto>()
            .Produces(StatusCodes.Status304NotModified)
            .Produces<ApiError>(StatusCodes.Status404NotFound);
        api.MapGet("/dataset", GetDatasetStatus)
            .WithName("GetDatasetStatus")
            .WithSummary("Dataset version, freshness and the result of the last import attempt.")
            .Produces<DatasetStatusDto>();

        return endpoints;
    }

    private static async Task<IResult> GetPlaces(
        HttpContext context,
        MapMemoryCache memoryCache,
        MapRefreshService refreshService,
        ILoggerFactory loggerFactory,
        double? minLat,
        double? maxLat,
        double? minLon,
        double? maxLon,
        bool forceRefresh = false,
        CancellationToken cancellationToken = default)
    {
        var boundsResult = TryCreateBoundingBox(minLat, maxLat, minLon, maxLon);
        if (boundsResult.Error is not null)
        {
            return Results.BadRequest(new ApiError(boundsResult.Error));
        }

        if (forceRefresh && boundsResult.Bounds is not null)
        {
            return Results.BadRequest(new ApiError(
                "forceRefresh can only be used when requesting the whole dataset."));
        }

        if (forceRefresh)
        {
            await ForceRefreshAsync(context, refreshService, loggerFactory, cancellationToken);
        }

        if (boundsResult.Bounds is null)
        {
            var gzip = AcceptsGzip(context.Request);
            var fullDataset = memoryCache.GetFullDataset(gzip);
            if (fullDataset.State.PlaceCount <= 0)
            {
                return NoData();
            }

            context.Response.Headers.Vary = HeaderNames.AcceptEncoding;
            var version = gzip ? $"{fullDataset.State.Version}-gzip" : fullDataset.State.Version;
            ApplyDatasetHeaders(context.Response, fullDataset.State, version);

            if (MatchesIfNoneMatch(context.Request, version))
            {
                return Results.StatusCode(StatusCodes.Status304NotModified);
            }

            if (gzip)
            {
                context.Response.Headers.ContentEncoding = "gzip";
            }

            return Results.Bytes(
                fullDataset.Json,
                contentType: "application/json; charset=utf-8");
        }

        var (state, places) = memoryCache.GetPlaces(boundsResult.Bounds.Value);
        if (state.PlaceCount <= 0)
        {
            return NoData();
        }

        ApplyDatasetHeaders(context.Response, state);

        if (MatchesIfNoneMatch(context.Request, state.Version))
        {
            return Results.StatusCode(StatusCodes.Status304NotModified);
        }

        return Results.Ok(places);
    }

    private static async Task ForceRefreshAsync(
        HttpContext context,
        MapRefreshService refreshService,
        ILoggerFactory loggerFactory,
        CancellationToken cancellationToken)
    {
        try
        {
            var refreshResult = await refreshService.RefreshAsync(force: true, cancellationToken);
            context.Response.Headers["X-Refresh-Outcome"] = refreshResult.Outcome;

            if (refreshResult.ForceRefreshAllowedAtUtc is { } allowedAt)
            {
                context.Response.Headers["X-Force-Refresh-Allowed-At"] = allowedAt.ToString("O");
            }
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            throw;
        }
        catch (Exception exception) when (exception is HttpRequestException or OperationCanceledException)
        {
            loggerFactory.CreateLogger("ForceRefresh")
                .LogWarning(exception, "Forced source refresh failed; serving cached data.");
            context.Response.Headers["X-Refresh-Outcome"] = "failed";
        }
        catch (InvalidDataException exception)
        {
            loggerFactory.CreateLogger("ForceRefresh")
                .LogWarning(exception, "Forced source refresh returned invalid data; serving cached data.");
            context.Response.Headers["X-Refresh-Outcome"] = "invalid-data";
        }
    }

    private static IResult NoData() =>
        Results.Json(
            new ApiError("No cached map data is currently available."),
            AppJsonSerializerContext.Default.ApiError,
            statusCode: StatusCodes.Status503ServiceUnavailable);

    private static IResult PlaceNotFound(string id) =>
        Results.Json(
            new ApiError($"Place '{id}' was not found."),
            AppJsonSerializerContext.Default.ApiError,
            statusCode: StatusCodes.Status404NotFound);

    private static IResult GetPlace(HttpContext context, MapMemoryCache memoryCache, string id)
    {
        var (state, place, _) = memoryCache.GetPlace(id);
        if (place is null)
        {
            return PlaceNotFound(id);
        }

        ApplyDatasetHeaders(context.Response, state);

        return MatchesIfNoneMatch(context.Request, state.Version)
            ? Results.StatusCode(StatusCodes.Status304NotModified)
            : Results.Ok(place);
    }

    private static IResult GetReports(HttpContext context, MapMemoryCache memoryCache, string id)
    {
        var (state, place, reports) = memoryCache.GetPlace(id);
        if (place is null)
        {
            return PlaceNotFound(id);
        }

        ApplyDatasetHeaders(context.Response, state);
        return MatchesIfNoneMatch(context.Request, state.Version)
            ? Results.StatusCode(StatusCodes.Status304NotModified)
            : Results.Ok(reports ?? new PlaceReportsDto(null, []));
    }

    private static IResult GetDatasetStatus(
        HttpContext context,
        AppSettings settings,
        MapMemoryCache memoryCache,
        MapRefreshService refreshService,
        TimeProvider time)
    {
        var state = memoryCache.GetState();
        // Freshness and import status change without a new dataset version, so the dataset ETag would lie here.
        context.Response.Headers.CacheControl = "no-cache";

        return Results.Ok(new DatasetStatusDto(
            state.SourceUrl,
            state.Version,
            state.PlaceCount,
            FromUnixSecondsOrNull(state.LastCheckedAtUnixSeconds),
            FromUnixSecondsOrNull(state.LastChangedAtUnixSeconds),
            refreshService.IsFresh(state, time.GetUtcNow()),
            (int)settings.Source.FreshFor.TotalHours,
            state.CoordinatesUrl,
            new ImportStatusDto(
                FromUnixSecondsOrNull(state.LastAttemptAtUnixSeconds),
                state.LastAttemptOutcome,
                state.LastError,
                state.SkippedRows,
                state.UnmatchedReports)));
    }

    private static (BoundingBox? Bounds, string? Error) TryCreateBoundingBox(
        double? minLat,
        double? maxLat,
        double? minLon,
        double? maxLon)
    {
        var anyBounds = minLat.HasValue || maxLat.HasValue || minLon.HasValue || maxLon.HasValue;
        if (!anyBounds)
        {
            return (null, null);
        }

        if (!minLat.HasValue || !maxLat.HasValue || !minLon.HasValue || !maxLon.HasValue)
        {
            return (null, "Bounding box requires minLat, maxLat, minLon and maxLon.");
        }

        if (!IsValidLatitude(minLat.Value) || !IsValidLatitude(maxLat.Value) ||
            !IsValidLongitude(minLon.Value) || !IsValidLongitude(maxLon.Value) ||
            minLat.Value > maxLat.Value || minLon.Value > maxLon.Value)
        {
            return (null, "Invalid bounding box.");
        }

        return (new BoundingBox(minLat.Value, maxLat.Value, minLon.Value, maxLon.Value), null);
    }

    private static bool IsValidLatitude(double value) => double.IsFinite(value) && value is >= -90 and <= 90;
    private static bool IsValidLongitude(double value) => double.IsFinite(value) && value is >= -180 and <= 180;

    private static DateTimeOffset? FromUnixSecondsOrNull(long value) =>
        value > 0 ? DateTimeOffset.FromUnixTimeSeconds(value) : null;
}
