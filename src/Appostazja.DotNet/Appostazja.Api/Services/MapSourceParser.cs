using System.Text.Json;
using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;
using ApostasyMap.Api.Contracts;
using ApostasyMap.Api.Data;
using ApostasyMap.Api.Json;
using ApostasyMap.Api.SourceModels;
using Microsoft.VisualBasic.FileIO;

namespace ApostasyMap.Api.Services;

internal sealed record ParsedDataset(
    PlaceRow[] Places,
    PlaceReportsRow[] Reports,
    int UnmatchedReportCount,
    int TotalReportRows,
    int SkippedReportRows,
    int TotalFeatures,
    int SkippedFeatures)
{
    public int SkippedRows => SkippedReportRows + SkippedFeatures;
    public int TotalRows => TotalReportRows + TotalFeatures;
}

/// <summary>
/// Joins the responses CSV with the GeoJSON coordinates. Structural problems (missing columns, invalid
/// encoding, not a FeatureCollection) throw; individual bad rows and features are skipped and counted so a
/// single upstream typo does not block every future import.
/// </summary>
internal static partial class MapSourceParser
{
    internal static ParsedDataset Parse(byte[] responses, byte[] coordinates)
    {
        var (reports, totalReportRows, skippedReportRows) = ParseResponses(responses);
        var (features, totalFeatures, skippedFeatures) = ParseCoordinates(coordinates);
        var locations = features
            .GroupBy(place => Key(place.Name, place.Address), StringComparer.OrdinalIgnoreCase);
        var places = new List<PlaceRow>();
        var details = new List<PlaceReportsRow>();

        foreach (var group in locations)
        {
            // The same address can have multiple pins. Keep the main pin and resolve old IDs as aliases.
            var pins = group.OrderByDescending(place => place.RatingsCount)
                .ThenBy(place => place.Id, StringComparer.Ordinal).ToArray();
            var place = pins[0];
            reports.Remove(group.Key, out var parish);
            place.PositiveRatings = parish?.Easy ?? 0;
            place.NeutralRatings = parish?.Medium ?? 0;
            place.NegativeRatings = parish?.Hard ?? 0;
            place.RatingsCount = parish?.Reports.Count ?? 0;
            place.Score = place.RatingsCount == 0
                ? 0
                : (double)(place.PositiveRatings - place.NegativeRatings) / place.RatingsCount;

            places.Add(place);
            details.Add(new PlaceReportsRow(place.Id, pins.Skip(1).Select(pin => pin.Id).ToArray(),
                // The CSV is in submission order; clients show the latest experiences first.
                new PlaceReportsDto(parish?.Website, parish is null ? [] : [.. Enumerable.Reverse(parish.Reports)])));
        }

        if (places.Count == 0 || places.All(place => place.RatingsCount == 0))
        {
            throw new InvalidDataException("The sources have no places with matching reports.");
        }

        return new ParsedDataset(
            [.. places.OrderBy(place => place.Id, StringComparer.Ordinal)],
            [.. details],
            reports.Values.Sum(parish => parish.Reports.Count),
            totalReportRows,
            skippedReportRows,
            totalFeatures,
            skippedFeatures);
    }

    private static (Dictionary<string, ParishReports> Reports, int Total, int Skipped) ParseResponses(byte[] payload)
    {
        using var csv = ResponsesCsv.OpenParser(payload);
        var result = new Dictionary<string, ParishReports>(StringComparer.OrdinalIgnoreCase);
        var total = 0;
        var skipped = 0;

        try
        {
            var headers = csv.ReadFields() ?? throw new InvalidDataException("The response CSV has no header.");
            headers[0] = headers[0].TrimStart('﻿');
            var name = ResponsesCsv.FindColumn(headers, ResponsesCsv.Name);
            var address = ResponsesCsv.FindColumn(headers, ResponsesCsv.Address);
            var website = ResponsesCsv.FindColumn(headers, ResponsesCsv.Website);
            var stage = ResponsesCsv.FindColumn(headers, ResponsesCsv.Stage);
            var rating = ResponsesCsv.FindColumn(headers, ResponsesCsv.Rating);
            var date = ResponsesCsv.FindColumn(headers, ResponsesCsv.Date);
            var comment = ResponsesCsv.FindColumn(headers, ResponsesCsv.Comment);

            while (!csv.EndOfData)
            {
                total++;
                string[]? fields;
                try
                {
                    fields = csv.ReadFields();
                }
                catch (MalformedLineException)
                {
                    skipped++;
                    continue;
                }

                if (fields is null || fields.Length != headers.Length ||
                    string.IsNullOrWhiteSpace(fields[name]) || string.IsNullOrWhiteSpace(fields[address]))
                {
                    skipped++;
                    continue;
                }

                var reportRating = fields[rating].Trim();
                var reportStage = fields[stage].Trim();
                if (reportRating is not ("easy" or "medium" or "hard") ||
                    reportStage is not ("before_apostasy" or "apostasy" or "after_apostasy"))
                {
                    skipped++;
                    continue;
                }

                var key = Key(fields[name], fields[address]);
                if (!result.TryGetValue(key, out var parish))
                {
                    parish = new ParishReports();
                    result.Add(key, parish);
                }

                switch (reportRating)
                {
                    case "easy": parish.Easy++; break;
                    case "medium": parish.Medium++; break;
                    default: parish.Hard++; break;
                }

                var reportDate = DateOnly.TryParseExact(fields[date].Trim(), "yyyy-MM-dd",
                    CultureInfo.InvariantCulture, DateTimeStyles.None, out var parsedDate)
                    ? parsedDate.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture)
                    : null;
                var text = fields[comment].Trim();
                parish.Reports.Add(new ReportDto(reportStage, reportRating, reportDate, text.Length == 0 ? null : text));

                parish.Website ??= ParseWebsite(fields[website]);
            }
        }
        catch (DecoderFallbackException)
        {
            throw new InvalidDataException("The response CSV is not valid UTF-8.");
        }

