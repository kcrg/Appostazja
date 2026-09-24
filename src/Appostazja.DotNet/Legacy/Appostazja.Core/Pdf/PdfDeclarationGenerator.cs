using System.Buffers.Binary;
using System.Globalization;
using System.Text;

namespace Appostazja.Core.Pdf;

public sealed class PdfDeclarationGenerator : IPdfDeclarationGenerator
{
    private const float PageWidth = 595.28f;
    private const float PageHeight = 841.89f;
    private const float Margin = 56.69f;

    public void Generate(
        ApostasyDeclaration declaration,
        ReadOnlyMemory<byte> trueTypeFont,
        Stream destination)
    {
        ArgumentNullException.ThrowIfNull(declaration);
        ArgumentNullException.ThrowIfNull(destination);

        if (!destination.CanWrite)
        {
            throw new ArgumentException("The PDF destination must be writable.", nameof(destination));
        }

        Validate(declaration);
        var font = new TrueTypeFont(trueTypeFont);
        var document = new LayoutDocument(font);

        document.Right(
            declaration.DeclarationDate.ToString("dd.MM.yyyy", CultureInfo.InvariantCulture),
            10);
        document.Space(10);
        document.Left(declaration.FullName, 11);
        document.Left(declaration.HomeAddress, 11);
        document.Space(20);

        document.Right("Proboszcz parafii miejsca zamieszkania", 10);
        document.Right(declaration.ResidenceParish, 11);
        document.Space(34);

        document.Center("OŚWIADCZENIE WOLI", 15);
        document.Center("O WYSTĄPIENIU Z KOŚCIOŁA KATOLICKIEGO", 15);
        document.Space(26);

        document.Paragraph(
            "Ja, niżej podpisany/a, oświadczam, że w sposób świadomy, dobrowolny i wolny " +
            "występuję ze wspólnoty Kościoła katolickiego. Proszę o przeprowadzenie czynności " +
            "przewidzianych w Dekrecie Ogólnym Konferencji Episkopatu Polski w sprawie wystąpień " +
            "z Kościoła oraz powrotu do wspólnoty Kościoła, obowiązującym od 19 lutego 2016 r., " +
            "oraz o dokonanie stosownej adnotacji w księdze ochrzczonych.",
            11);

        document.Space(12);
        document.Left($"Data chrztu: {declaration.BaptismDate:dd.MM.yyyy}", 11);
        document.Paragraph($"Parafia chrztu: {declaration.BaptismParish}", 11);
        document.Space(14);

        document.Paragraph("Motywacja:", 11);
        document.Paragraph(declaration.Motivation, 11);
        document.Space(14);

        document.Paragraph(
            "Decyzję podejmuję bez przymusu i ze świadomością konsekwencji, jakie pociąga za " +
            "sobą ten akt. Moja wola zerwania wspólnoty z Kościołem jest jednoznaczna.",
            11);

        document.Space(20);
        document.Paragraph(
            "Jeżeli chrzest odbył się w innej parafii, do oświadczenia należy dołączyć " +
            "aktualne świadectwo chrztu.",
            9);

        document.Signature("czytelny, własnoręczny podpis");
        document.AddPageNumbers();

        PdfFile.Write(destination, font, document.Pages);
    }

    private static void Validate(ApostasyDeclaration declaration)
    {
        ValidateRequired(declaration.FullName, nameof(declaration.FullName), 200);
        ValidateRequired(declaration.HomeAddress, nameof(declaration.HomeAddress), 300);
        ValidateRequired(declaration.BaptismParish, nameof(declaration.BaptismParish), 300);
        ValidateRequired(declaration.ResidenceParish, nameof(declaration.ResidenceParish), 300);
        ValidateRequired(declaration.Motivation, nameof(declaration.Motivation), 2_000);

        if (declaration.BaptismDate > declaration.DeclarationDate)
        {
            throw new ArgumentException("Baptism date cannot be later than declaration date.");
        }
    }

