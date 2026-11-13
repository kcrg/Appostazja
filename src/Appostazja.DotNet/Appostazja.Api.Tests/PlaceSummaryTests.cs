using ApostasyMap.Api.Contracts;
using ApostasyMap.Api.Data;
using ApostasyMap.Api.Services;
using static Appostazja.Api.Tests.SourceData;

namespace Appostazja.Api.Tests;

public class PlaceSummaryTests
{
    private static readonly PlaceDto Place = new("p1", "Parafia", "Adres 1", 50, 20, 0, new(0, 0, 0, 0));

    [Fact]
    public void Splits_ratings_by_stage_and_picks_the_latest_action_date()
    {
        var details = new PlaceReportsDto(null,
        [
            new("before_apostasy", "easy", "2021-05-10", null),
            new("before_apostasy", "hard", null, null),
            new("apostasy", "medium", "2023-01-02", null),
            new("apostasy", "easy", "2022-12-31", null),
            new("after_apostasy", "hard", "2019-07-01", null),
        ]);

        var place = PlaceSummary.WithReportSummary(Place, details);

        Assert.Equal(new RatingBreakdownDto(1, 0, 1, 2), place.Stages!.BeforeApostasy);
        Assert.Equal(new RatingBreakdownDto(1, 1, 0, 2), place.Stages.Apostasy);
        Assert.Equal(new RatingBreakdownDto(0, 0, 1, 1), place.Stages.AfterApostasy);
        Assert.Equal("2023-01-02", place.LastActionDate);
    }

    [Fact]
    public void Place_without_reports_or_dates_has_empty_stages_and_no_date()
    {
        var place = PlaceSummary.WithReportSummary(Place, null);
        var undated = PlaceSummary.WithReportSummary(Place,
            new PlaceReportsDto(null, [new("apostasy", "easy", null, null)]));

        Assert.Equal(0, place.Stages!.BeforeApostasy.Total + place.Stages.Apostasy.Total + place.Stages.AfterApostasy.Total);
        Assert.Null(place.LastActionDate);
        Assert.Null(undated.LastActionDate);
    }

    [Fact]
    public void Imported_snapshot_serves_stage_totals_that_match_overall_ratings_also_via_alias()
    {
        var csv = Csv(
            Row("Parafia", "Adres 1", stage: "before_apostasy", rating: "easy", date: "2020-01-01"),
            Row("Parafia", "Adres 1", stage: "apostasy", rating: "hard", date: "2024-06-30"),
            Row("Parafia", "Adres 1", stage: "after_apostasy", rating: "medium", date: "not-a-date"));
        var geo = GeoJson(
            ("main", "Parafia", "Adres 1", 50, 20, 3),
            ("old", "Parafia", "Adres 1", 50, 20, 0));
        var parsed = MapSourceParser.Parse(csv, geo);
        var cache = new MapMemoryCache();

        cache.Replace(new DatasetStateRow { PlaceCount = 1 }, parsed.Places, parsed.Reports);

        var place = cache.GetPlace("old").Place!;
        Assert.Equal("main", place.Id);
        Assert.Equal(place.Ratings.Total,
            place.Stages!.BeforeApostasy.Total + place.Stages.Apostasy.Total + place.Stages.AfterApostasy.Total);
        Assert.Equal(1, place.Stages.Apostasy.Negative);
        Assert.Equal("2024-06-30", place.LastActionDate);
    }
}
