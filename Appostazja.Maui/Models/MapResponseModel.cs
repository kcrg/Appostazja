using Appostazja.Maui.Models.Map;

namespace Appostazja.Maui.Models;

public class MapResponseModel
{
    [JsonPropertyName("features")]
    public IEnumerable<FeatureModel> Features { get; set; } = null!;

    [JsonPropertyName("type")]
    public string? Type { get; set; }
}

[JsonSerializable(typeof(MapResponseModel))]
[JsonSourceGenerationOptions(GenerationMode = JsonSourceGenerationMode.Default)]
public partial class SourceGeneratorJsonContext : JsonSerializerContext
{
}