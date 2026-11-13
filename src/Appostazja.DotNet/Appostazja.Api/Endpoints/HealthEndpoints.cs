using System.Globalization;
using ApostasyMap.Api.Configuration;
using ApostasyMap.Api.Contracts;
using ApostasyMap.Api.Data;
using ApostasyMap.Api.Json;

namespace ApostasyMap.Api.Endpoints;

public static class HealthEndpoints
{
    public static IEndpointRouteBuilder MapHealthEndpoints(this IEndpointRouteBuilder endpoints)
    {
        // Liveness only says the process answers; readiness says the data is worth serving.
        endpoints.MapGet("/health", Live).ExcludeFromDescription();
        endpoints.MapGet("/health/live", Live).ExcludeFromDescription();
        endpoints.MapGet("/health/ready", Ready).ExcludeFromDescription();
        return endpoints;
    }

    private static IResult Live(HttpContext context)
    {
        context.Response.Headers.CacheControl = "no-store";
        return Results.Text("ok");
    }

    private static IResult Ready(
        HttpContext context, AppSettings settings, MapMemoryCache memoryCache, TimeProvider time)
    {
        context.Response.Headers.CacheControl = "no-store";
        var reason = GetNotReadyReason(memoryCache.GetState(), time.GetUtcNow(), settings.Import.ReadyMaxAge);
        return Results.Json(
            new HealthDto(reason is null ? "ready" : "not-ready", reason),
            AppJsonSerializerContext.Default.HealthDto,
            statusCode: reason is null ? StatusCodes.Status200OK : StatusCodes.Status503ServiceUnavailable);
    }

    internal static string? GetNotReadyReason(DatasetStateRow state, DateTimeOffset nowUtc, TimeSpan maxAge)
    {
        if (state.PlaceCount <= 0)
        {
            return "No map data has been imported yet.";
        }

        var lastChecked = DateTimeOffset.FromUnixTimeSeconds(state.LastCheckedAtUnixSeconds);
        if (nowUtc - lastChecked > maxAge)
        {
            var age = string.Create(CultureInfo.InvariantCulture, $"{(nowUtc - lastChecked).TotalHours:0}");
            return state.LastError is { } error
                ? $"Data was last checked {age} h ago. Last attempt: {error}"
                : $"Data was last checked {age} h ago.";
        }

        return null;
    }
}
