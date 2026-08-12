namespace Appostazja.Core.Pdf;

public interface IPdfDeclarationGenerator
{
    void Generate(
        ApostasyDeclaration declaration,
        ReadOnlyMemory<byte> trueTypeFont,
        Stream destination);
}
