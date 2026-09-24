using System.Text.Json.Serialization;
using ApostasyMap.Api.Contracts;
using ApostasyMap.Api.SourceModels;

namespace ApostasyMap.Api.Json;

[JsonSourceGenerationOptions(
    PropertyNamingPolicy = JsonKnownNamingPolicy.CamelCase,
    GenerationMode = JsonSourceGenerationMode.Default)]
[JsonSerializable(typeof(SourceFeatureCollection))]
[JsonSerializable(typeof(PlaceDto))]
[JsonSerializable(typeof(PlaceDto[]))]
[JsonSerializable(typeof(RatingBreakdownDto))]
[JsonSerializable(typeof(DatasetStatusDto))]
[JsonSerializable(typeof(ApiError))]
internal partial class AppJsonSerializerContext : JsonSerializerContext;
