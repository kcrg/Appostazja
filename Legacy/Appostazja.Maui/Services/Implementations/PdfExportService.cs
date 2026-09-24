using Appostazja.Core.Pdf;

namespace Appostazja.Maui.Services.Implementations;

public sealed class PdfExportService(IPdfDeclarationGenerator generator) : IPdfExportService
{
    public const string FileName = "deklaracja-apostazji.pdf";

    public async Task ExportAsync(
        ApostasyDeclaration declaration,
        CancellationToken cancellationToken = default)
    {
        await using Stream fontStream = await FileSystem.Current
            .OpenAppPackageFileAsync("PdfNunito-Regular.ttf")
            .ConfigureAwait(false);
        using var fontBuffer = new MemoryStream();
        await fontStream.CopyToAsync(fontBuffer, cancellationToken).ConfigureAwait(false);

        string path = Path.Combine(FileSystem.Current.CacheDirectory, FileName);
        await using (FileStream destination = File.Create(path))
        {
            generator.Generate(declaration, fontBuffer.ToArray(), destination);
            await destination.FlushAsync(cancellationToken).ConfigureAwait(false);
        }

        await Share.Default.RequestAsync(
            new ShareFileRequest(
                "Deklaracja apostazji",
                new ShareFile(path, "application/pdf")));
    }
}
