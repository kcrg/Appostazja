using System.Net.Http.Json;
using Appostazja.Core.Models;

namespace Appostazja.Core.Services;

public sealed class MapDataService(HttpClient httpClient) : IMapDataService
{
    public static readonly Uri Endpoint = new("https://mapaapostazji.pl/api/map-data.php");

    private readonly SemaphoreSlim cacheLock = new(1, 1);
    private IReadOnlyList<MapFeature>? cache;

    public async Task<IReadOnlyList<MapFeature>> GetChurchesAsync(
        bool forceRefresh = false,
        CancellationToken cancellationToken = default)
    {
        if (!forceRefresh && cache is not null)
        {
            return cache;
        }

        await cacheLock.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            if (!forceRefresh && cache is not null)
            {
                return cache;
            }

            using HttpResponseMessage response = await httpClient
                .GetAsync(Endpoint, HttpCompletionOption.ResponseHeadersRead, cancellationToken)
                .ConfigureAwait(false);
            response.EnsureSuccessStatusCode();

            MapDataResponse? payload = await response.Content
                .ReadFromJsonAsync(MapJsonContext.Default.MapDataResponse, cancellationToken)
                .ConfigureAwait(false);

            cache = payload?.Features
                .Where(IsValidPoint)
                .ToArray() ?? [];

            return cache;
        }
        finally
        {
            cacheLock.Release();
        }
    }

    private static bool IsValidPoint(MapFeature feature) =>
        feature.Geometry.Coordinates is [var longitude, var latitude, ..] &&
        longitude is >= -180 and <= 180 &&
        latitude is >= -90 and <= 90;
}
