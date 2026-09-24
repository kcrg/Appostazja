using System.Net;
using System.Text;
using Appostazja.Core.Services;
using FluentAssertions;

namespace Appostazja.Core.Tests;

[TestFixture]
public sealed class MapDataServiceTests
{
    [Test]
    public async Task GetChurchesAsync_ShouldDeserializeGeoJsonAndCacheResponse()
    {
        const string json =
            """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": { "type": "Point", "coordinates": [21.0122, 52.2297] },
                  "properties": {
                    "id": "warszawa",
                    "church_name": "Parafia testowa",
                    "church_address": "Warszawa",
                    "average_rating": 0.5
                  }
                },
                {
                  "type": "Feature",
                  "geometry": { "type": "Point", "coordinates": [500, 500] },
                  "properties": { "id": "invalid" }
                }
              ]
            }
            """;

        var handler = new StubHttpMessageHandler(json);
        var service = new MapDataService(new HttpClient(handler));

        var first = await service.GetChurchesAsync();
        var second = await service.GetChurchesAsync();
        var refreshed = await service.GetChurchesAsync(forceRefresh: true);

        first.Features.Should().ContainSingle();
        first.Features[0].Properties.Id.Should().Be("warszawa");
        first.Features[0].Geometry.Coordinates.Should().Equal(21.0122, 52.2297);
        second.Features.Should().BeSameAs(first.Features);
        second.ContentVersion.Should().Be(first.ContentVersion);
        refreshed.Features.Should().BeSameAs(first.Features);
        refreshed.ContentVersion.Should().Be(first.ContentVersion);
        handler.CallCount.Should().Be(2);
    }
    [Test]
    public async Task GetChurchesAsync_ShouldUseFreshPersistentCache()
    {
        const string json =
            """
            {"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[19.4,52.1]},"properties":{"id":"cached","church_name":"Cache","church_address":"Polska","average_rating":4.2}}]}
            """;
        var now = new DateTimeOffset(2026, 8, 12, 14, 30, 0, TimeSpan.Zero);
        var cache = new StubMapDataCache(
            new MapDataCacheEntry(json, now.AddHours(-2)));
        var handler = new StubHttpMessageHandler("{}", HttpStatusCode.ServiceUnavailable);
        var service = new MapDataService(
            new HttpClient(handler),
            cache,
            new TestTimeProvider(now));

        MapDataSnapshot result = await service.GetChurchesAsync();

        result.Features.Should().ContainSingle();
        result.Features[0].Properties.Id.Should().Be("cached");
        result.IsFromCache.Should().BeTrue();
        result.IsStale.Should().BeFalse();
        handler.CallCount.Should().Be(0);
    }

    [Test]
    public async Task GetChurchesAsync_ShouldRefreshCacheAfterOneDay()
    {
        const string cachedJson =
            """
            {"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[19.4,52.1]},"properties":{"id":"cached"}}]}
            """;
        const string freshJson =
            """
            {"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[21.0,52.2]},"properties":{"id":"fresh"}}]}
            """;
        var now = new DateTimeOffset(2026, 8, 12, 14, 30, 0, TimeSpan.Zero);
        var cache = new StubMapDataCache(
            new MapDataCacheEntry(cachedJson, now - MapDataService.CacheLifetime));
        var handler = new StubHttpMessageHandler(freshJson);
        var service = new MapDataService(
            new HttpClient(handler),
            cache,
            new TestTimeProvider(now));

        MapDataSnapshot result = await service.GetChurchesAsync();

        result.Features.Should().ContainSingle();
        result.Features[0].Properties.Id.Should().Be("fresh");
        result.IsFromCache.Should().BeFalse();
        handler.CallCount.Should().Be(1);
        cache.WrittenEntry.Should().NotBeNull();
        cache.WrittenEntry!.RetrievedAt.Should().Be(now);
    }

    [Test]
    public async Task GetChurchesAsync_ShouldUseStaleCacheWhenRefreshFails()
    {
        const string json =
            """
            {"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[19.4,52.1]},"properties":{"id":"stale"}}]}
            """;
        var now = new DateTimeOffset(2026, 8, 12, 14, 30, 0, TimeSpan.Zero);
        var cache = new StubMapDataCache(
            new MapDataCacheEntry(json, now.AddDays(-2)));
        var handler = new StubHttpMessageHandler("{}", HttpStatusCode.ServiceUnavailable);
        var service = new MapDataService(
            new HttpClient(handler),
            cache,
            new TestTimeProvider(now));

        MapDataSnapshot result = await service.GetChurchesAsync();

        result.Features.Should().ContainSingle();
        result.Features[0].Properties.Id.Should().Be("stale");
        result.IsFromCache.Should().BeTrue();
        result.IsStale.Should().BeTrue();
        handler.CallCount.Should().Be(1);
    }


    private sealed class StubHttpMessageHandler(
        string json,
        HttpStatusCode statusCode = HttpStatusCode.OK) : HttpMessageHandler
    {
        public int CallCount { get; private set; }

        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request,
            CancellationToken cancellationToken)
        {
            CallCount++;
            request.RequestUri.Should().Be(MapDataService.Endpoint);

            return Task.FromResult(
                new HttpResponseMessage(statusCode)
                {
                    Content = new StringContent(json, Encoding.UTF8, "application/json"),
                });
        }
    }

    private sealed class StubMapDataCache(MapDataCacheEntry? entry) : IMapDataCache
    {
        public MapDataCacheEntry? WrittenEntry { get; private set; }

        public Task<MapDataCacheEntry?> ReadAsync(
            CancellationToken cancellationToken = default) =>
            Task.FromResult(entry);

        public Task WriteAsync(
            MapDataCacheEntry cacheEntry,
            CancellationToken cancellationToken = default)
        {
            WrittenEntry = cacheEntry;
            return Task.CompletedTask;
        }
    }

    private sealed class TestTimeProvider(DateTimeOffset utcNow) : TimeProvider
    {
        public override DateTimeOffset GetUtcNow() => utcNow;
    }
}
