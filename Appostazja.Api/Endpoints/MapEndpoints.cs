using System.Globalization;
using ApostasyMap.Api.Configuration;
using ApostasyMap.Api.Contracts;
using ApostasyMap.Api.Data;
using ApostasyMap.Api.Json;
using ApostasyMap.Api.Services;

namespace ApostasyMap.Api.Endpoints;

public static class MapEndpoints
{
    public static IEndpointRouteBuilder MapMapEndpoints(this IEndpointRouteBuilder endpoints)
    {
        var api = endpoints.MapGroup("/api/v1");

        api.MapGet("/places", GetPlaces);
        api.MapGet("/places/{id}", GetPlace);
        api.MapGet("/dataset", GetDatasetStatus);

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
            catch (HttpRequestException exception)
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

        if (boundsResult.Bounds is null)
        {
            var fullDataset = memoryCache.GetFullDataset();
            if (fullDataset.State.PlaceCount <= 0)
            {
                return Results.Json(
                    new ApiError("No cached map data is currently available."),
                    AppJsonSerializerContext.Default.ApiError,
                    statusCode: StatusCodes.Status503ServiceUnavailable);
            }

            ApplyDatasetHeaders(context, fullDataset.State);

            if (MatchesIfNoneMatch(context, fullDataset.State.Version))
            {
                return Results.StatusCode(StatusCodes.Status304NotModified);
            }

            return Results.Bytes(
                fullDataset.Json,
                contentType: "application/json; charset=utf-8");
        }

        var state = memoryCache.GetState();
        if (state.PlaceCount <= 0)
        {
            return Results.Json(
                new ApiError("No cached map data is currently available."),
                AppJsonSerializerContext.Default.ApiError,
                statusCode: StatusCodes.Status503ServiceUnavailable);
        }

        ApplyDatasetHeaders(context, state);

        if (MatchesIfNoneMatch(context, state.Version))
        {
            return Results.StatusCode(StatusCodes.Status304NotModified);
        }

        return Results.Ok(memoryCache.GetPlaces(boundsResult.Bounds));
    }

    private static IResult GetPlace(HttpContext context, MapMemoryCache memoryCache, string id)
    {
        var state = memoryCache.GetState();
        ApplyDatasetHeaders(context, state);

        if (MatchesIfNoneMatch(context, state.Version))
        {
            return Results.StatusCode(StatusCodes.Status304NotModified);
        }

        var place = memoryCache.GetPlace(id);
        return place is null ? Results.NotFound() : Results.Ok(place);
    }

    private static IResult GetDatasetStatus(
        HttpContext context,
        AppSettings settings,
        MapMemoryCache memoryCache,
        MapRefreshService refreshService)
    {
        var state = memoryCache.GetState();
        ApplyDatasetHeaders(context, state);

        var now = DateTimeOffset.UtcNow;
        return Results.Ok(new DatasetStatusDto(
            state.SourceUrl,
            state.Version,
            state.PlaceCount,
            FromUnixSecondsOrNull(state.LastCheckedAtUnixSeconds),
            FromUnixSecondsOrNull(state.LastChangedAtUnixSeconds),
            refreshService.IsFresh(state, now),
            (int)settings.Source.FreshFor.TotalHours));
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

    private static void ApplyDatasetHeaders(HttpContext context, DatasetStateRow state)
    {
        context.Response.Headers.CacheControl = "public, max-age=900, must-revalidate";

        if (!string.IsNullOrWhiteSpace(state.Version))
        {
            context.Response.Headers.ETag = $"\"{state.Version}\"";
        }

        if (state.LastChangedAtUnixSeconds > 0)
        {
            context.Response.Headers.LastModified = DateTimeOffset
                .FromUnixTimeSeconds(state.LastChangedAtUnixSeconds)
                .ToString("R", CultureInfo.InvariantCulture);
        }
    }

    private static bool MatchesIfNoneMatch(HttpContext context, string? version)
    {
        if (string.IsNullOrWhiteSpace(version))
        {
            return false;
        }

        var expected = $"\"{version}\"";
        foreach (var value in context.Request.Headers.IfNoneMatch)
        {
            if (string.Equals(value, expected, StringComparison.Ordinal) || value == "*")
            {
                return true;
            }
        }

        return false;
    }

    private static bool IsValidLatitude(double value) => double.IsFinite(value) && value is >= -90 and <= 90;
    private static bool IsValidLongitude(double value) => double.IsFinite(value) && value is >= -180 and <= 180;

    private static DateTimeOffset? FromUnixSecondsOrNull(long value) =>
        value > 0 ? DateTimeOffset.FromUnixTimeSeconds(value) : null;
}
