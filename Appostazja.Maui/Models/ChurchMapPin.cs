namespace Appostazja.Maui.Models;

public sealed record ChurchMapPin(
    string Id,
    string Label,
    string Address,
    double AverageRating,
    Location Location)
{
    public string RatingText =>
        AverageRating > 0
            ? $"Ocena {AverageRating:0.0}/5"
            : "Brak ocen";
}