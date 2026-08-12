namespace Appostazja.Maui.Models.Map;

public class GeometryModel
{
    [JsonPropertyName("coordinates")]
    public IEnumerable<double?>? Coordinates { get; set; }

    [JsonPropertyName("type")]
    public string? Type { get; set; }
}