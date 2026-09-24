using System.Text.Json;
using Appostazja.Core.Models;

namespace Appostazja.Core.Services;

public sealed class MapDataService(
    HttpClient httpClient,
    IMapDataCache? persistentCache = null,
    TimeProvider? timeProvider = null) : IMapDataService
{
    public static readonly Uri Endpoint = new("https://mapaapostazji.pl/api/map-data.php");
    public static readonly TimeSpan CacheLifetime = TimeSpan.FromDays(1);

    private readonly SemaphoreSlim cacheLock = new(1, 1);
    private readonly HttpClient httpClient = httpClient;
    private readonly IMapDataCache? persistentCache = persistentCache;
    private readonly TimeProvider timeProvider = timeProvider ?? TimeProvider.System;
    private MapDataCacheEntry? memoryCache;

    private MapDataSnapshot? memorySnapshot;

    public async Task<MapDataSnapshot> GetChurchesAsync(
        bool forceRefresh = false,
        CancellationToken cancellationToken = default)
    {
        if (!forceRefresh && TryGetFreshMemorySnapshot(out var cachedMemorySnapshot))
        {
            return cachedMemorySnapshot;
        }

        await cacheLock.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            if (!forceRefresh && TryGetFreshMemorySnapshot(out cachedMemorySnapshot))
            {
                return cachedMemorySnapshot;
            }

            MapDataCacheEntry? cachedEntry = memoryCache;
            if (cachedEntry is null && persistentCache is not null)
            {
                cachedEntry = await persistentCache
                    .ReadAsync(cancellationToken)
                    .ConfigureAwait(false);
                memoryCache = cachedEntry;
                memorySnapshot = null;
            }

            if (!forceRefresh && TryCreateFreshSnapshot(cachedEntry, true, out var diskSnapshot))
            {
                memorySnapshot = diskSnapshot;
                return diskSnapshot;
            }

            try
            {
                using HttpResponseMessage response = await httpClient
                    .GetAsync(Endpoint, HttpCompletionOption.ResponseHeadersRead, cancellationToken)
                    .ConfigureAwait(false);
                response.EnsureSuccessStatusCode();

                string geoJson = await response.Content
                    .ReadAsStringAsync(cancellationToken)
                    .ConfigureAwait(false);

                var freshEntry = new MapDataCacheEntry(
                    geoJson,
                    timeProvider.GetUtcNow());

                MapDataSnapshot freshSnapshot;
                if (memorySnapshot is not null &&
                    cachedEntry is not null &&
                    string.Equals(cachedEntry.GeoJson, geoJson, StringComparison.Ordinal))
                {
                    freshSnapshot = memorySnapshot with
                    {
                        RetrievedAt = freshEntry.RetrievedAt,
                        IsFromCache = false,
                        IsStale = false,
                    };
                }
                else if (!TryCreateSnapshot(freshEntry, false, false, out freshSnapshot))
                {
                    throw new JsonException("The map response did not contain valid GeoJSON data.");
                }

                memorySnapshot = freshSnapshot;
                memoryCache = freshEntry;
                if (persistentCache is not null)
                {
                    await persistentCache
                        .WriteAsync(freshEntry, cancellationToken)
                        .ConfigureAwait(false);
                }

                return freshSnapshot;
            }
            catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
            {
                throw;
            }
            catch (Exception) when (cachedEntry is not null)
            {
                if (TryCreateSnapshot(cachedEntry, true, true, out var staleSnapshot))
                {
                    memorySnapshot = staleSnapshot;
                    return staleSnapshot;
                }

                throw;
            }
        }
        finally
        {
            cacheLock.Release();
        }
    }

    private bool TryGetFreshMemorySnapshot(out MapDataSnapshot snapshot)
    {
        if (memoryCache is not null &&
            memorySnapshot is not null &&
            timeProvider.GetUtcNow() - memoryCache.RetrievedAt < CacheLifetime)
        {
            snapshot = memorySnapshot;
            return true;
        }

        snapshot = default!;
        return false;
    }

    private bool TryCreateFreshSnapshot(
        MapDataCacheEntry? entry,
        bool isFromCache,
        out MapDataSnapshot snapshot)
    {
        if (entry is not null &&
            timeProvider.GetUtcNow() - entry.RetrievedAt < CacheLifetime)
        {
            return TryCreateSnapshot(entry, isFromCache, false, out snapshot);
        }

        snapshot = default!;
        return false;
    }

    private static bool TryCreateSnapshot(
        MapDataCacheEntry? entry,
        bool isFromCache,
        bool isStale,
        out MapDataSnapshot snapshot)
    {
        if (entry is null)
        {
            snapshot = default!;
            return false;
        }

        try
        {
            MapDataResponse? payload = JsonSerializer.Deserialize(
                entry.GeoJson,
                MapJsonContext.Default.MapDataResponse);

            List<MapFeature> features = payload?.Features ?? [];
            features.RemoveAll(static feature => !IsValidPoint(feature));

            snapshot = new MapDataSnapshot(
                features,
                entry.RetrievedAt,
                isFromCache,
                isStale,
                ComputeContentVersion(features));

            return true;
        }
        catch (JsonException)
        {
            snapshot = default!;
            return false;
        }
    }

    private static bool IsValidPoint(MapFeature feature) =>
        feature.Geometry.Coordinates is [var longitude, var latitude, ..] &&
        longitude is >= -180 and <= 180 &&
        latitude is >= -90 and <= 90;

    private static ulong ComputeContentVersion(List<MapFeature> features)
    {
        const ulong aggregateSeed = 0x9E3779B97F4A7C15UL;
        ulong sum = (ulong)features.Count * aggregateSeed;
        ulong xor = 0;

        foreach (MapFeature feature in features)
        {
            ulong featureHash = ComputeFeatureHash(feature);
            unchecked
            {
                sum += featureHash;
                xor ^= featureHash * aggregateSeed;
            }
        }

        return sum ^ xor;
    }

    private static ulong ComputeFeatureHash(MapFeature feature)
    {
        ulong hash = 14695981039346656037UL;
        AddString(ref hash, feature.Properties.Id);
        AddString(ref hash, feature.Properties.Name);
        AddString(ref hash, feature.Properties.Address);
        AddInt64(ref hash, BitConverter.DoubleToInt64Bits(feature.Properties.AverageRating));
        AddInt64(ref hash, BitConverter.DoubleToInt64Bits(feature.Geometry.Coordinates[0]));
        AddInt64(ref hash, BitConverter.DoubleToInt64Bits(feature.Geometry.Coordinates[1]));
        return hash;
    }

    private static void AddString(ref ulong hash, string value)
    {
        foreach (char character in value)
        {
            AddByte(ref hash, (byte)character);
            AddByte(ref hash, (byte)(character >> 8));
        }

        AddByte(ref hash, 0xFF);
    }

    private static void AddInt64(ref ulong hash, long value)
    {
        for (int shift = 0; shift < 64; shift += 8)
        {
            AddByte(ref hash, (byte)(value >> shift));
        }
    }

    private static void AddByte(ref ulong hash, byte value)
    {
        hash ^= value;
        hash *= 1099511628211UL;
    }
}
