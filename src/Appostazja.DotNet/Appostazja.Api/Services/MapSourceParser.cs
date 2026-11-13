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

internal static partial class MapSourceParser
{
    internal static (PlaceRow[] Places, PlaceReportsRow[] Reports, int UnmatchedReportCount) Parse(
        byte[] responses, byte[] coordinates)
    {
        var reports = ParseResponses(responses);
        var locations = ParseCoordinates(coordinates)
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
                new PlaceReportsDto(parish?.Website, parish is null ? [] : [.. parish.Reports])));
        }

        if (places.Count == 0 || places.All(place => place.RatingsCount == 0))
        {
            throw new InvalidDataException("The sources have no places with matching reports.");
        }

        return ([.. places.OrderBy(place => place.Id, StringComparer.Ordinal)], [.. details],
            reports.Values.Sum(parish => parish.Reports.Count));
    }

    private static Dictionary<string, ParishReports> ParseResponses(byte[] payload)
    {
        using var stream = new MemoryStream(payload, writable: false);
        using var csv = new TextFieldParser(stream, new UTF8Encoding(false, true), detectEncoding: false)
        {
            Delimiters = [","],
            HasFieldsEnclosedInQuotes = true,
            TrimWhiteSpace = false
        };
        var result = new Dictionary<string, ParishReports>(StringComparer.OrdinalIgnoreCase);

        try
        {
            var headers = csv.ReadFields() ?? throw new InvalidDataException("The response CSV has no header.");
            headers[0] = headers[0].TrimStart('\uFEFF');
            var name = FindColumn(headers, "church_name");
            var address = FindColumn(headers, "church_address");
            var website = FindColumn(headers, "church_website");
            var stage = FindColumn(headers, "action_type");
            var rating = FindColumn(headers, "action_rating");
            var date = FindColumn(headers, "action_date");
            var comment = FindColumn(headers, "action_comment");

            while (!csv.EndOfData)
            {
                var line = csv.LineNumber;
                var fields = csv.ReadFields();
                if (fields is null || fields.Length != headers.Length ||
                    string.IsNullOrWhiteSpace(fields[name]) || string.IsNullOrWhiteSpace(fields[address]))
                {
                    throw new InvalidDataException($"Invalid response CSV row at line {line}.");
                }

                var key = Key(fields[name], fields[address]);
                if (!result.TryGetValue(key, out var parish))
                {
                    parish = new ParishReports();
                    result.Add(key, parish);
                }

                var reportRating = fields[rating].Trim();
                switch (reportRating)
                {
                    case "easy": parish.Easy++; break;
                    case "medium": parish.Medium++; break;
                    case "hard": parish.Hard++; break;
                    default: throw new InvalidDataException($"Invalid response rating at line {line}.");
                }

                var reportStage = fields[stage].Trim();
                if (reportStage is not ("before_apostasy" or "apostasy" or "after_apostasy"))
                {
                    throw new InvalidDataException($"Invalid response stage at line {line}.");
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
        catch (MalformedLineException exception)
        {
            throw new InvalidDataException($"Malformed response CSV at line {exception.LineNumber}.");
        }
        catch (DecoderFallbackException)
        {
            throw new InvalidDataException("The response CSV is not valid UTF-8.");
        }

        return result;
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

    private static int FindColumn(string[] headers, string name)
    {
        var index = Array.IndexOf(headers, name);
        if (index < 0 || index != Array.LastIndexOf(headers, name))
        {
            throw new InvalidDataException($"The response CSV must contain one '{name}' column.");
        }

        return index;
    }

    private static string Key(string name, string address) =>
        $"{Normalize(name)}\0{Normalize(address)}";

    private static string Normalize(string value) =>
        Whitespace().Replace(value.Trim().Normalize(), " ");

    [GeneratedRegex(@"\s+", RegexOptions.CultureInvariant)]
    private static partial Regex Whitespace();

    [GeneratedRegex(@"^(?:[\p{L}\p{N}-]+\.)+[\p{L}]{2,}(?::\d+)?(?:[/?#][^\s]*)?$", RegexOptions.CultureInvariant)]
    private static partial Regex BareWebsite();

    private static PlaceRow[] ParseCoordinates(ReadOnlySpan<byte> payload)
    {
        var source = JsonSerializer.Deserialize(payload, AppJsonSerializerContext.Default.SourceFeatureCollection)
            ?? throw new InvalidDataException("Source JSON is empty.");

        if (!string.Equals(source.Type, "FeatureCollection", StringComparison.Ordinal))
        {
            throw new InvalidDataException("Source JSON is not a GeoJSON FeatureCollection.");
        }

        var features = source.Features
            ?? throw new InvalidDataException("Source JSON has no features array.");

        var ids = new HashSet<string>(StringComparer.Ordinal);
        var places = new PlaceRow[features.Length];

        for (var i = 0; i < features.Length; i++)
        {
            var feature = features[i];
            var geometry = feature.Geometry
                ?? throw Invalid(i, "missing geometry");
            var properties = feature.Properties
                ?? throw Invalid(i, "missing properties");

            if (!string.Equals(feature.Type, "Feature", StringComparison.Ordinal) ||
                !string.Equals(geometry.Type, "Point", StringComparison.Ordinal))
            {
                throw Invalid(i, "only Point features are supported");
            }

            var coordinates = geometry.Coordinates;
            if (coordinates is null || coordinates.Length < 2)
            {
                throw Invalid(i, "missing coordinates");
            }

            var longitude = coordinates[0];
            var latitude = coordinates[1];
            if (!double.IsFinite(latitude) || latitude is < -90 or > 90 ||
                !double.IsFinite(longitude) || longitude is < -180 or > 180)
            {
                throw Invalid(i, "invalid coordinates");
            }

            var id = Require(properties.Id, i, "id");
            if (!ids.Add(id))
            {
                throw Invalid(i, $"duplicate id '{id}'");
            }

            places[i] = new PlaceRow
            {
                Id = id,
                Name = Require(properties.Name, i, "nm"),
                Address = Require(properties.Address, i, "adr"),
                Latitude = latitude,
                Longitude = longitude,
                RatingsCount = properties.RatingsCount
            };
        }

        return places;
    }

    private static string Require(string? value, int index, string field)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            throw Invalid(index, $"missing {field}");
        }

        return value.Trim();
    }

    private static InvalidDataException Invalid(int index, string message) =>
        new($"Invalid feature at index {index}: {message}.");

    private sealed class ParishReports
    {
        public List<ReportDto> Reports { get; } = [];
        public string? Website { get; set; }
        public int Easy { get; set; }
        public int Medium { get; set; }
        public int Hard { get; set; }
    }
}
