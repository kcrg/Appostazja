using System.Text.Json;
using ApostasyMap.Api.Contracts;
using ApostasyMap.Api.Json;

namespace ApostasyMap.Api.Data;

public sealed class MapMemoryCache
{
    private Snapshot _snapshot = Snapshot.Empty;

    public DatasetStateRow GetState() => Volatile.Read(ref _snapshot).State;

    public (DatasetStateRow State, byte[] Json) GetFullDataset()
    {
        var snapshot = Volatile.Read(ref _snapshot);
        return (snapshot.State, snapshot.FullDatasetJson);
    }

    public PlaceDto[] GetPlaces(BoundingBox? bounds)
    {
        var snapshot = Volatile.Read(ref _snapshot);
        if (bounds is not { } box)
        {
            return snapshot.Places;
        }

        var result = new List<PlaceDto>();
        foreach (var place in snapshot.Places)
        {
            if (place.Latitude >= box.MinLatitude &&
                place.Latitude <= box.MaxLatitude &&
                place.Longitude >= box.MinLongitude &&
                place.Longitude <= box.MaxLongitude)
            {
                result.Add(place);
            }
        }

        return [.. result];
    }

    public PlaceDto? GetPlace(string id)
    {
        var snapshot = Volatile.Read(ref _snapshot);
        return snapshot.ById.TryGetValue(id, out var place) ? place : null;
    }

    public void Replace(DatasetStateRow state, PlaceDto[] places)
    {
        var byId = new Dictionary<string, PlaceDto>(places.Length, StringComparer.Ordinal);
        foreach (var place in places)
        {
            byId[place.Id] = place;
        }

        // The full-dataset endpoint is by far the hottest serialization path. Build its
        // UTF-8 payload once per dataset change and reuse it for every unfiltered request.
        var fullDatasetJson = JsonSerializer.SerializeToUtf8Bytes(
            places,
            AppJsonSerializerContext.Default.PlaceDtoArray);

        Volatile.Write(ref _snapshot, new Snapshot(state, places, byId, fullDatasetJson));
    }

    public void Replace(DatasetStateRow state, IReadOnlyList<PlaceRow> places)
    {
        var dtos = new PlaceDto[places.Count];
        for (var i = 0; i < places.Count; i++)
        {
            var place = places[i];
            dtos[i] = new PlaceDto(
                place.Id,
                place.Name,
                place.Address,
                place.Latitude,
                place.Longitude,
                place.Score,
                new RatingBreakdownDto(
                    place.PositiveRatings,
                    place.NeutralRatings,
                    place.NegativeRatings,
                    place.RatingsCount));
        }

        Replace(state, dtos);
    }

    public void UpdateState(DatasetStateRow state)
    {
        var snapshot = Volatile.Read(ref _snapshot);
        Volatile.Write(
            ref _snapshot,
            new Snapshot(state, snapshot.Places, snapshot.ById, snapshot.FullDatasetJson));
    }

    private sealed record Snapshot(
        DatasetStateRow State,
        PlaceDto[] Places,
        Dictionary<string, PlaceDto> ById,
        byte[] FullDatasetJson)
    {
        public static readonly Snapshot Empty = new(
            new DatasetStateRow(),
            [],
            new Dictionary<string, PlaceDto>(StringComparer.Ordinal),
            "[]"u8.ToArray());
    }
}
