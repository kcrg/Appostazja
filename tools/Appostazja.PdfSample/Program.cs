using Appostazja.Core.Pdf;

if (args.Length != 2)
{
    Console.Error.WriteLine("Usage: Appostazja.PdfSample <font.ttf> <output.pdf>");
    return 1;
}

byte[] font = await File.ReadAllBytesAsync(args[0]);
string? outputDirectory = Path.GetDirectoryName(Path.GetFullPath(args[1]));
if (outputDirectory is not null)
{
    Directory.CreateDirectory(outputDirectory);
}

var declaration = new ApostasyDeclaration(
    "Jan Łącki",
    "ul. Świętojańska 1, 00-001 Warszawa",
    new DateOnly(1990, 5, 12),
    "Parafia św. Józefa w Łodzi",
    "Parafia miejsca zamieszkania w Warszawie",
    "Nie podzielam doktryny Kościoła i nie chcę należeć do tej wspólnoty.",
    new DateOnly(2026, 8, 12));

await using FileStream output = File.Create(args[1]);
new PdfDeclarationGenerator().Generate(declaration, font, output);
return 0;
