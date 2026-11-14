using ApostasyMap.Api.Contracts;

namespace ApostasyMap.Api.Services;

/// <summary>Aggregates for the statistics screen, computed once per dataset snapshot.</summary>
internal static class DatasetStats
{
    /// <summary>Separate bars for this many most recent years; anything older is folded into one period.</summary>
    internal const int YearBars = 9;
    internal const int TopCityCount = 10;

    internal static StatsDto Compute(
        IReadOnlyList<PlaceDto> places,
        IReadOnlyDictionary<string, PlaceReportsDto> reportsByPlace)
    {
        var ratings = new Counter();
        var before = new Counter();
        var apostasy = new Counter();
        var after = new Counter();
        var years = new SortedDictionary<int, Counter>();
        var cities = new Dictionary<string, CityCounter>(StringComparer.OrdinalIgnoreCase);
        int positive = 0, mixed = 0, negative = 0, none = 0;
        int reportCount = 0, commentCount = 0, datedCount = 0;

        foreach (var place in places)
        {
            switch (place.Tone)
            {
                case "positive": positive++; break;
                case "mixed": mixed++; break;
                case "negative": negative++; break;
                default: none++; break;
            }

            var city = CityOf(place.Address);
            CityCounter? cityCounter = null;
            if (city is not null && place.Ratings.Total > 0)
            {
                if (!cities.TryGetValue(city, out cityCounter))
                {
                    cities[city] = cityCounter = new CityCounter(city);
                }

                cityCounter.Places++;
            }

            if (!reportsByPlace.TryGetValue(place.Id, out var details))
            {
                continue;
            }

            foreach (var report in details.Reports)
            {
                reportCount++;
                ratings.Add(report.Rating);
                cityCounter?.Ratings.Add(report.Rating);
                (report.Stage switch
                {
                    "before_apostasy" => before,
                    "apostasy" => apostasy,
                    _ => after
                }).Add(report.Rating);

                if (report.Comment is not null)
                {
                    commentCount++;
                }

                // Dates are normalized to yyyy-MM-dd by the importer.
                if (report.Date is { Length: >= 4 } date && int.TryParse(date.AsSpan(0, 4), out var year))
                {
                    datedCount++;
                    if (!years.TryGetValue(year, out var yearCounter))
                    {
                        years[year] = yearCounter = new Counter();
                    }

                    yearCounter.Add(report.Rating);
                }
            }
        }

        var topCities = cities.Values
            .Where(city => city.Ratings.Total > 0)
            .OrderByDescending(city => city.Ratings.Total)
            .ThenByDescending(city => city.Places)
            .ThenBy(city => city.Name, StringComparer.Ordinal)
            .Take(TopCityCount)
            .Select(city =>
            {
                var cityRatings = city.Ratings.ToDto();
                return new CityStatsDto(city.Name, city.Places, cityRatings, PlaceSummary.RatingOf(cityRatings));
            })
            .ToArray();

        return new StatsDto(
            places.Count,
            reportCount,
            commentCount,
            datedCount,
            ratings.ToDto(),
            new StageRatingsDto(before.ToDto(), apostasy.ToDto(), after.ToDto()),
            new ToneCountsDto(positive, mixed, negative, none),
            Periods(years),
            topCities);
    }

    /// <summary>
    /// One bar per year for the last <see cref="YearBars"/> years up to the newest dated report, with empty years
    /// kept so the time axis stays linear. Everything older goes into one leading period.
    /// </summary>
    internal static PeriodStatsDto[] Periods(SortedDictionary<int, Counter> years)
    {
        if (years.Count == 0)
        {
            return [];
        }

        var last = years.Keys.Max();
        var firstSeparate = Math.Max(years.Keys.Min(), last - YearBars + 1);
        var result = new List<PeriodStatsDto>();

        var older = new Counter();
        var oldest = int.MaxValue;
        foreach (var (year, counter) in years)
        {
            if (year >= firstSeparate)
            {
                break;
            }

            oldest = Math.Min(oldest, year);
            older.Add(counter);
        }

        if (older.Total > 0)
        {
            result.Add(new PeriodStatsDto(oldest, firstSeparate - 1, older.ToDto()));
        }

        for (var year = firstSeparate; year <= last; year++)
        {
            var counter = years.GetValueOrDefault(year) ?? new Counter();
            result.Add(new PeriodStatsDto(year, year, counter.ToDto()));
        }

        return [.. result];
    }

    /// <summary>
    /// The town of a Polish address ("Lubelska 16, 35-234 Rzeszów" gives "Rzeszów"): the last comma-separated part
    /// without its postal code. Works for foreign codes too ("116 65 Stockholm").
    /// </summary>
    internal static string? CityOf(string address)
    {
        var comma = address.LastIndexOf(',');
        var part = (comma >= 0 ? address[(comma + 1)..] : address).Trim();
        var start = 0;
        while (start < part.Length && (char.IsDigit(part[start]) || part[start] is '-' or ' '))
        {
            start++;
        }

        var city = part[start..].Trim();
        // Without a postal code, a single part is more likely a street than a town.
        return city.Length == 0 || (start == 0 && comma < 0) ? null : city;
    }

    internal sealed class Counter
    {
        public int Easy { get; private set; }
        public int Medium { get; private set; }
        public int Hard { get; private set; }
        public int Total => Easy + Medium + Hard;

        public void Add(string rating)
        {
            switch (rating)
            {
                case "easy": Easy++; break;
                case "medium": Medium++; break;
                case "hard": Hard++; break;
            }
        }

        public void Add(Counter other)
        {
            Easy += other.Easy;
            Medium += other.Medium;
            Hard += other.Hard;
        }

        public RatingBreakdownDto ToDto() => new(Easy, Medium, Hard, Total);
    }

    private sealed class CityCounter(string name)
    {
        public string Name { get; } = name;
        public int Places { get; set; }
        public Counter Ratings { get; } = new();
    }
}