    private static void ValidateRequired(string value, string name, int maximumLength)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            throw new ArgumentException("Value is required.", name);
        }

        if (value.Length > maximumLength)
        {
            throw new ArgumentException(
                $"Value cannot be longer than {maximumLength} characters.",
                name);
        }
    }

    private sealed class LayoutDocument(TrueTypeFont font)
    {
        private const float ContentBottom = 72;
        private const float DefaultLeading = 15;

        private readonly TrueTypeFont font = font;
        private float y = PageHeight - Margin;

        public List<PageCanvas> Pages { get; } = [new()];

        private PageCanvas CurrentPage => Pages[^1];

        public void Space(float points) => y -= points;

        public void Left(string text, float size) =>
            WriteWrapped(text, size, DefaultLeading, TextAlignment.Left);

        public void Right(string text, float size) =>
            WriteWrapped(text, size, DefaultLeading, TextAlignment.Right);

        public void Center(string text, float size) =>
            WriteWrapped(text, size, size + 4, TextAlignment.Center);

        public void Paragraph(string text, float size) =>
            WriteWrapped(text, size, DefaultLeading, TextAlignment.Left);

        public void Signature(string label)
        {
            EnsureSpace(100);
            y -= 56;

            const float lineWidth = 190;
            float startX = PageWidth - Margin - lineWidth;
            CurrentPage.DrawLine(startX, y, startX + lineWidth, y);
            y -= 14;
            CurrentPage.DrawText(font, label, 8, startX, y, TextAlignment.Left);
        }

        public void AddPageNumbers()
        {
            for (int index = 0; index < Pages.Count; index++)
            {
                Pages[index].DrawText(
                    font,
                    $"Strona {index + 1} z {Pages.Count}",
                    8,
                    PageWidth / 2,
                    28,
                    TextAlignment.Center);
            }
        }

        private void WriteWrapped(
            string text,
            float size,
            float leading,
            TextAlignment alignment)
        {
            float availableWidth = PageWidth - (2 * Margin);
            foreach (string paragraph in NormalizeLineEndings(text).Split('\n'))
            {
                IReadOnlyList<string> lines = font.Wrap(paragraph, size, availableWidth);
                foreach (string line in lines)
                {
                    EnsureSpace(leading);

                    float x = alignment switch
                    {
                        TextAlignment.Left => Margin,
                        TextAlignment.Center => PageWidth / 2,
                        TextAlignment.Right => PageWidth - Margin,
                        _ => throw new ArgumentOutOfRangeException(nameof(alignment)),
                    };

                    CurrentPage.DrawText(font, line, size, x, y, alignment);
                    y -= leading;
                }
            }
        }

        private void EnsureSpace(float required)
        {
            if (y - required >= ContentBottom)
            {
                return;
            }

            Pages.Add(new PageCanvas());
            y = PageHeight - Margin;
        }

        private static string NormalizeLineEndings(string value) =>
            value.Replace("\r\n", "\n", StringComparison.Ordinal)
                .Replace('\r', '\n');
    }

    private sealed class PageCanvas
    {
        private readonly StringBuilder content = new();

        public byte[] GetContent() => Encoding.ASCII.GetBytes(content.ToString());

        public void DrawText(
            TrueTypeFont font,
            string text,
            float size,
            float anchorX,
            float y,
            TextAlignment alignment)
        {
            float width = font.Measure(text, size);
            float x = alignment switch
            {
                TextAlignment.Left => anchorX,
                TextAlignment.Center => anchorX - (width / 2),
                TextAlignment.Right => anchorX - width,
                _ => throw new ArgumentOutOfRangeException(nameof(alignment)),
            };

            content.Append("BT /F1 ")
                .Append(PdfNumber(size))
                .Append(" Tf 0 g 1 0 0 1 ")
                .Append(PdfNumber(x))
                .Append(' ')
                .Append(PdfNumber(y))
                .Append(" Tm <")
                .Append(font.Encode(text))
                .Append("> Tj ET\n");
        }

        public void DrawLine(float x1, float y1, float x2, float y2)
        {
            content.Append("0.7 w ")
                .Append(PdfNumber(x1))
                .Append(' ')
                .Append(PdfNumber(y1))
                .Append(" m ")
                .Append(PdfNumber(x2))
                .Append(' ')
                .Append(PdfNumber(y2))
                .Append(" l S\n");
        }
    }

    private sealed class TrueTypeFont
    {
        private readonly Dictionary<string, Table> tables;
        private readonly Dictionary<ushort, int> usedGlyphs = [];
        private readonly ushort[] advanceWidths;
        private readonly Cmap cmap;

        public TrueTypeFont(ReadOnlyMemory<byte> data)
        {
            if (data.Length < 12)
            {
                throw new ArgumentException("The TrueType font is invalid.", nameof(data));
            }

            Data = data;
            ReadOnlySpan<byte> bytes = data.Span;
            ushort tableCount = ReadUInt16(bytes, 4);
            tables = new Dictionary<string, Table>(tableCount, StringComparer.Ordinal);

            for (int index = 0; index < tableCount; index++)
            {
                int directoryOffset = 12 + (index * 16);
                string tag = Encoding.ASCII.GetString(bytes.Slice(directoryOffset, 4));
                int offset = checked((int)ReadUInt32(bytes, directoryOffset + 8));
                int length = checked((int)ReadUInt32(bytes, directoryOffset + 12));
                EnsureRange(bytes, offset, length);
                tables[tag] = new Table(offset, length);
            }

            Table head = GetTable("head");
            UnitsPerEm = ReadUInt16(bytes, head.Offset + 18);
            FontBounds = new FontBounds(
                ReadInt16(bytes, head.Offset + 36),
                ReadInt16(bytes, head.Offset + 38),
                ReadInt16(bytes, head.Offset + 40),
                ReadInt16(bytes, head.Offset + 42));

            Table hhea = GetTable("hhea");
            Ascent = ScaleToPdf(ReadInt16(bytes, hhea.Offset + 4));
            Descent = ScaleToPdf(ReadInt16(bytes, hhea.Offset + 6));
            ushort horizontalMetricCount = ReadUInt16(bytes, hhea.Offset + 34);

            Table maxp = GetTable("maxp");
            ushort glyphCount = ReadUInt16(bytes, maxp.Offset + 4);
            if (horizontalMetricCount == 0 || horizontalMetricCount > glyphCount)
            {
                throw new ArgumentException("The TrueType horizontal metrics are invalid.", nameof(data));
            }

            Table hmtx = GetTable("hmtx");
            advanceWidths = new ushort[glyphCount];
            ushort lastWidth = 0;
            for (int glyph = 0; glyph < glyphCount; glyph++)
            {
                if (glyph < horizontalMetricCount)
                {
                    lastWidth = ReadUInt16(bytes, hmtx.Offset + (glyph * 4));
                }

                advanceWidths[glyph] = lastWidth;
            }

            cmap = Cmap.Create(bytes, GetTable("cmap"));
        }

        public ushort UnitsPerEm { get; }

        public int Ascent { get; }

        public int Descent { get; }

        public FontBounds FontBounds { get; }

        public ReadOnlyMemory<byte> Data { get; }

        public IReadOnlyDictionary<ushort, int> UsedGlyphs => usedGlyphs;

        public float Measure(string text, float fontSize)
        {
            long width = 0;
            foreach (Rune rune in text.EnumerateRunes())
            {
                ushort glyph = GetGlyph(rune);
                width += advanceWidths[glyph];
            }

            return width * fontSize / UnitsPerEm;
        }

        public IReadOnlyList<string> Wrap(string text, float fontSize, float maximumWidth)
        {
            if (string.IsNullOrWhiteSpace(text))
            {
                return [string.Empty];
            }

            var lines = new List<string>();
            var current = new StringBuilder();

            foreach (string word in text.Split(' ', StringSplitOptions.RemoveEmptyEntries))
            {
                string candidate = current.Length == 0 ? word : $"{current} {word}";
                if (Measure(candidate, fontSize) <= maximumWidth)
                {
                    current.Clear();
                    current.Append(candidate);
                    continue;
                }

                if (current.Length > 0)
                {
                    lines.Add(current.ToString());
                    current.Clear();
                }

                if (Measure(word, fontSize) <= maximumWidth)
                {
                    current.Append(word);
                    continue;
                }

                foreach (Rune rune in word.EnumerateRunes())
                {
                    string candidatePart = current.ToString() + rune;
                    if (current.Length > 0 && Measure(candidatePart, fontSize) > maximumWidth)
                    {
                        lines.Add(current.ToString());
                        current.Clear();
                    }

                    current.Append(rune);
                }
            }

            if (current.Length > 0)
            {
                lines.Add(current.ToString());
            }

            return lines;
        }

        public string Encode(string text)
        {
            var encoded = new StringBuilder(text.Length * 4);
            foreach (Rune rune in text.EnumerateRunes())
            {
                ushort glyph = GetGlyph(rune);
                usedGlyphs.TryAdd(glyph, rune.Value);
                encoded.Append(glyph.ToString("X4", CultureInfo.InvariantCulture));
            }

            return encoded.ToString();
        }

        public int GetPdfWidth(ushort glyph) =>
            (int)Math.Round(advanceWidths[glyph] * 1000d / UnitsPerEm);

        public int ScaleToPdf(int fontUnits) =>
            (int)Math.Round(fontUnits * 1000d / UnitsPerEm);

        private ushort GetGlyph(Rune rune)
        {
            ushort glyph = cmap.GetGlyph(rune.Value);
            if (glyph == 0 && rune.Value != 0)
            {
                throw new InvalidOperationException(
                    $"The embedded font does not contain U+{rune.Value:X4}.");
            }

            if (glyph >= advanceWidths.Length)
            {
                throw new InvalidOperationException("The font returned an invalid glyph index.");
            }

            return glyph;
        }

        private Table GetTable(string tag) =>
            tables.TryGetValue(tag, out Table table)
                ? table
                : throw new ArgumentException($"The TrueType font has no '{tag}' table.");

        private static ushort ReadUInt16(ReadOnlySpan<byte> bytes, int offset)
        {
            EnsureRange(bytes, offset, 2);
            return BinaryPrimitives.ReadUInt16BigEndian(bytes[offset..]);
        }

        private static short ReadInt16(ReadOnlySpan<byte> bytes, int offset)
        {
            EnsureRange(bytes, offset, 2);
            return BinaryPrimitives.ReadInt16BigEndian(bytes[offset..]);
        }

        private static uint ReadUInt32(ReadOnlySpan<byte> bytes, int offset)
        {
            EnsureRange(bytes, offset, 4);
            return BinaryPrimitives.ReadUInt32BigEndian(bytes[offset..]);
        }

        private static void EnsureRange(ReadOnlySpan<byte> bytes, int offset, int length)
        {
            if (offset < 0 || length < 0 || offset > bytes.Length - length)
            {
                throw new ArgumentException("The TrueType font contains an invalid table offset.");
            }
        }

        private readonly record struct Table(int Offset, int Length);

        private abstract class Cmap
        {
            public abstract ushort GetGlyph(int unicode);

            public static Cmap Create(ReadOnlySpan<byte> bytes, Table cmapTable)
            {
                int count = ReadUInt16(bytes, cmapTable.Offset + 2);
                int? format12Offset = null;
                int? format4Offset = null;

                for (int index = 0; index < count; index++)
                {
                    int recordOffset = cmapTable.Offset + 4 + (index * 8);
                    int subtableOffset = checked(
                        cmapTable.Offset + (int)ReadUInt32(bytes, recordOffset + 4));
                    ushort format = ReadUInt16(bytes, subtableOffset);
                    if (format == 12)
                    {
                        format12Offset ??= subtableOffset;
                    }
                    else if (format == 4)
                    {
                        format4Offset ??= subtableOffset;
                    }
                }

                if (format12Offset is int offset12)
                {
                    return new CmapFormat12(bytes, offset12);
                }

                if (format4Offset is int offset4)
                {
                    return new CmapFormat4(bytes, offset4);
                }

                throw new ArgumentException("The TrueType font has no supported Unicode cmap.");
            }
        }

        private sealed class CmapFormat12 : Cmap
        {
            private readonly Group[] groups;

            public CmapFormat12(ReadOnlySpan<byte> bytes, int offset)
            {
                int groupCount = checked((int)ReadUInt32(bytes, offset + 12));
                groups = new Group[groupCount];
                for (int index = 0; index < groupCount; index++)
                {
                    int groupOffset = offset + 16 + (index * 12);
                    groups[index] = new Group(
                        ReadUInt32(bytes, groupOffset),
                        ReadUInt32(bytes, groupOffset + 4),
                        ReadUInt32(bytes, groupOffset + 8));
                }
            }

            public override ushort GetGlyph(int unicode)
            {
                uint value = checked((uint)unicode);
                int low = 0;
                int high = groups.Length - 1;

                while (low <= high)
                {
                    int middle = low + ((high - low) / 2);
                    Group group = groups[middle];
                    if (value < group.Start)
                    {
                        high = middle - 1;
                    }
                    else if (value > group.End)
                    {
                        low = middle + 1;
                    }
                    else
                    {
                        return checked((ushort)(group.StartGlyph + value - group.Start));
                    }
                }

                return 0;
            }

            private readonly record struct Group(uint Start, uint End, uint StartGlyph);
        }

        private sealed class CmapFormat4 : Cmap
        {
            private readonly byte[] bytes;
            private readonly int segmentCount;
            private readonly int endCodesOffset;
            private readonly int startCodesOffset;
            private readonly int deltasOffset;
            private readonly int rangeOffsetsOffset;

            public CmapFormat4(ReadOnlySpan<byte> fontBytes, int offset)
            {
                int length = ReadUInt16(fontBytes, offset + 2);
                bytes = fontBytes.Slice(offset, length).ToArray();
                segmentCount = ReadUInt16(bytes, 6) / 2;
                endCodesOffset = 14;
                startCodesOffset = endCodesOffset + (segmentCount * 2) + 2;
                deltasOffset = startCodesOffset + (segmentCount * 2);
                rangeOffsetsOffset = deltasOffset + (segmentCount * 2);
            }

            public override ushort GetGlyph(int unicode)
            {
                if ((uint)unicode > ushort.MaxValue)
                {
                    return 0;
                }

                ushort code = (ushort)unicode;
                for (int index = 0; index < segmentCount; index++)
                {
                    ushort end = ReadUInt16(bytes, endCodesOffset + (index * 2));
                    if (code > end)
                    {
                        continue;
                    }

                    ushort start = ReadUInt16(bytes, startCodesOffset + (index * 2));
                    if (code < start)
                    {
                        return 0;
                    }

                    short delta = ReadInt16(bytes, deltasOffset + (index * 2));
                    int rangePosition = rangeOffsetsOffset + (index * 2);
                    ushort rangeOffset = ReadUInt16(bytes, rangePosition);
                    if (rangeOffset == 0)
                    {
                        return (ushort)((code + delta) & 0xFFFF);
                    }

                    int glyphPosition = rangePosition + rangeOffset + ((code - start) * 2);
                    ushort glyph = ReadUInt16(bytes, glyphPosition);
                    return glyph == 0 ? (ushort)0 : (ushort)((glyph + delta) & 0xFFFF);
                }

                return 0;
            }
        }
    }

    private static class PdfFile
    {
        public static void Write(
            Stream destination,
            TrueTypeFont font,
            IReadOnlyList<PageCanvas> pages)
        {
            const int catalogId = 1;
            const int pagesId = 2;
            const int fontId = 3;
            const int cidFontId = 4;
            const int descriptorId = 5;
            const int fontFileId = 6;
            const int toUnicodeId = 7;
            const int firstPageId = 8;

            int objectCount = 7 + (pages.Count * 2);
            var offsets = new long[objectCount + 1];

            WriteAscii(destination, "%PDF-1.7\n%");
            destination.Write([0xE2, 0xE3, 0xCF, 0xD3]);
            WriteAscii(destination, "\n");

            WriteObject(destination, offsets, catalogId, $"<< /Type /Catalog /Pages {pagesId} 0 R >>");

            string kids = string.Join(
                ' ',
                Enumerable.Range(0, pages.Count).Select(index => $"{firstPageId + (index * 2)} 0 R"));
            WriteObject(
                destination,
                offsets,
                pagesId,
                $"<< /Type /Pages /Count {pages.Count} /Kids [{kids}] >>");

            WriteObject(
                destination,
                offsets,
                fontId,
                $"<< /Type /Font /Subtype /Type0 /BaseFont /Nunito /Encoding /Identity-H " +
                $"/DescendantFonts [{cidFontId} 0 R] /ToUnicode {toUnicodeId} 0 R >>");

            string widths = string.Join(
                ' ',
                font.UsedGlyphs.Keys
                    .Order()
                    .Select(glyph => $"{glyph} [{font.GetPdfWidth(glyph)}]"));
            WriteObject(
                destination,
                offsets,
                cidFontId,
                $"<< /Type /Font /Subtype /CIDFontType2 /BaseFont /Nunito " +
                "/CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> " +
                $"/FontDescriptor {descriptorId} 0 R /CIDToGIDMap /Identity /DW 500 /W [{widths}] >>");

            FontBounds bounds = font.FontBounds;
            WriteObject(
                destination,
                offsets,
                descriptorId,
                $"<< /Type /FontDescriptor /FontName /Nunito /Flags 32 " +
                $"/FontBBox [{font.ScaleToPdf(bounds.XMin)} {font.ScaleToPdf(bounds.YMin)} " +
                $"{font.ScaleToPdf(bounds.XMax)} {font.ScaleToPdf(bounds.YMax)}] " +
                $"/ItalicAngle 0 /Ascent {font.Ascent} /Descent {font.Descent} " +
                $"/CapHeight {font.Ascent} /StemV 80 /FontFile2 {fontFileId} 0 R >>");

            WriteStreamObject(destination, offsets, fontFileId, font.Data.Span);
            WriteStreamObject(
                destination,
                offsets,
                toUnicodeId,
                Encoding.ASCII.GetBytes(BuildToUnicode(font.UsedGlyphs)));

            for (int index = 0; index < pages.Count; index++)
            {
                int pageId = firstPageId + (index * 2);
                int contentId = pageId + 1;
                WriteObject(
                    destination,
                    offsets,
                    pageId,
                    $"<< /Type /Page /Parent {pagesId} 0 R " +
                    $"/MediaBox [0 0 {PdfNumber(PageWidth)} {PdfNumber(PageHeight)}] " +
                    $"/Resources << /Font << /F1 {fontId} 0 R >> >> /Contents {contentId} 0 R >>");
                WriteStreamObject(destination, offsets, contentId, pages[index].GetContent());
            }

            long xrefOffset = destination.Position;
            WriteAscii(destination, $"xref\n0 {objectCount + 1}\n");
            WriteAscii(destination, "0000000000 65535 f \n");
            for (int id = 1; id <= objectCount; id++)
            {
                WriteAscii(
                    destination,
                    offsets[id].ToString("D10", CultureInfo.InvariantCulture) + " 00000 n \n");
            }

            WriteAscii(
                destination,
                $"trailer\n<< /Size {objectCount + 1} /Root {catalogId} 0 R >>\n" +
                $"startxref\n{xrefOffset}\n%%EOF\n");
        }

        private static string BuildToUnicode(IReadOnlyDictionary<ushort, int> glyphs)
        {
            var cmap = new StringBuilder(
                "/CIDInit /ProcSet findresource begin\n" +
                "12 dict begin\nbegincmap\n" +
                "/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n" +
                "/CMapName /Nunito-UCS def\n/CMapType 2 def\n" +
                "1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n");

            foreach (IReadOnlyList<KeyValuePair<ushort, int>> chunk in glyphs
                         .OrderBy(pair => pair.Key)
                         .Chunk(100))
            {
                cmap.Append(chunk.Count).Append(" beginbfchar\n");
                foreach ((ushort glyph, int unicode) in chunk)
                {
                    cmap.Append('<')
                        .Append(glyph.ToString("X4", CultureInfo.InvariantCulture))
                        .Append("> <")
                        .Append(ToUtf16Hex(unicode))
                        .Append(">\n");
                }

                cmap.Append("endbfchar\n");
            }

            cmap.Append("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n");
            return cmap.ToString();
        }

        private static string ToUtf16Hex(int unicode)
        {
            Span<char> characters = stackalloc char[2];
            int count = new Rune(unicode).EncodeToUtf16(characters);
            var result = new StringBuilder(count * 4);
            for (int index = 0; index < count; index++)
            {
                result.Append(((ushort)characters[index]).ToString("X4", CultureInfo.InvariantCulture));
            }

            return result.ToString();
        }

        private static void WriteObject(
            Stream destination,
            long[] offsets,
            int id,
            string value)
        {
            offsets[id] = destination.Position;
            WriteAscii(destination, $"{id} 0 obj\n{value}\nendobj\n");
        }

        private static void WriteStreamObject(
            Stream destination,
            long[] offsets,
            int id,
            ReadOnlySpan<byte> content)
        {
            offsets[id] = destination.Position;
            WriteAscii(destination, $"{id} 0 obj\n<< /Length {content.Length} >>\nstream\n");
            destination.Write(content);
            WriteAscii(destination, "\nendstream\nendobj\n");
        }

        private static void WriteAscii(Stream destination, string value) =>
            destination.Write(Encoding.ASCII.GetBytes(value));
    }

    private enum TextAlignment
    {
        Left,
        Center,
        Right,
    }

    private readonly record struct FontBounds(short XMin, short YMin, short XMax, short YMax);

    private static string PdfNumber(float value) =>
        value.ToString("0.##", CultureInfo.InvariantCulture);
}