        return (result, total, skipped);
    }

    private static string? ParseWebsite(string website)
    {
        website = website.Trim();
        if (!website.Contains("://", StringComparison.Ordinal) && BareWebsite().IsMatch(website))
        {
            website = $"https://{website}";
        }

        if (!Uri.TryCreate(website, UriKind.Absolute, out var url) ||
            (url.Scheme != Uri.UriSchemeHttps && url.Scheme != Uri.UriSchemeHttp) ||
            url.UserInfo.Length > 0)
        {
            return null;
        }

        return url.AbsoluteUri;
    }

    private static string Key(string name, string address) =>
        $"{Normalize(name)}\0{Normalize(address)}";

    private static string Normalize(string value) =>
        Whitespace().Replace(value.Trim().Normalize(), " ");

    [GeneratedRegex(@"\s+", RegexOptions.CultureInvariant)]
    private static partial Regex Whitespace();

    [GeneratedRegex(@"^(?:[\p{L}\p{N}-]+\.)+[\p{L}]{2,}(?::\d+)?(?:[/?#][^\s]*)?$", RegexOptions.CultureInvariant)]
    private static partial Regex BareWebsite();

    private static (List<PlaceRow> Places, int Total, int Skipped) ParseCoordinates(ReadOnlySpan<byte> payload)
    {
        SourceFeatureCollection? source;
        try
        {
            source = JsonSerializer.Deserialize(payload, AppJsonSerializerContext.Default.SourceFeatureCollection);
        }
        catch (JsonException)
        {
            throw new InvalidDataException("The coordinate feed is not valid GeoJSON.");
        }

        if (source is null)
        {
            throw new InvalidDataException("Source JSON is empty.");
        }

        if (!string.Equals(source.Type, "FeatureCollection", StringComparison.Ordinal))
        {
            throw new InvalidDataException("Source JSON is not a GeoJSON FeatureCollection.");
        }

        var features = source.Features
            ?? throw new InvalidDataException("Source JSON has no features array.");

        var ids = new HashSet<string>(StringComparer.Ordinal);
        var places = new List<PlaceRow>(features.Length);
        var skipped = 0;

        foreach (var feature in features)
        {
            var place = TryReadFeature(feature);
            if (place is null || !ids.Add(place.Id))
            {
                skipped++;
                continue;
            }

            places.Add(place);
        }

        return (places, features.Length, skipped);
    }

    private static PlaceRow? TryReadFeature(SourceFeature? feature)
    {
        if (feature is not { Geometry: { } geometry, Properties: { } properties } ||
            !string.Equals(feature.Type, "Feature", StringComparison.Ordinal) ||
            !string.Equals(geometry.Type, "Point", StringComparison.Ordinal) ||
            geometry.Coordinates is not { Length: >= 2 } coordinates)
        {
            return null;
        }

        var longitude = coordinates[0];
        var latitude = coordinates[1];
        if (!double.IsFinite(latitude) || latitude is < -90 or > 90 ||
            !double.IsFinite(longitude) || longitude is < -180 or > 180 ||
            string.IsNullOrWhiteSpace(properties.Id) ||
            string.IsNullOrWhiteSpace(properties.Name) ||
            string.IsNullOrWhiteSpace(properties.Address))
        {
            return null;
        }

        return new PlaceRow
        {
            Id = properties.Id.Trim(),
            Name = properties.Name.Trim(),
            Address = properties.Address.Trim(),
            Latitude = latitude,
            Longitude = longitude,
            RatingsCount = properties.RatingsCount
        };
    }

    private sealed class ParishReports
    {
        public List<ReportDto> Reports { get; } = [];
        public string? Website { get; set; }
        public int Easy { get; set; }
        public int Medium { get; set; }
        public int Hard { get; set; }
    }
}
