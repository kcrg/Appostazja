using ApostasyMap.Api.Services;
using static Appostazja.Api.Tests.SourceData;

namespace Appostazja.Api.Tests;

public class MapSourceParserTests
{
    [Fact]
    public void Joins_reports_to_pins_ignoring_case_and_whitespace_and_computes_score()
    {
        var csv = Csv(
            Row("Kościół  św. Józefa", "ul. Długa 1,  Rzeszów", rating: "easy"),
            Row("kościół św. józefa", "UL. DŁUGA 1, RZESZÓW", rating: "hard"),
            Row("Kościół św. Józefa", "ul. Długa 1, Rzeszów", rating: "easy"),
            Row("Kościół św. Józefa", "ul. Długa 1, Rzeszów", rating: "medium"));
        var geo = GeoJson(("jozef", "Kościół św. Józefa", "ul. Długa 1, Rzeszów", 50.05, 22.0, 4));

        var result = MapSourceParser.Parse(csv, geo);

        var place = Assert.Single(result.Places);
        Assert.Equal(4, place.RatingsCount);
        Assert.Equal((2, 1, 1), (place.PositiveRatings, place.NeutralRatings, place.NegativeRatings));
        Assert.Equal((2 - 1) / 4.0, place.Score);
        Assert.Equal(4, Assert.Single(result.Reports).Details.Reports.Length);
        Assert.Equal(0, result.UnmatchedReportCount);
    }

    [Fact]
    public void Reports_are_returned_newest_submission_first()
    {
        var csv = Csv(
            Row("Parafia", "Adres 1", comment: "najstarsza"),
            Row("Parafia", "Adres 1", comment: "środkowa"),
            Row("Parafia", "Adres 1", comment: "najnowsza"));
        var geo = GeoJson(("p1", "Parafia", "Adres 1", 50, 20, 3));

        var reports = Assert.Single(MapSourceParser.Parse(csv, geo).Reports).Details.Reports;

        Assert.Equal(["najnowsza", "środkowa", "najstarsza"], reports.Select(report => report.Comment));
    }

    [Fact]
    public void Duplicate_pins_at_one_address_keep_the_most_reported_pin_and_alias_the_rest()
    {
        var csv = Csv(Row("Parafia", "Adres 1"));
        var geo = GeoJson(
            ("old-pin", "Parafia", "Adres 1", 50, 20, 1),
            ("main-pin", "Parafia", "Adres 1", 50.0001, 20, 7));

        var result = MapSourceParser.Parse(csv, geo);

        Assert.Equal("main-pin", Assert.Single(result.Places).Id);
        Assert.Equal(["old-pin"], Assert.Single(result.Reports).Aliases);
    }

    [Fact]
    public void Invalid_rows_and_features_are_skipped_and_counted_instead_of_failing_the_import()
    {
        var csv = Csv(
            Row("Parafia", "Adres 1"),
            Row("Parafia", "Adres 1", rating: "very_hard"),
            Row("Parafia", "Adres 1", stage: "unknown_stage"),
            Row("", "Adres 1"),
            "\"too\",\"few\",\"fields\"");
        var geo = GeoJson(
            ("p1", "Parafia", "Adres 1", 50, 20, 1),
            ("p1", "Duplikat", "Adres 2", 50, 20, 1),
            ("p3", "Poza mapą", "Adres 3", 95, 20, 1));

        var result = MapSourceParser.Parse(csv, geo);

        Assert.Equal(1, Assert.Single(result.Places).RatingsCount);
        Assert.Equal(5, result.TotalReportRows);
        Assert.Equal(4, result.SkippedReportRows);
        Assert.Equal(3, result.TotalFeatures);
        Assert.Equal(2, result.SkippedFeatures);
    }

    [Fact]
    public void Reports_without_a_pin_are_counted_as_unmatched()
    {
        var csv = Csv(Row("Parafia", "Adres 1"), Row("Inna parafia", "Adres 9"), Row("Inna parafia", "Adres 9"));
        var geo = GeoJson(("p1", "Parafia", "Adres 1", 50, 20, 1));

        Assert.Equal(2, MapSourceParser.Parse(csv, geo).UnmatchedReportCount);
    }

    [Fact]
    public void Missing_required_column_is_a_structural_error()
    {
        var csv = "church_name,church_address\n\"A\",\"B\"\n"u8.ToArray();
        var geo = GeoJson(("p1", "A", "B", 50, 20, 1));

        Assert.Throws<InvalidDataException>(() => MapSourceParser.Parse(csv, geo));
    }

    [Fact]
    public void Bare_domain_websites_become_https_and_unsafe_urls_are_dropped()
    {
        var geo = GeoJson(("p1", "A", "B", 50, 20, 1), ("p2", "C", "D", 50, 20, 1));
        var csv = Csv(
            Row("A", "B", website: "parafia.example.pl/kontakt"),
            Row("C", "D", website: "javascript:alert(1)"));

        var reports = MapSourceParser.Parse(csv, geo).Reports.ToDictionary(r => r.PlaceId);

        Assert.Equal("https://parafia.example.pl/kontakt", reports["p1"].Details.Website);
        Assert.Null(reports["p2"].Details.Website);
    }
}
