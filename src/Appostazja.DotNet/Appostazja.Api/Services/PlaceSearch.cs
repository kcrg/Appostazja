using System.Text;
using ApostasyMap.Api.Contracts;

namespace ApostasyMap.Api.Services;

public enum PlaceSort
{
    /// <summary>Best name match first; without a query the same as <see cref="Reports"/>.</summary>
    Relevance,
    /// <summary>Highest score first. Places without reports go last.</summary>
    Best,
    /// <summary>Lowest score first. Places without reports go last.</summary>
    Worst,
    /// <summary>Most reports first.</summary>
    Reports,
    /// <summary>Alphabetical by name, ignoring case and diacritics.</summary>
    Name
}

public sealed record PlaceSearchQuery(
    string? Text,
    PlaceSort Sort = PlaceSort.Relevance,
    bool WithReportsOnly = false,
    int Offset = 0,
    int Limit = PlaceSearch.DefaultLimit);

/// <summary>
/// Search over one dataset snapshot. Matching ignores case and diacritics ("jozefa" finds "Józefa"), and every
/// word of the query must appear in the name or the address, so "józefa rzeszów" narrows to one city.
/// </summary>
public sealed class PlaceSearch
{
    public const int DefaultLimit = 30;
    public const int MaxLimit = 100;
    public const int MaxQueryLength = 100;

    private const int MaxRelevance = 4;

    // Every sort is a fixed order of the snapshot, so the places are sorted once per dataset instead of on every
    // request; a search walks the order it needs and keeps only the requested page. Relevance groups the matches
    // by how well the name matches, and inside a group it is the reports order.
    private readonly Entry[] _byReports;
    private readonly Entry[] _byName;
    private readonly Entry[] _best;
    private readonly Entry[] _worst;

    public PlaceSearch(IReadOnlyList<PlaceDto> places)
    {
        var entries = new Entry[places.Count];
        for (var i = 0; i < places.Count; i++)
        {
            var place = places[i];
            entries[i] = new Entry(place, Fold(place.Name), Fold(place.Address));
        }

        _byReports = Sorted(entries, PlaceSort.Reports);
        _byName = Sorted(entries, PlaceSort.Name);
        _best = Sorted(entries, PlaceSort.Best);
        _worst = Sorted(entries, PlaceSort.Worst);
    }

    public static PlaceSearch Empty { get; } = new([]);

    public PlaceSearchResultDto Search(PlaceSearchQuery query)
    {
        var tokens = Tokenize(query.Text);
        var sort = query.Sort == PlaceSort.Relevance && tokens.Length == 0 ? PlaceSort.Reports : query.Sort;
        return sort == PlaceSort.Relevance
            ? SearchByRelevance(query, tokens)
            : SearchInOrder(query, tokens, sort switch
            {
                PlaceSort.Best => _best,
                PlaceSort.Worst => _worst,
                PlaceSort.Name => _byName,
                _ => _byReports
            });
    }

    private static PlaceSearchResultDto SearchInOrder(PlaceSearchQuery query, string[] tokens, Entry[] order)
    {
        var items = new List<PlaceDto>(Math.Min(query.Limit, order.Length));
        var total = 0;
        foreach (var entry in order)
        {
            if (!Includes(query, entry, tokens))
            {
                continue;
            }

            if (total >= query.Offset && items.Count < query.Limit)
            {
                items.Add(entry.Place);
            }

            total++;
        }

        return new PlaceSearchResultDto(total, query.Offset, query.Limit, [.. items]);
    }

    private PlaceSearchResultDto SearchByRelevance(PlaceSearchQuery query, string[] tokens)
    {
        var phrase = string.Join(' ', tokens);
        var matches = new List<(Entry Entry, int Relevance)>();
        Span<int> counts = stackalloc int[MaxRelevance + 1];
        foreach (var entry in _byReports)
        {
            if (Includes(query, entry, tokens))
            {
                var relevance = Relevance(entry, tokens, phrase);
                matches.Add((entry, relevance));
                counts[relevance]++;
            }
        }

        // The page is a window over the groups from the most relevant down; skip whole groups before it.
        var items = new List<PlaceDto>(Math.Min(query.Limit, matches.Count));
        var skip = query.Offset;
        for (var relevance = MaxRelevance; relevance > 0 && items.Count < query.Limit; relevance--)
        {
            if (skip >= counts[relevance])
            {
                skip -= counts[relevance];
                continue;
            }

            foreach (var match in matches)
            {
                if (match.Relevance != relevance)
                {
                    continue;
                }

                if (skip > 0)
                {
                    skip--;
                }
                else if (items.Count < query.Limit)
                {
                    items.Add(match.Entry.Place);
                }
                else
                {
                    break;
                }
            }
        }

        return new PlaceSearchResultDto(matches.Count, query.Offset, query.Limit, [.. items]);
    }

