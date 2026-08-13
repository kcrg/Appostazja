using System.Text.Json.Serialization;

namespace Appostazja.Maui.Models;

internal sealed class FormDraft
{
    public string FullName { get; init; } = string.Empty;

    public DateTime BaptismDate { get; init; } = DateTime.Today;

    public string BaptismParish { get; init; } = string.Empty;

    public string ResidenceParish { get; init; } = string.Empty;

    public string HomeAddress { get; init; } = string.Empty;

    public string Motivation { get; init; } = string.Empty;
}

[JsonSerializable(typeof(FormDraft))]
[JsonSourceGenerationOptions(
    PropertyNameCaseInsensitive = false,
    GenerationMode = JsonSourceGenerationMode.Metadata)]
internal sealed partial class FormDraftJsonContext : JsonSerializerContext;
