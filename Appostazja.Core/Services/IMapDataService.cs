using Appostazja.Core.Models;

namespace Appostazja.Core.Services;

public interface IMapDataService
{
    Task<MapDataSnapshot> GetChurchesAsync(
        bool forceRefresh = false,
        CancellationToken cancellationToken = default);
}

public sealed record MapDataSnapshot(
    IReadOnlyList<MapFeature> Features,
    DateTimeOffset RetrievedAt,
    bool IsFromCache,
    bool IsStale,
    ulong ContentVersion);

public sealed record MapDataCacheEntry(
    string GeoJson,
    DateTimeOffset RetrievedAt);

public interface IMapDataCache
{
    Task<MapDataCacheEntry?> ReadAsync(CancellationToken cancellationToken = default);

    Task WriteAsync(
        MapDataCacheEntry entry,
        CancellationToken cancellationToken = default);
}
