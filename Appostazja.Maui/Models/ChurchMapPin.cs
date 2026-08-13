namespace Appostazja.Maui.Models;

public sealed record ChurchMapPin(
    string Id,
    string Label,
    string Address,
    double AverageRating,
    Location Location)
{
    public double DisplayRating =>
        Math.Clamp((AverageRating + 1) * 2.5, 0, 5);

    public string RatingText => $"Ocena {DisplayRating:0.0}/5";

    public string PinImageSource => AverageRating switch
    {
        < -0.2 => "pin_bad.png",
        <= 0.2 => "pin_average.png",
        _ => "pin_good.png",
    };
}