using System.Text.Json.Serialization;

namespace Appostazja.Core.Models;

public sealed class MapDataResponse
{
    [JsonPropertyName("type")]
    public string Type { get; init; } = string.Empty;

    [JsonPropertyName("features")]
    public List<MapFeature> Features { get; init; } = [];
}

public sealed class MapFeature
{
    [JsonPropertyName("type")]
    public string Type { get; init; } = string.Empty;

    [JsonPropertyName("geometry")]
    public MapGeometry Geometry { get; init; } = new();

    [JsonPropertyName("properties")]
    public ChurchProperties Properties { get; init; } = new();
}

public sealed class MapGeometry
{
    [JsonPropertyName("type")]
    public string Type { get; init; } = string.Empty;

    // GeoJSON stores a point as [longitude, latitude].
    [JsonPropertyName("coordinates")]
    public double[] Coordinates { get; init; } = [];
}

public sealed class ChurchProperties
{
    [JsonPropertyName("id")]
    public string Id { get; init; } = string.Empty;

    [JsonPropertyName("church_name")]
    public string Name { get; init; } = string.Empty;

    [JsonPropertyName("church_address")]
    public string Address { get; init; } = string.Empty;

    [JsonPropertyName("average_rating")]
    public double AverageRating { get; init; }
}

[JsonSerializable(typeof(MapDataResponse))]
[JsonSourceGenerationOptions(
    PropertyNameCaseInsensitive = false,
    GenerationMode = JsonSourceGenerationMode.Metadata)]
internal sealed partial class MapJsonContext : JsonSerializerContext;
