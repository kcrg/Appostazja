using QuestPDF.Fluent;
using QuestPDF.Helpers;
using QuestPDF.Infrastructure;
using Colors = QuestPDF.Helpers.Colors;
namespace Appostazja.Maui.ViewModels;

public partial class FormViewModel : ObservableObject
{
    public const string NameLabel = "Imię i nazwisko:  ";
    public const string BaptismDateLabel = "Data chrztu:  ";
    public const string BaptismParishLabel = "Parafa chrztu:  ";
    public const string ResignationTitleLabel = "Rezygnacja z członkostwa w Kościele katolickim";
    public const string ResignationReasonLabel1 = "Na podstawie art. 53 par. 1 i 2 Konstytucji Rzeczypospolitej Polskiej oraz Uchwały nr 20/370/2015 Konferencji Episkopatu Polski niniejszym rezygnuję z członkostwa w Kościele rzymskokatolickim i proszę o dokonanie stosownej adnotacji w księdze ochrzczonych. Członkostwo w Kościele jest niezgodne z moim światopoglądem. Decyzję tę podejmuję z własnej i nieprzymuszonej woli. Znam konsekwencje z tym związane.";
    public const string ResignationReasonLabel2 = "Członkostwo w Kościele jest niezgodne z moim światopoglądem. Decyzję tę podejmuję z własnej i nieprzymuszonej woli. Znam konsekwencje z tym związane.";
    public const string ReceivedByLabel = "Otrzymują:";
    public const string BaptismSignatureLabel = "data, pieczęć parafi i podpis proboszcza";
    public const string SignatureLabel = "podpis osoby rezygnującej";
    public const string PdfFileName = "appostazja.pdf";
    public const string PdfFileLabel = "Deklaracja apostazji";

    public const string DateFormat = "dd.MM.yyyy";

    [ObservableProperty]
    private string? name;

    [ObservableProperty]
    private DateTime baptismDate;

    [ObservableProperty]
    private string? baptismParish;

    [ObservableProperty]
    private bool submittedInBaptismParish;

    [ObservableProperty]
    private string? homeAddress;

    [ObservableProperty]
    private string? reason;

    public FormViewModel()
    {
    }

    [RelayCommand]
    public async Task ExportDocument()
    {
        string pdfPath = Path.Combine(FileSystem.Current.AppDataDirectory, PdfFileName);

        Document.Create(container =>
        {
            _ = container.Page(page =>
            {
                page.Size(PageSizes.A4);
                page.Margin(2, Unit.Centimetre);
                page.PageColor(Colors.White);
                page.DefaultTextStyle(x => x.FontSize(12));

                _ = page.Header().AlignRight().Text(DateTime.Now.ToString(DateFormat));

                page.Content()
                    .PaddingVertical(1, Unit.Centimetre)
                    .Column(x =>
                    {
                        x.Item().AlignLeft().Text(x =>
                        {
                            _ = x.Span(NameLabel).Bold();
                            _ = x.Span(Name);
                        });
                        x.Item().AlignLeft().Text(x =>
                        {
                            _ = x.Span(BaptismDateLabel).Bold();
                            _ = x.Span(BaptismDate.ToString(DateFormat));
                        });
                        x.Item().AlignLeft().Text(x =>
                        {
                            _ = x.Span(BaptismParishLabel).Bold();
                            _ = x.Span(BaptismParish);
                        });

                        _ = x.Item().AlignRight().PaddingRight(28).PaddingTop(24).Text(BaptismParish);

                        _ = x.Item().AlignCenter().PaddingTop(22).Text(ResignationTitleLabel).SemiBold();

                        _ = x.Item().PaddingTop(18).Text(ResignationReasonLabel1);
                        _ = x.Item().PaddingTop(12).Text(ResignationReasonLabel2);

                        _ = x.Item().AlignLeft().PaddingTop(48).Text(ReceivedByLabel).Bold();
                        _ = x.Item().AlignLeft().Text($"- {BaptismParish}");
                        _ = x.Item().AlignLeft().Text($"- {Name}");

                        x.Item().AlignLeft().PaddingTop(64).Row(row =>
                        {
                            _ = row.RelativeItem().AlignLeft().Text(BaptismSignatureLabel);
                            _ = row.RelativeItem().AlignRight().Text(SignatureLabel);
                        });
                    });
            });
        })
        .GeneratePdf(pdfPath);

        _ = await Launcher.Default.OpenAsync(new OpenFileRequest(PdfFileLabel, new ReadOnlyFile(pdfPath)));
    }
}