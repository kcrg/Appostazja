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

        first.Should().ContainSingle();
        first[0].Properties.Id.Should().Be("warszawa");
        first[0].Geometry.Coordinates.Should().Equal(21.0122, 52.2297);
        second.Should().BeSameAs(first);
        handler.CallCount.Should().Be(1);
    }

    private sealed class StubHttpMessageHandler(string json) : HttpMessageHandler
    {
        public int CallCount { get; private set; }

        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request,
            CancellationToken cancellationToken)
        {
            CallCount++;
            request.RequestUri.Should().Be(MapDataService.Endpoint);

            return Task.FromResult(
                new HttpResponseMessage(HttpStatusCode.OK)
                {
                    Content = new StringContent(json, Encoding.UTF8, "application/json"),
                });
        }
    }
}
