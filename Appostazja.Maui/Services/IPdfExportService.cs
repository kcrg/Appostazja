using Appostazja.Core.Pdf;

namespace Appostazja.Maui.Services;

public interface IPdfExportService
{
    Task ExportAsync(
        ApostasyDeclaration declaration,
        CancellationToken cancellationToken = default);
}
