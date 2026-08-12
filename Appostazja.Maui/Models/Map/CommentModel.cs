namespace Appostazja.Maui.Models.Map;

public class CommentModel
{
    [JsonPropertyName("action")]
    public string? Action { get; set; }

    [JsonPropertyName("age")]
    public object? Age { get; set; }

    [JsonPropertyName("comment")]
    public string? Comment { get; set; }

    [JsonPropertyName("date")]
    public string? Date { get; set; }

    [JsonPropertyName("education")]
    public string? Education { get; set; }

    [JsonPropertyName("rate_level")]
    public string? RateLevel { get; set; }

    [JsonPropertyName("sex")]
    public string? Sex { get; set; }
}