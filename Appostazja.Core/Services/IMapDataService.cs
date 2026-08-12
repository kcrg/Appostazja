using Appostazja.Core.Models;

namespace Appostazja.Core.Services;

public interface IMapDataService
{
    Task<IReadOnlyList<MapFeature>> GetChurchesAsync(
        bool forceRefresh = false,
        CancellationToken cancellationToken = default);
}
