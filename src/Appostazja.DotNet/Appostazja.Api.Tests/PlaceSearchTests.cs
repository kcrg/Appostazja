using ApostasyMap.Api.Contracts;
using ApostasyMap.Api.Endpoints;
using ApostasyMap.Api.Services;

namespace Appostazja.Api.Tests;

public class PlaceSearchTests
{
    private static PlaceDto Place(string id, string name, string address, int easy = 0, int medium = 0, int hard = 0)
    {
        var total = easy + medium + hard;
        return new PlaceDto(id, name, address, 50, 20, total == 0 ? 0 : (double)(easy - hard) / total,
            new RatingBreakdownDto(easy, medium, hard, total));
    }

    private static readonly PlaceSearch Search = new([
        Place("jozef-rze", "Kościół pw. Świętego Józefa", "Lubelska 16, 35-234 Rzeszów", easy: 2),
        Place("jozef-krk", "Parafia św. Józefa", "Zamoyskiego 2, 30-523 Kraków", easy: 1, hard: 3),
        Place("lukasz", "Parafia Świętego Łukasza", "Długa 1, 00-001 Łódź", easy: 5, hard: 5),
        Place("anna", "Kościół św. Anny", "Krakowskie Przedmieście 68, Warszawa"),
        Place("maria", "Bazylika Mariacka", "Plac Mariacki 5, 31-042 Kraków", easy: 1),
    ]);

    private static string[] Ids(PlaceSearchResultDto result) => result.Items.Select(place => place.Id).ToArray();

    [Theory]
    [InlineData("jozefa", new[] { "jozef-rze", "jozef-krk" })]
    [InlineData("ŁUKASZA", new[] { "lukasz" })]
    [InlineData("lodz", new[] { "lukasz" })]
    [InlineData("józefa  rzeszów", new[] { "jozef-rze" })]
    [InlineData("józefa gdańsk", new string[0])]
    public void Matches_name_and_address_ignoring_case_diacritics_and_requiring_every_word(string query, string[] expected)
    {
        var result = Search.Search(new PlaceSearchQuery(query, PlaceSort.Name));

        Assert.Equal(expected, Ids(result));
        Assert.Equal(expected.Length, result.Total);
    }

    [Fact]
    public void Relevance_puts_name_matches_before_address_matches()
    {
        // "krakow" is in Anna's street name and in two Kraków addresses, but in no parish name.
        var byAddress = Search.Search(new PlaceSearchQuery("krakow"));
        var byName = Search.Search(new PlaceSearchQuery("parafia"));

        Assert.Equal(["jozef-krk", "maria", "anna"], Ids(byAddress));
        Assert.Equal(["lukasz", "jozef-krk"], Ids(byName)); // both start with the phrase; more reports first
    }

    [Fact]
    public void Best_and_worst_order_by_score_then_report_count_and_put_unrated_places_last()
    {
        var best = Search.Search(new PlaceSearchQuery(null, PlaceSort.Best));
        var worst = Search.Search(new PlaceSearchQuery(null, PlaceSort.Worst));

        Assert.Equal(["jozef-rze", "maria", "lukasz", "jozef-krk", "anna"], Ids(best));
        Assert.Equal(["jozef-krk", "lukasz", "jozef-rze", "maria", "anna"], Ids(worst));
    }

    [Fact]
    public void Without_a_query_relevance_means_most_reports_first()
    {
        var result = Search.Search(new PlaceSearchQuery(""));

        Assert.Equal(["lukasz", "jozef-krk", "jozef-rze", "maria", "anna"], Ids(result));
    }

    [Fact]
    public void Reports_filter_drops_places_without_reports_and_paging_keeps_the_total()
    {
        var page = Search.Search(new PlaceSearchQuery(null, PlaceSort.Reports, WithReportsOnly: true, Offset: 1, Limit: 2));

        Assert.Equal(4, page.Total);
        Assert.Equal(["jozef-krk", "jozef-rze"], Ids(page));
    }

    [Theory]
    [InlineData("best", 0, 30, true)]
    [InlineData("BEST", 0, 30, true)]
    [InlineData(null, 0, 30, true)]
    [InlineData("rating", 0, 30, false)]
    [InlineData("name", -1, 30, false)]
    [InlineData("name", 0, 0, false)]
    [InlineData("name", 0, 101, false)]
    public void Query_parameters_are_validated(string? sort, int offset, int limit, bool valid)
    {
        var (query, error) = MapEndpoints.CreateSearchQuery("x", sort, false, offset, limit);

        Assert.Equal(valid, query is not null);
        Assert.Equal(valid, error is null);
    }

    [Fact]
    public void Pages_of_a_relevance_search_line_up_with_one_long_list()
    {
        // "a" starts a word only in "Anny"; the other names merely contain it, so they form a second, weaker
        // group in reports order. Pages of one place must walk across the groups like a single long page.
        var everything = Search.Search(new PlaceSearchQuery("a", Limit: 100));
        var paged = Enumerable.Range(0, everything.Total)
            .SelectMany(offset => Ids(Search.Search(new PlaceSearchQuery("a", Offset: offset, Limit: 1))))
            .ToArray();
        var acrossGroups = Search.Search(new PlaceSearchQuery("a", Offset: 0, Limit: 2));

        Assert.Equal(["anna", "lukasz", "jozef-krk", "jozef-rze", "maria"], Ids(everything));
        Assert.Equal(Ids(everything), paged);
        Assert.Equal(5, acrossGroups.Total);
        Assert.Equal(["anna", "lukasz"], Ids(acrossGroups));
        Assert.Empty(Search.Search(new PlaceSearchQuery("a", Offset: 50)).Items);
    }

    [Fact]
    public void Name_sort_ignores_case_and_diacritics()
    {
        var result = Search.Search(new PlaceSearchQuery(null, PlaceSort.Name));

        Assert.Equal(["maria", "jozef-rze", "anna", "jozef-krk", "lukasz"], Ids(result));
    }

    [Fact]
    public void Overlong_query_is_rejected()
    {
        var (query, _) = MapEndpoints.CreateSearchQuery(new string('a', PlaceSearch.MaxQueryLength + 1), null, false, 0, 30);

        Assert.Null(query);
    }
}
