using System.Text.Json.Serialization;
using ApostasyMap.Api.Contracts;
using ApostasyMap.Api.SourceModels;
using ApostasyMap.Api.Services;

namespace ApostasyMap.Api.Json;

[JsonSourceGenerationOptions(
    PropertyNamingPolicy = JsonKnownNamingPolicy.CamelCase,
    GenerationMode = JsonSourceGenerationMode.Default)]
[JsonSerializable(typeof(SourceFeatureCollection))]
[JsonSerializable(typeof(PlaceDto))]
[JsonSerializable(typeof(PlaceDto[]))]
[JsonSerializable(typeof(RatingBreakdownDto))]
[JsonSerializable(typeof(StageRatingsDto))]
[JsonSerializable(typeof(PlaceReportsDto))]
[JsonSerializable(typeof(PlaceSearchResultDto))]
[JsonSerializable(typeof(StatsDto))]
[JsonSerializable(typeof(int?))]
[JsonSerializable(typeof(string[]))]
[JsonSerializable(typeof(DatasetStatusDto))]
[JsonSerializable(typeof(ApiError))]
[JsonSerializable(typeof(StreetViewDto))]
[JsonSerializable(typeof(StreetViewMetadata))]
[JsonSerializable(typeof(HealthDto))]
// Query/route parameter types, needed by the OpenAPI document generator under NativeAOT.
[JsonSerializable(typeof(double?))]
[JsonSerializable(typeof(bool))]
[JsonSerializable(typeof(string))]
internal partial class AppJsonSerializerContext : JsonSerializerContext;
