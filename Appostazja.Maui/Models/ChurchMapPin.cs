namespace Appostazja.Maui.Models;

public sealed record ChurchMapPin(
    string Id,
    string Label,
    string Address,
    double AverageRating,
    Location Location)
{
    public ChurchRatingCategory RatingCategory => AverageRating switch
    {
        < -0.2 => ChurchRatingCategory.Bad,
        <= 0.2 => ChurchRatingCategory.Average,
        _ => ChurchRatingCategory.Good,
    };

    public double DisplayRating =>
        Math.Clamp((AverageRating + 1) * 2.5, 0, 5);

    public string RatingText => $"Ocena {DisplayRating:0.0}/5";

    public string PinImageSource => RatingCategory switch
    {
        ChurchRatingCategory.Bad => "pin_bad.png",
        ChurchRatingCategory.Average => "pin_average.png",
        _ => "pin_good.png",
    };
}

public enum ChurchRatingCategory
{
    Bad,
    Average,
    Good,
}
