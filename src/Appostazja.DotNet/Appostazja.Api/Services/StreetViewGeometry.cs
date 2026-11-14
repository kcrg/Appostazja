using System.Security.Cryptography;
using System.Text;

namespace ApostasyMap.Api.Services;

/// <summary>Signs Google Maps Platform URLs with the project's URL signing secret (HMAC-SHA1).</summary>
internal static class UrlSigner
{
    /// <summary>The Cloud console shows the secret as URL-safe base64.</summary>
    public static byte[] DecodeSecret(string secret) =>
        Convert.FromBase64String(secret.Trim().Replace('-', '+').Replace('_', '/'));

    /// <summary>Signature of the path and query (everything after the host), as URL-safe base64.</summary>
    public static string Signature(string pathAndQuery, byte[] secret) =>
        Convert.ToBase64String(HMACSHA1.HashData(secret, Encoding.ASCII.GetBytes(pathAndQuery)))
            .Replace('+', '-')
            .Replace('/', '_');

    /// <summary>The URL with <c>&amp;signature=</c> appended; unchanged without a secret.</summary>
    public static string Sign(string url, byte[]? secret)
    {
        if (secret is null)
        {
            return url;
        }

        var uri = new Uri(url);
        return $"{url}&signature={Signature(uri.PathAndQuery, secret)}";
    }
}

internal sealed record PanoramaCandidate(string PanoId, double Latitude, double Longitude, string? Copyright);

/// <summary>Picking a street-level panorama of the parish and pointing it at the church.</summary>
internal static class StreetViewGeometry
{
    private const double EarthRadiusMeters = 6_371_000.0;

    /// <summary>Google's own imagery; photo spheres uploaded by users carry the author's name instead.</summary>
    public static bool IsGoogleImagery(string? copyright) =>
        copyright?.Contains("Google", StringComparison.OrdinalIgnoreCase) == true;

    /// <summary>
    /// The Google panorama closest to the parish. User photo spheres are skipped even when closer: even with
    /// source=outdoor they are often taken inside the church.
    /// </summary>
    public static PanoramaCandidate? PickGoogleImagery(
        double latitude, double longitude, IEnumerable<PanoramaCandidate> candidates) =>
        candidates
            .Where(candidate => IsGoogleImagery(candidate.Copyright))
            .MinBy(candidate => DistanceMeters(latitude, longitude, candidate.Latitude, candidate.Longitude));

    /// <summary>Points around the parish to probe for Google imagery: 8 directions at 40 m and 80 m.</summary>
    public static IReadOnlyList<(double Latitude, double Longitude)> ProbePoints(double latitude, double longitude)
    {
        var points = new List<(double, double)>(16);
        foreach (var radius in new[] { 40.0, 80.0 })
        {
            for (var step = 0; step < 8; step++)
            {
                var angle = double.DegreesToRadians(step * 45.0);
                var dLat = radius * Math.Cos(angle) / EarthRadiusMeters;
                var dLng = radius * Math.Sin(angle) / (EarthRadiusMeters * Math.Cos(double.DegreesToRadians(latitude)));
                points.Add((latitude + double.RadiansToDegrees(dLat), longitude + double.RadiansToDegrees(dLng)));
            }
        }

        return points;
    }

    public static double DistanceMeters(double lat1, double lng1, double lat2, double lng2)
    {
        var dLat = double.DegreesToRadians(lat2 - lat1);
        var dLng = double.DegreesToRadians(lng2 - lng1);
        var a = Math.Pow(Math.Sin(dLat / 2), 2) +
                Math.Cos(double.DegreesToRadians(lat1)) * Math.Cos(double.DegreesToRadians(lat2)) *
                Math.Pow(Math.Sin(dLng / 2), 2);
        return 2 * EarthRadiusMeters * Math.Atan2(Math.Sqrt(a), Math.Sqrt(1 - a));
    }

    /// <summary>Initial compass bearing from the panorama to the parish, so the camera faces the church.</summary>
    public static int Bearing(double fromLat, double fromLng, double toLat, double toLng)
    {
        var phi1 = double.DegreesToRadians(fromLat);
        var phi2 = double.DegreesToRadians(toLat);
        var deltaLambda = double.DegreesToRadians(toLng - fromLng);
        var y = Math.Sin(deltaLambda) * Math.Cos(phi2);
        var x = Math.Cos(phi1) * Math.Sin(phi2) - Math.Sin(phi1) * Math.Cos(phi2) * Math.Cos(deltaLambda);
        return (int)Math.Round(double.RadiansToDegrees(Math.Atan2(y, x)) + 360.0) % 360;
    }
}
