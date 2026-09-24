using System.Text.Json.Serialization;

namespace ApostasyMap.Api.SourceModels;

public sealed class SourceFeatureCollection
{
    [JsonPropertyName("type")]
    public string? Type { get; init; }

    [JsonPropertyName("features")]
    public SourceFeature[]? Features { get; init; }
}

public sealed class SourceFeature
{
    [JsonPropertyName("type")]
    public string? Type { get; init; }

    [JsonPropertyName("geometry")]
    public SourceGeometry? Geometry { get; init; }

    [JsonPropertyName("properties")]
    public SourceProperties? Properties { get; init; }
}

public sealed class SourceGeometry
{
    [JsonPropertyName("type")]
    public string? Type { get; init; }

    [JsonPropertyName("coordinates")]
    public double[]? Coordinates { get; init; }
}

public sealed class SourceProperties
{
    [JsonPropertyName("id")]
    public string? Id { get; init; }

    [JsonPropertyName("nm")]
    public string? Name { get; init; }

    [JsonPropertyName("adr")]
    public string? Address { get; init; }

    [JsonPropertyName("avg")]
    public double Score { get; init; }

    [JsonPropertyName("cnt")]
    public int RatingsCount { get; init; }

    [JsonPropertyName("rts")]
    public int[]? Ratings { get; init; }
}
