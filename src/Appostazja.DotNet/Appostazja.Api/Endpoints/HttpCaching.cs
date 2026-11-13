using System.Globalization;
using ApostasyMap.Api.Data;
using Microsoft.Net.Http.Headers;

namespace ApostasyMap.Api.Endpoints;

internal static class HttpCaching
{
    internal const string DatasetCacheControl = "public, max-age=900, must-revalidate";

    /// <summary>
    /// Bump when the JSON shape of dataset responses changes. ETags combine it with the dataset version, so a
    /// client holding a response in the old shape gets the new body instead of a 304.
    /// </summary>
    internal const string ResponseFormat = "f2";

    internal static string? ResponseVersion(DatasetStateRow state) =>
        string.IsNullOrWhiteSpace(state.Version) ? null : $"{state.Version}.{ResponseFormat}";

    /// <summary>Honours q-values, including <c>gzip;q=0</c> and an <c>identity</c> preference.</summary>
    internal static bool AcceptsGzip(HttpRequest request)
    {
        double? gzip = null;
        double? wildcard = null;
        double? identity = null;
        foreach (var coding in request.GetTypedHeaders().AcceptEncoding)
        {
            if (coding.Value.Equals("gzip", StringComparison.OrdinalIgnoreCase))
            {
                gzip = coding.Quality ?? 1;
            }
            else if (coding.Value.Equals("*", StringComparison.Ordinal))
            {
                wildcard = coding.Quality ?? 1;
            }
            else if (coding.Value.Equals("identity", StringComparison.OrdinalIgnoreCase))
            {
                identity = coding.Quality ?? 1;
            }
        }

        var quality = gzip ?? wildcard ?? 0;
        return quality > 0 && quality >= (identity ?? 0);
    }

    internal static void ApplyDatasetHeaders(HttpResponse response, DatasetStateRow state, string? version = null)
    {
        response.Headers.CacheControl = DatasetCacheControl;

        version ??= ResponseVersion(state);
        if (!string.IsNullOrWhiteSpace(version))
        {
            response.Headers.ETag = $"\"{version}\"";
        }

        if (state.LastChangedAtUnixSeconds > 0)
        {
            response.Headers.LastModified = DateTimeOffset
                .FromUnixTimeSeconds(state.LastChangedAtUnixSeconds)
                .ToString("R", CultureInfo.InvariantCulture);
        }
    }

    internal static bool MatchesIfNoneMatch(HttpRequest request, string? version)
    {
        if (string.IsNullOrWhiteSpace(version))
        {
            return false;
        }

        var expected = new EntityTagHeaderValue($"\"{version}\"");
        foreach (var value in request.GetTypedHeaders().IfNoneMatch)
        {
            if (value.Equals(EntityTagHeaderValue.Any) || value.Compare(expected, useStrongComparison: false))
            {
                return true;
            }
        }

        return false;
    }
}
