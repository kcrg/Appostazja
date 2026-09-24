using Appostazja.Core.Services;
using Microsoft.Extensions.Logging;

namespace Appostazja.Maui.Services.Implementations;

public sealed class FileMapDataCache(
    IFileSystem fileSystem,
    ILogger<FileMapDataCache> logger) : IMapDataCache
{
    private const string CacheFileName = "map-data.geojson.cache";

    private string CachePath =>
        Path.Combine(fileSystem.AppDataDirectory, CacheFileName);

    public async Task<MapDataCacheEntry?> ReadAsync(
        CancellationToken cancellationToken = default)
    {
        try
        {
            if (!File.Exists(CachePath))
            {
                return null;
            }

            await using var stream = File.OpenRead(CachePath);
            using var reader = new StreamReader(stream);
            string? timestampText = await reader.ReadLineAsync(cancellationToken);
            string geoJson = await reader.ReadToEndAsync(cancellationToken);

            return DateTimeOffset.TryParseExact(
                    timestampText,
                    "O",
                    System.Globalization.CultureInfo.InvariantCulture,
                    System.Globalization.DateTimeStyles.RoundtripKind,
                    out DateTimeOffset retrievedAt) &&
                !string.IsNullOrWhiteSpace(geoJson)
                    ? new MapDataCacheEntry(geoJson, retrievedAt)
                    : null;
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            throw;
        }
        catch (Exception exception) when (
            exception is IOException or UnauthorizedAccessException)
        {
            logger.LogWarning(exception, "Reading the map cache failed.");
            return null;
        }
    }

    public async Task WriteAsync(
        MapDataCacheEntry entry,
        CancellationToken cancellationToken = default)
    {
        string temporaryPath = $"{CachePath}.tmp";

        try
        {
            await using (var stream = File.Create(temporaryPath))
            await using (var writer = new StreamWriter(stream))
            {
                await writer.WriteLineAsync(
                    entry.RetrievedAt.ToString(
                        "O",
                        System.Globalization.CultureInfo.InvariantCulture)
                        .AsMemory(),
                    cancellationToken);
                await writer.WriteAsync(entry.GeoJson.AsMemory(), cancellationToken);
            }

            File.Move(temporaryPath, CachePath, true);
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            TryDelete(temporaryPath);
            throw;
        }
        catch (Exception exception) when (
            exception is IOException or UnauthorizedAccessException)
        {
            TryDelete(temporaryPath);
            logger.LogWarning(exception, "Writing the map cache failed.");
        }
    }

    private static void TryDelete(string path)
    {
        try
        {
            File.Delete(path);
        }
        catch (IOException)
        {
        }
        catch (UnauthorizedAccessException)
        {
        }
    }
}
