namespace Appostazja.Maui.Models.Map;

public class PropertiesModel
{
    [JsonPropertyName("average_rating")]
    public double AverageRating { get; set; }

    [JsonPropertyName("church_address")]
    public string? ChurchAddress { get; set; }

    [JsonPropertyName("church_name")]
    public string? ChurchName { get; set; }

    [JsonPropertyName("church_www")]
    public string? ChurchWww { get; set; }

    [JsonPropertyName("comments")]
    public IEnumerable<CommentModel>? Comments { get; set; }

    [JsonPropertyName("ratings")]
    public RatingsModel? Ratings { get; set; }
}
