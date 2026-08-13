using System.Text.Json;
using Appostazja.Maui.Models;
using Appostazja.Core.Pdf;
using Microsoft.Extensions.Logging;

namespace Appostazja.Maui.ViewModels;

public sealed partial class FormViewModel(
    IPdfExportService pdfExportService,
    ISecureStorage secureStorage,
    ILogger<FormViewModel> logger) : BaseViewModel
{
    private const string DraftKey = "apostasy-declaration-draft-v1";
    private static readonly TimeSpan SaveDebounce = TimeSpan.FromMilliseconds(600);
    private readonly SemaphoreSlim draftLock = new(1, 1);
    private CancellationTokenSource? draftSaveCts;
    private bool draftLoaded;
    private bool isRestoringDraft;

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

    partial void OnFullNameChanged(string value) => ScheduleDraftSave();

    partial void OnBaptismDateChanged(DateTime value) => ScheduleDraftSave();

    partial void OnBaptismParishChanged(string value) => ScheduleDraftSave();

    partial void OnResidenceParishChanged(string value) => ScheduleDraftSave();

    partial void OnHomeAddressChanged(string value) => ScheduleDraftSave();

    partial void OnMotivationChanged(string value) => ScheduleDraftSave();

    public override async Task OnNavigatedToAsync()
    {
        CurrentState = null;
        if (draftLoaded)
        {
            return;
        }

        isRestoringDraft = true;
        try
        {
            string? json = await secureStorage.GetAsync(DraftKey);
            FormDraft? draft = string.IsNullOrWhiteSpace(json)
                ? null
                : JsonSerializer.Deserialize(json, FormDraftJsonContext.Default.FormDraft);

            if (draft is not null)
            {
                ApplyDraft(draft);
            }
        }
        catch (Exception exception)
        {
            logger.LogWarning(exception, "Restoring the declaration draft failed.");
            secureStorage.Remove(DraftKey);
        }
        finally
        {
            isRestoringDraft = false;
            draftLoaded = true;
        }
    }

    public Task SaveDraftNowAsync()
    {
        CancelScheduledSave();
        return draftLoaded ? SaveDraftAsync() : Task.CompletedTask;
    }

    private void ScheduleDraftSave()
    {
        if (!draftLoaded || isRestoringDraft)
        {
            return;
        }

        CancelScheduledSave();
        var cancellation = new CancellationTokenSource();
        draftSaveCts = cancellation;
        _ = SaveDraftAfterDelayAsync(cancellation);
    }

    private void CancelScheduledSave()
    {
        CancellationTokenSource? cancellation = draftSaveCts;
        draftSaveCts = null;
        cancellation?.Cancel();
    }

    private async Task SaveDraftAfterDelayAsync(
        CancellationTokenSource cancellation)
    {
        try
        {
            await Task.Delay(SaveDebounce, cancellation.Token);
            await SaveDraftAsync();
        }
        catch (OperationCanceledException) when (cancellation.IsCancellationRequested)
        {
        }
        finally
        {
            if (ReferenceEquals(draftSaveCts, cancellation))
            {
                draftSaveCts = null;
            }

            cancellation.Dispose();
        }
    }

    private async Task SaveDraftAsync()
    {
        var draft = new FormDraft
        {
            FullName = FullName,
            BaptismDate = BaptismDate,
            BaptismParish = BaptismParish,
            ResidenceParish = ResidenceParish,
            HomeAddress = HomeAddress,
            Motivation = Motivation,
        };

        await draftLock.WaitAsync();
        try
        {
            string json = JsonSerializer.Serialize(
                draft,
                FormDraftJsonContext.Default.FormDraft);
            await secureStorage.SetAsync(DraftKey, json);
        }
        catch (Exception exception)
        {
            logger.LogWarning(exception, "Saving the declaration draft failed.");
        }
        finally
        {
            draftLock.Release();
        }
    }

    private void ApplyDraft(FormDraft draft)
    {
        FullName = draft.FullName;
        BaptismDate = draft.BaptismDate.Date <= DateTime.Today
            ? draft.BaptismDate.Date
            : DateTime.Today;
        BaptismParish = draft.BaptismParish;
        ResidenceParish = draft.ResidenceParish;
        HomeAddress = draft.HomeAddress;
        Motivation = draft.Motivation;
    }

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
