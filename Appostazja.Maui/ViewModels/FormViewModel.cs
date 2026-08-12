using Appostazja.Core.Pdf;
using Microsoft.Extensions.Logging;

namespace Appostazja.Maui.ViewModels;

public sealed partial class FormViewModel(
    IPdfExportService pdfExportService,
    ILogger<FormViewModel> logger) : ObservableObject
{
    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(ExportDocumentCommand))]
    public partial string FullName { get; set; } = string.Empty;

    [ObservableProperty]
    public partial DateTime BaptismDate { get; set; } = DateTime.Today;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(ExportDocumentCommand))]
    public partial string BaptismParish { get; set; } = string.Empty;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(ExportDocumentCommand))]
    public partial string ResidenceParish { get; set; } = string.Empty;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(ExportDocumentCommand))]
    public partial string HomeAddress { get; set; } = string.Empty;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(ExportDocumentCommand))]
    public partial string Motivation { get; set; } = string.Empty;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(ExportDocumentCommand))]
    public partial bool IsBusy { get; set; }

    [ObservableProperty]
    public partial string? ErrorMessage { get; set; }

    private bool CanExportDocument() =>
        !IsBusy &&
        !string.IsNullOrWhiteSpace(FullName) &&
        !string.IsNullOrWhiteSpace(HomeAddress) &&
        !string.IsNullOrWhiteSpace(BaptismParish) &&
        !string.IsNullOrWhiteSpace(ResidenceParish) &&
        !string.IsNullOrWhiteSpace(Motivation);

    [RelayCommand(CanExecute = nameof(CanExportDocument))]
    private async Task ExportDocumentAsync(CancellationToken cancellationToken)
    {
        IsBusy = true;
        ErrorMessage = null;

        try
        {
            var declaration = new ApostasyDeclaration(
                FullName.Trim(),
                HomeAddress.Trim(),
                DateOnly.FromDateTime(BaptismDate),
                BaptismParish.Trim(),
                ResidenceParish.Trim(),
                Motivation.Trim(),
                DateOnly.FromDateTime(DateTime.Today));

            await pdfExportService.ExportAsync(declaration, cancellationToken);
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
        }
        catch (Exception exception)
        {
            logger.LogError(exception, "Generating the apostasy declaration failed.");
            ErrorMessage = "Nie udało się utworzyć PDF. Sprawdź dane i spróbuj ponownie.";
        }
        finally
        {
            IsBusy = false;
        }
    }
}
