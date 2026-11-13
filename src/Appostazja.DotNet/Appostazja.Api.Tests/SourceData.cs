using System.Globalization;
using System.Text;

namespace Appostazja.Api.Tests;

/// <summary>Builds upstream-shaped payloads: the responses CSV (with private columns) and the GeoJSON feed.</summary>
internal static class SourceData
{
    public const string Header =
        "submitted_at,church_name,church_address,church_website,action_type,action_rating,action_date,action_comment,age,gender";

    public static string Row(
        string name,
        string address,
        string rating = "easy",
        string stage = "apostasy",
        string website = "",
        string date = "2024-03-01",
        string comment = "",
        string submittedAt = "2024-03-02T10:00:00Z",
        string age = "25-34",
        string gender = "K") =>
        string.Join(',', new[] { submittedAt, name, address, website, stage, rating, date, comment, age, gender }
            .Select(Quote));

    public static byte[] Csv(params string[] rows) =>
        Encoding.UTF8.GetBytes(string.Join('\n', [Header, .. rows]) + "\n");

    public static byte[] GeoJson(params (string Id, string Name, string Address, double Lat, double Lon, int Count)[] places)
    {
        var features = places.Select(place => string.Create(CultureInfo.InvariantCulture,
            $$$"""{"type":"Feature","geometry":{"type":"Point","coordinates":[{{{place.Lon}}},{{{place.Lat}}}]},"properties":{"id":"{{{place.Id}}}","nm":"{{{place.Name}}}","adr":"{{{place.Address}}}","cnt":{{{place.Count}}}}}"""));
        return Encoding.UTF8.GetBytes($$"""{"type":"FeatureCollection","features":[{{string.Join(',', features)}}]}""");
    }

    /// <summary>A consistent dataset of <paramref name="count"/> parishes, each with one report.</summary>
    public static (byte[] Csv, byte[] GeoJson) Parishes(int count)
    {
        var rows = Enumerable.Range(1, count).Select(i => Row($"Parafia {i}", $"Ulica {i}, Miasto")).ToArray();
        var places = Enumerable.Range(1, count)
            .Select(i => ($"p{i}", $"Parafia {i}", $"Ulica {i}, Miasto", 50 + i * 0.01, 20.0, 1)).ToArray();
        return (Csv(rows), GeoJson(places));
    }

    private static string Quote(string value) => $"\"{value.Replace("\"", "\"\"", StringComparison.Ordinal)}\"";
}
