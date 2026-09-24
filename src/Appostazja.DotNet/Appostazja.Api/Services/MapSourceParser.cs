using System.Text.Json;
using ApostasyMap.Api.Data;
using ApostasyMap.Api.Json;
using ApostasyMap.Api.SourceModels;

namespace ApostasyMap.Api.Services;

public static class MapSourceParser
{
    public static PlaceRow[] Parse(ReadOnlySpan<byte> payload)
    {
        var source = JsonSerializer.Deserialize(payload, AppJsonSerializerContext.Default.SourceFeatureCollection)
            ?? throw new InvalidDataException("Source JSON is empty.");

        if (!string.Equals(source.Type, "FeatureCollection", StringComparison.Ordinal))
        {
            throw new InvalidDataException("Source JSON is not a GeoJSON FeatureCollection.");
        }

        var features = source.Features
            ?? throw new InvalidDataException("Source JSON has no features array.");

        var ids = new HashSet<string>(StringComparer.Ordinal);
        var places = new PlaceRow[features.Length];

        for (var i = 0; i < features.Length; i++)
        {
            var feature = features[i];
            var geometry = feature.Geometry
                ?? throw Invalid(i, "missing geometry");
            var properties = feature.Properties
                ?? throw Invalid(i, "missing properties");

            if (!string.Equals(feature.Type, "Feature", StringComparison.Ordinal) ||
                !string.Equals(geometry.Type, "Point", StringComparison.Ordinal))
            {
                throw Invalid(i, "only Point features are supported");
            }

            var coordinates = geometry.Coordinates;
            if (coordinates is null || coordinates.Length < 2)
            {
                throw Invalid(i, "missing coordinates");
            }

            var longitude = coordinates[0];
            var latitude = coordinates[1];
            if (!double.IsFinite(latitude) || latitude is < -90 or > 90 ||
                !double.IsFinite(longitude) || longitude is < -180 or > 180)
            {
                throw Invalid(i, "invalid coordinates");
            }

            var id = Require(properties.Id, i, "id");
            if (!ids.Add(id))
            {
                throw Invalid(i, $"duplicate id '{id}'");
            }

            if (!double.IsFinite(properties.Score) || properties.Score is < -1 or > 1)
            {
                throw Invalid(i, "avg must be a finite value between -1 and 1");
            }

            var ratings = properties.Ratings;
            if (ratings is null || ratings.Length < 3 || ratings[0] < 0 || ratings[1] < 0 || ratings[2] < 0)
            {
                throw Invalid(i, "rts must contain three non-negative counters");
            }

            if (properties.RatingsCount < 0)
            {
                throw Invalid(i, "cnt must be non-negative");
            }

            places[i] = new PlaceRow
            {
                Id = id,
                Name = Require(properties.Name, i, "nm"),
                Address = Require(properties.Address, i, "adr"),
                Latitude = latitude,
                Longitude = longitude,
                Score = properties.Score,
                RatingsCount = properties.RatingsCount,
                PositiveRatings = ratings[0],
                NeutralRatings = ratings[1],
                NegativeRatings = ratings[2]
            };
        }

        return places;
    }

    private static string Require(string? value, int index, string field)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            throw Invalid(index, $"missing {field}");
        }

        return value.Trim();
    }

    private static InvalidDataException Invalid(int index, string message) =>
        new($"Invalid feature at index {index}: {message}.");
}
