using System.Text;
using ApostasyMap.Api.Services;
using static Appostazja.Api.Tests.SourceData;

namespace Appostazja.Api.Tests;

public class ResponsesCsvTests
{
    [Fact]
    public void Sanitized_csv_keeps_only_published_columns()
    {
        var raw = Csv(Row("Parafia", "Adres 1", submittedAt: "2024-01-01T08:15:00Z", age: "45-54", gender: "M"));

        var sanitized = Encoding.UTF8.GetString(ResponsesCsv.Sanitize(raw));

        Assert.DoesNotContain("submitted_at", sanitized);
        Assert.DoesNotContain("2024-01-01T08:15:00Z", sanitized);
        Assert.DoesNotContain("45-54", sanitized);
        Assert.DoesNotContain("gender", sanitized);
        Assert.Contains("Parafia", sanitized);
    }

    [Fact]
    public void Sanitizing_is_idempotent_so_cached_payloads_hash_the_same()
    {
        var once = ResponsesCsv.Sanitize(Csv(
            Row("Parafia \"Nowa\"", "Adres, 1", comment: "Linia 1\nLinia 2, \"cytat\" jan@example.com")));

        Assert.Equal(once, ResponsesCsv.Sanitize(once));
    }

    [Fact]
    public void Unreadable_rows_stay_as_empty_rows_so_the_parser_still_counts_them()
    {
        var sanitized = ResponsesCsv.Sanitize(Csv(Row("Parafia", "Adres 1"), "\"too\",\"few\""));
        var geo = GeoJson(("p1", "Parafia", "Adres 1", 50, 20, 1));

        var result = MapSourceParser.Parse(sanitized, geo);

        Assert.Equal(2, result.TotalReportRows);
        Assert.Equal(1, result.SkippedReportRows);
    }

    [Theory]
    [InlineData("Pisałam na jan.kowalski@parafia.pl w sprawie", "Pisałam na [e-mail ukryty] w sprawie")]
    [InlineData("Dzwoń: +48 601 234 567.", "Dzwoń: [numer ukryty].")]
    [InlineData("tel. 601-234-567", "tel. [numer ukryty]")]
    [InlineData("kancelaria 12 345 67 89", "kancelaria [numer ukryty]")]
    [InlineData("(tel.: +48 891 31 20 38, e-mail", "(tel.: [numer ukryty], e-mail")]
    [InlineData("PESEL 85010112345 podałem", "PESEL [numer ukryty] podałem")]
    public void Contact_details_are_masked(string comment, string expected) =>
        Assert.Equal(expected, ResponsesCsv.ScrubContactDetails(comment));

    [Theory]
    [InlineData("Byłem 2024-03-01 o 10:30, drugi raz w 2025.")]
    [InlineData("Wizyta 2024-03-01 10:30:15 trwała godzinę")]
    [InlineData("Czekałem 3 tygodnie, opłata 100 zł, lata 2019 2020 2021")]
    public void Dates_and_ordinary_numbers_are_left_alone(string comment) =>
        Assert.Equal(comment, ResponsesCsv.ScrubContactDetails(comment));
}
