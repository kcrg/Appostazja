using Appostazja.Maui.Controls.Map;
using Appostazja.Maui.Models;
using Appostazja.Maui.Models.Map;
using RestSharp;
using RestSharp.Serializers.Json;
using System.Text.Json;

namespace Appostazja.Maui.Services.Implementations;

public class DataService : IDataService
{
    private readonly IRestClient restClient;

    private MapResponseModel? responseCache;

    public DataService(IRestClient restClient)
    {
        this.restClient = restClient;

        //restClient = new RestClient("https://mapaapostazji.pl/", configureSerialization: s =>
        //    s.UseSystemTextJson(new JsonSerializerOptions { TypeInfoResolver = SourceGeneratorJsonContext.Default }));
    }

    public async Task<MapResponseModel?> GetGeoJsonAsync()
    {
        if (responseCache is not null)
        {
            return responseCache;
        }

        RestRequest restRequest = new("https://mapaapostazji.pl/generated.geojson");
        //_ = restRequest.AddHeader("Accept", "text/plain");

        RestResponse<MapResponseModel> result = await restClient.ExecuteGetAsync<MapResponseModel>(restRequest);

        if (result.IsSuccessful)
        {
            responseCache = result.Data;
        }

        return responseCache;
    }
}
