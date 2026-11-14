using ApostasyMap.Api.Contracts;
using ApostasyMap.Api.Services;

namespace Appostazja.Api.Tests;

public class DatasetStatsTests
{
    private static PlaceDto Place(string id, string address, string tone, int easy, int medium, int hard) =>
        new(id, id, address, 50, 20, 0, new RatingBreakdownDto(easy, medium, hard, easy + medium + hard), Tone: tone);

    private static ReportDto Report(string stage, string rating, string? date = null, string? comment = null) =>
        new(stage, rating, date, comment);

    [Fact]
    public void Totals_stages_tones_and_cities_add_up_over_all_reports()
    {
        var places = new[]
        {
            Place("a", "Lubelska 16, 35-234 Rzeszów", "positive", 2, 0, 0),
            Place("b", "Plac Świętego Józefa 2, 35-234 RZESZÓW", "negative", 0, 0, 1),
            Place("c", "Floriańska 1, 31-019 Kraków", "mixed", 0, 1, 0),
        };
        var reports = new Dictionary<string, PlaceReportsDto>
        {
            ["a"] = new(null, [Report("apostasy", "easy", "2021-03-01", "ok"), Report("before_apostasy", "easy")]),
            ["b"] = new(null, [Report("after_apostasy", "hard", "2022-01-05")]),
            ["c"] = new(null, [Report("apostasy", "medium", comment: "długo")]),
        };

        var stats = DatasetStats.Compute(places, reports);

        Assert.Equal((3, 4, 2, 2), (stats.PlaceCount, stats.ReportCount, stats.CommentCount, stats.DatedReportCount));
        Assert.Equal(new RatingBreakdownDto(2, 1, 1, 4), stats.Ratings);
        Assert.Equal(new RatingBreakdownDto(1, 0, 0, 1), stats.Stages.BeforeApostasy);
        Assert.Equal(new RatingBreakdownDto(1, 1, 0, 2), stats.Stages.Apostasy);
        Assert.Equal(new RatingBreakdownDto(0, 0, 1, 1), stats.Stages.AfterApostasy);
        Assert.Equal(new ToneCountsDto(1, 1, 1, 0), stats.PlaceTones);

        // Rzeszów in two spellings is one city and comes first with three reports.
        var rzeszow = stats.TopCities[0];
        Assert.Equal(("Rzeszów", 2, 3), (rzeszow.City, rzeszow.PlaceCount, rzeszow.Ratings.Total));
        Assert.Equal(PlaceSummary.RatingOf(new RatingBreakdownDto(2, 0, 1, 3)), rzeszow.Rating);
        Assert.Equal("Kraków", stats.TopCities[1].City);
    }

    [Fact]
    public void Years_keep_empty_years_on_the_axis_and_fold_sparse_early_years_into_one_period()
    {
        var dates = new[] { "1999-05-01", "2005-01-01", "2018-02-02", "2021-01-01", "2021-06-01", "2026-03-03" };
        var places = new[] { Place("a", "Długa 1, 00-001 Warszawa", "positive", dates.Length, 0, 0) };
        var reports = new Dictionary<string, PlaceReportsDto>
        {
            ["a"] = new(null, dates.Select(date => Report("apostasy", "easy", date)).ToArray()),
        };

        var years = DatasetStats.Compute(places, reports).Years;

        // Nine separate years end at 2026, so they start at 2018; 1999 and 2005 form one leading period.
        Assert.Equal((1999, 2017, 2), (years[0].FromYear, years[0].ToYear, years[0].Ratings.Total));
        Assert.Equal(Enumerable.Range(2018, DatasetStats.YearBars), years.Skip(1).Select(period => period.FromYear));
        Assert.Equal(0, years.Single(period => period.FromYear == 2019).Ratings.Total);
        Assert.Equal(2, years.Single(period => period.FromYear == 2021).Ratings.Total);
    }

    [Theory]
    [InlineData("Lubelska 16, 35-234 Rzeszów", "Rzeszów")]
    [InlineData("Generała Stefana \"Grota\" Roweckiego 8, 27-500 Opatów", "Opatów")]
    [InlineData("Södermannagatan 52, 116 65 Stockholm", "Stockholm")]
    [InlineData("Kościelna, 74-100 Gryfino", "Gryfino")]
    [InlineData("Rynek 1", null)]
    public void City_is_the_last_address_part_without_the_postal_code(string address, string? city)
    {
        Assert.Equal(city, DatasetStats.CityOf(address));
    }
}