    private static bool Includes(PlaceSearchQuery query, Entry entry, string[] tokens) =>
        (!query.WithReportsOnly || entry.Place.Ratings.Total > 0) && (tokens.Length == 0 || Matches(entry, tokens));

    private static Entry[] Sorted(Entry[] entries, PlaceSort sort)
    {
        var sorted = (Entry[])entries.Clone();
        Array.Sort(sorted, (a, b) => Compare(sort, (a, 0), (b, 0)));
        return sorted;
    }

    private static bool Matches(Entry entry, string[] tokens)
    {
        foreach (var token in tokens)
        {
            if (!entry.Name.Contains(token, StringComparison.Ordinal) &&
                !entry.Address.Contains(token, StringComparison.Ordinal))
            {
                return false;
            }
        }

        return true;
    }

    /// <summary>Name starting with the query, then a name word starting with it, then all words in the name.</summary>
    private static int Relevance(Entry entry, string[] tokens, string phrase)
    {
        if (entry.Name.StartsWith(phrase, StringComparison.Ordinal))
        {
            return 4;
        }

        if (entry.Name.Contains(' ' + phrase, StringComparison.Ordinal))
        {
            return 3;
        }

        return tokens.All(token => entry.Name.Contains(token, StringComparison.Ordinal)) ? 2 : 1;
    }

    private static int Compare(PlaceSort sort, (Entry Entry, int Relevance) a, (Entry Entry, int Relevance) b)
    {
        var x = a.Entry.Place;
        var y = b.Entry.Place;
        var result = sort switch
        {
            PlaceSort.Relevance => b.Relevance.CompareTo(a.Relevance),
            PlaceSort.Best => CompareRated(x, y, descending: true),
            PlaceSort.Worst => CompareRated(x, y, descending: false),
            PlaceSort.Reports => y.Ratings.Total.CompareTo(x.Ratings.Total),
            _ => 0
        };

        if (result == 0 && sort is PlaceSort.Relevance or PlaceSort.Reports)
        {
            result = y.Ratings.Total.CompareTo(x.Ratings.Total);
        }

        if (result == 0)
        {
            result = string.CompareOrdinal(a.Entry.Name, b.Entry.Name);
        }

        return result != 0 ? result : string.CompareOrdinal(x.Id, y.Id);
    }

    /// <summary>
    /// Places without reports have no rating, so they always go last. Among equal scores, more reports first:
    /// a score backed by ten reports says more than one backed by a single report.
    /// </summary>
    private static int CompareRated(PlaceDto x, PlaceDto y, bool descending)
    {
        var xRated = x.Ratings.Total > 0;
        var yRated = y.Ratings.Total > 0;
        if (xRated != yRated)
        {
            return xRated ? -1 : 1;
        }

        var byScore = descending ? y.Score.CompareTo(x.Score) : x.Score.CompareTo(y.Score);
        return byScore != 0 ? byScore : y.Ratings.Total.CompareTo(x.Ratings.Total);
    }

    internal static string[] Tokenize(string? text) =>
        string.IsNullOrWhiteSpace(text)
            ? []
            : Fold(text).Split(' ', StringSplitOptions.RemoveEmptyEntries);

    /// <summary>Lower case, Latin diacritics removed, punctuation turned into single spaces.</summary>
    internal static string Fold(string value)
    {
        var builder = new StringBuilder(value.Length);
        var pendingSpace = false;
        foreach (var raw in value)
        {
            var c = RemoveDiacritic(char.ToLowerInvariant(raw));
            if (char.IsLetterOrDigit(c))
            {
                if (pendingSpace && builder.Length > 0)
                {
                    builder.Append(' ');
                }

                builder.Append(c);
                pendingSpace = false;
            }
            else
            {
                pendingSpace = true;
            }
        }

        return builder.ToString();
    }

    // An explicit table instead of Unicode normalization keeps the result identical with invariant globalization.
    private static char RemoveDiacritic(char c) => c switch
    {
        'ą' or 'á' or 'à' or 'â' or 'ä' or 'ã' or 'å' => 'a',
        'ć' or 'č' or 'ç' => 'c',
        'ď' => 'd',
        'ę' or 'é' or 'è' or 'ê' or 'ë' or 'ě' => 'e',
        'í' or 'ì' or 'î' or 'ï' => 'i',
        'ł' or 'ľ' or 'ĺ' => 'l',
        'ń' or 'ň' or 'ñ' => 'n',
        'ó' or 'ò' or 'ô' or 'ö' or 'õ' or 'ő' => 'o',
        'ř' => 'r',
        'ś' or 'š' or 'ß' => 's',
        'ť' => 't',
        'ú' or 'ù' or 'û' or 'ü' or 'ů' or 'ű' => 'u',
        'ý' or 'ÿ' => 'y',
        'ź' or 'ż' or 'ž' => 'z',
        _ => c
    };

    private sealed record Entry(PlaceDto Place, string Name, string Address);
}
