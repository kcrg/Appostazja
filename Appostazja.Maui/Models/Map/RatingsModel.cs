namespace Appostazja.Maui.Models.Map;

public class RatingsModel
{
    [JsonPropertyName("after_easy")]
    public int AfterEasy { get; set; }

    [JsonPropertyName("after_hard")]
    public int AfterHard { get; set; }

    [JsonPropertyName("after_medium")]
    public int AfterMedium { get; set; }

    [JsonPropertyName("apostasy_easy")]
    public int ApostasyEasy { get; set; }

    [JsonPropertyName("apostasy_hard")]
    public int ApostasyHard { get; set; }

    [JsonPropertyName("apostasy_medium")]
    public int ApostasyMedium { get; set; }

    [JsonPropertyName("before_easy")]
    public int BeforeEasy { get; set; }

    [JsonPropertyName("before_hard")]
    public int BeforeHard { get; set; }

    [JsonPropertyName("before_medium")]
    public int BeforeMedium { get; set; }
}