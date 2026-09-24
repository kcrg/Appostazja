using System.Text;
using Appostazja.Core.Pdf;
using FluentAssertions;

namespace Appostazja.Core.Tests;

[TestFixture]
public sealed class PdfDeclarationGeneratorTests
{
    private static readonly ApostasyDeclaration Declaration = new(
        "Jan Łącki",
        "ul. Świętojańska 1, 00-001 Warszawa",
        new DateOnly(1990, 5, 12),
        "Parafia św. Józefa w Łodzi",
        "Parafia miejsca zamieszkania w Warszawie",
        "Nie podzielam doktryny Kościoła i nie chcę należeć do tej wspólnoty.",
        new DateOnly(2026, 8, 12));

    [Test]
    public void Generate_ShouldCreatePdfWithEmbeddedFontAndUnicodeMap()
    {
        byte[] font = File.ReadAllBytes(
            Path.Combine(TestContext.CurrentContext.TestDirectory, "Nunito-Regular.ttf"));
        using var output = new MemoryStream();

        new PdfDeclarationGenerator().Generate(Declaration, font, output);

        byte[] pdf = output.ToArray();
        Encoding.ASCII.GetString(pdf.AsSpan(0, 8)).Should().StartWith("%PDF-1.7");
        Encoding.ASCII.GetString(pdf).Should().Contain("/ToUnicode");
        Encoding.ASCII.GetString(pdf).Should().EndWith("%%EOF\n");
        pdf.Length.Should().BeGreaterThan(font.Length);
    }

    [Test]
    public void Generate_ShouldRejectMissingRequiredData()
    {
        byte[] font = File.ReadAllBytes(
            Path.Combine(TestContext.CurrentContext.TestDirectory, "Nunito-Regular.ttf"));
        ApostasyDeclaration invalid = Declaration with { FullName = " " };

        Action action = () =>
            new PdfDeclarationGenerator().Generate(invalid, font, new MemoryStream());

        action.Should().Throw<ArgumentException>()
            .WithParameterName(nameof(ApostasyDeclaration.FullName));
    }
}
