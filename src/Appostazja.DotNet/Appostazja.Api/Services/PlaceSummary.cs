using ApostasyMap.Api.Contracts;

namespace ApostasyMap.Api.Services;

/// <summary>
/// Per-place figures derived from the stored reports. Computed when the snapshot is built, so databases
/// written by older versions get them without a re-import.
/// </summary>
internal static class PlaceSummary
{
    internal static PlaceDto WithReportSummary(PlaceDto place, PlaceReportsDto? details)
    {
        var before = new Counter();
        var apostasy = new Counter();
        var after = new Counter();
        string? lastActionDate = null;

        foreach (var report in details?.Reports ?? [])
        {
            var counter = report.Stage switch
            {
                "before_apostasy" => before,
                "apostasy" => apostasy,
                "after_apostasy" => after,
                _ => null
            };
            counter?.Add(report.Rating);

            // Dates are normalized to yyyy-MM-dd by the importer, so ordinal order is chronological.
            if (report.Date is { } date && string.CompareOrdinal(date, lastActionDate) > 0)
            {
                lastActionDate = date;
            }
        }

        return place with
        {
            Stages = new StageRatingsDto(before.ToDto(), apostasy.ToDto(), after.ToDto()),
            LastActionDate = lastActionDate,
            Rating = place.Ratings.Total == 0 ? null : Math.Round((place.Score + 1) * 2.5, 1, MidpointRounding.AwayFromZero),
            Tone = ToneOf(place)
        };
    }

    /// <summary>The thresholds the map uses for pin colours, so every client shows the same verdict.</summary>
    internal static string ToneOf(PlaceDto place) => place switch
    {
        { Ratings.Total: 0 } => "none",
        { Score: < -0.2 } => "negative",
        { Score: <= 0.2 } => "mixed",
        _ => "positive"
    };

    private sealed class Counter
    {
        private int _easy;
        private int _medium;
        private int _hard;

        public void Add(string rating)
        {
            switch (rating)
            {
                case "easy": _easy++; break;
                case "medium": _medium++; break;
                case "hard": _hard++; break;
            }
        }

        public RatingBreakdownDto ToDto() => new(_easy, _medium, _hard, _easy + _medium + _hard);
    }
}
