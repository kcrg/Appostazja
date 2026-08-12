namespace Appostazja.Core.Pdf;

public sealed record ApostasyDeclaration(
    string FullName,
    string HomeAddress,
    DateOnly BaptismDate,
    string BaptismParish,
    string ResidenceParish,
    string Motivation,
    DateOnly DeclarationDate);
