using System.IO.Compression;
using System.Text.Json;
using ApostasyMap.Api.Contracts;
using ApostasyMap.Api.Json;
using ApostasyMap.Api.Services;

namespace ApostasyMap.Api.Data;

public sealed class MapMemoryCache
{
    private Snapshot _snapshot = Snapshot.Empty;

    public DatasetStateRow GetState() => Volatile.Read(ref _snapshot).State;

    public (DatasetStateRow State, StatsDto Stats) GetStats()
    {
        var snapshot = Volatile.Read(ref _snapshot);
        return (snapshot.State, snapshot.Stats);
    }

    public (DatasetStateRow State, PlaceSearchResultDto Result) Search(PlaceSearchQuery query)
    {
        var snapshot = Volatile.Read(ref _snapshot);
        return (snapshot.State, snapshot.Search.Search(query));
    }

    public (DatasetStateRow State, byte[] Json) GetFullDataset(bool gzip)
    {
        var snapshot = Volatile.Read(ref _snapshot);
        return (snapshot.State, gzip ? snapshot.FullDatasetGzip : snapshot.FullDatasetJson);
    }

    public (DatasetStateRow State, PlaceDto[] Places) GetPlaces(BoundingBox bounds)
    {
        var snapshot = Volatile.Read(ref _snapshot);
        var result = new List<PlaceDto>();
        foreach (var place in snapshot.Places)
        {
            if (place.Latitude >= bounds.MinLatitude &&
                place.Latitude <= bounds.MaxLatitude &&
                place.Longitude >= bounds.MinLongitude &&
                place.Longitude <= bounds.MaxLongitude)
            {
                result.Add(place);
            }
        }

        return (snapshot.State, [.. result]);
    }

    public (DatasetStateRow State, PlaceDto? Place, PlaceReportsDto? Reports) GetPlace(string id)
    {
        var snapshot = Volatile.Read(ref _snapshot);
        if (!snapshot.ById.TryGetValue(id, out var place))
        {
            return (snapshot.State, null, null);
        }

        return (snapshot.State, place, snapshot.Reports.GetValueOrDefault(place.Id));
    }

    public void Replace(DatasetStateRow state, PlaceDto[] places, IReadOnlyList<PlaceReportsRow> reports)
    {
        var detailsById = new Dictionary<string, PlaceReportsDto>(reports.Count, StringComparer.Ordinal);
        foreach (var entry in reports)
        {
            detailsById[entry.PlaceId] = entry.Details;
        }

        places = Array.ConvertAll(places,
            place => PlaceSummary.WithReportSummary(place, detailsById.GetValueOrDefault(place.Id)));

        var byId = new Dictionary<string, PlaceDto>(places.Length, StringComparer.Ordinal);
        foreach (var place in places)
        {
            byId[place.Id] = place;
        }

        var reportsById = new Dictionary<string, PlaceReportsDto>(reports.Count, StringComparer.Ordinal);
        foreach (var entry in reports)
        {
            reportsById.Add(entry.PlaceId, entry.Details);
            foreach (var alias in entry.Aliases)
            {
                byId.Add(alias, byId[entry.PlaceId]);
            }
        }

        // The full-dataset endpoint is by far the hottest serialization path. Build its
        // UTF-8 payload once per dataset change and reuse it for every unfiltered request.
        var fullDatasetJson = JsonSerializer.SerializeToUtf8Bytes(
            places,
            AppJsonSerializerContext.Default.PlaceDtoArray);

        using var compressed = new MemoryStream();
        using (var gzip = new GZipStream(compressed, CompressionLevel.Optimal, leaveOpen: true))
        {
            gzip.Write(fullDatasetJson);
        }

        Volatile.Write(ref _snapshot, new Snapshot(state, places, byId, reportsById,
            fullDatasetJson, compressed.ToArray(), new PlaceSearch(places), DatasetStats.Compute(places, reportsById)));
    }

    public void Replace(DatasetStateRow state, IReadOnlyList<PlaceRow> places, IReadOnlyList<PlaceReportsRow> reports)
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

        Replace(state, dtos, reports);
    }

    public void UpdateState(DatasetStateRow state)
    {
        var snapshot = Volatile.Read(ref _snapshot);
        Volatile.Write(ref _snapshot, snapshot with { State = state });
    }

    private sealed record Snapshot(
        DatasetStateRow State,
        PlaceDto[] Places,
        Dictionary<string, PlaceDto> ById,
        Dictionary<string, PlaceReportsDto> Reports,
        byte[] FullDatasetJson,
        byte[] FullDatasetGzip,
        PlaceSearch Search,
        StatsDto Stats)
    {
        public static readonly Snapshot Empty = new(
            new DatasetStateRow(),
            [],
            new Dictionary<string, PlaceDto>(StringComparer.Ordinal),
            new Dictionary<string, PlaceReportsDto>(StringComparer.Ordinal),
            "[]"u8.ToArray(),
            [],
            PlaceSearch.Empty,
            DatasetStats.Compute([], new Dictionary<string, PlaceReportsDto>()));
    }
}
