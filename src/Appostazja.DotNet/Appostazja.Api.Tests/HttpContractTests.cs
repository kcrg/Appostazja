using ApostasyMap.Api.Data;
using ApostasyMap.Api.Endpoints;
using Microsoft.AspNetCore.Http;

namespace Appostazja.Api.Tests;

public class HttpContractTests
{
    [Theory]
    [InlineData("gzip, deflate, br", true)]
    [InlineData("gzip;q=0", false)]
    [InlineData("*", true)]
    [InlineData("gzip;q=0.5, identity", false)]
    [InlineData("identity;q=0.1, gzip;q=0.8", true)]
    [InlineData("", false)]
    public void Gzip_is_served_only_when_the_client_prefers_it(string acceptEncoding, bool expected)
    {
        var context = new DefaultHttpContext();
        context.Request.Headers.AcceptEncoding = acceptEncoding;

        Assert.Equal(expected, HttpCaching.AcceptsGzip(context.Request));
    }

    [Theory]
    [InlineData("\"abc\"", true)]
    [InlineData("W/\"abc\"", true)]
    [InlineData("\"other\", \"abc\"", true)]
    [InlineData("*", true)]
    [InlineData("\"abc-gzip\"", false)]
    public void If_none_match_compares_dataset_version_weakly(string ifNoneMatch, bool expected)
    {
        var context = new DefaultHttpContext();
        context.Request.Headers.IfNoneMatch = ifNoneMatch;

        Assert.Equal(expected, HttpCaching.MatchesIfNoneMatch(context.Request, "abc"));
    }

    [Fact]
    public void Readiness_fails_without_data()
    {
        var reason = HealthEndpoints.GetNotReadyReason(new DatasetStateRow(), DateTimeOffset.UtcNow, TimeSpan.FromHours(48));

        Assert.NotNull(reason);
    }

    [Fact]
    public void Readiness_fails_when_data_is_stale_and_explains_the_last_failure()
    {
        var now = new DateTimeOffset(2026, 11, 13, 12, 0, 0, TimeSpan.Zero);
        var state = new DatasetStateRow
        {
            PlaceCount = 10,
            LastCheckedAtUnixSeconds = now.AddHours(-72).ToUnixTimeSeconds(),
            LastError = "Too many invalid source rows: 9 of 10."
        };

        var reason = HealthEndpoints.GetNotReadyReason(state, now, TimeSpan.FromHours(48));

        Assert.Contains("72 h", reason);
        Assert.Contains("Too many invalid source rows", reason);
    }

    [Fact]
    public void Readiness_passes_with_recently_checked_data()
    {
        var now = DateTimeOffset.UtcNow;
        var state = new DatasetStateRow { PlaceCount = 10, LastCheckedAtUnixSeconds = now.AddHours(-30).ToUnixTimeSeconds() };

        Assert.Null(HealthEndpoints.GetNotReadyReason(state, now, TimeSpan.FromHours(48)));
    }
}
