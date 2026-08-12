namespace Appostazja.Maui.Models.Map;

public class FeatureModel
{
    [JsonPropertyName("geometry")]
    public GeometryModel? Geometry { get; set; }

    [JsonPropertyName("properties")]
    public PropertiesModel? Properties { get; set; }

    [JsonPropertyName("type")]
    public string? Type { get; set; }
}