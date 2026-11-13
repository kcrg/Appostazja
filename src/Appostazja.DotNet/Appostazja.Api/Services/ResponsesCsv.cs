using System.Text;
using System.Text.RegularExpressions;
using Microsoft.VisualBasic.FileIO;

namespace ApostasyMap.Api.Services;

/// <summary>
/// Reduces the upstream responses CSV to the columns this API publishes and scrubs contact details from comments.
/// Only the sanitized form is stored or hashed, so submission timestamps and demographic columns never reach SQLite.
/// </summary>
internal static partial class ResponsesCsv
{
    internal const string Name = "church_name";
    internal const string Address = "church_address";
    internal const string Website = "church_website";
    internal const string Stage = "action_type";
    internal const string Rating = "action_rating";
    internal const string Date = "action_date";
    internal const string Comment = "action_comment";

    private static readonly string[] Columns = [Name, Address, Website, Stage, Rating, Date, Comment];
    private const int WebsiteIndex = 2;
    private const int CommentIndex = 6;

    /// <summary>
    /// Returns a CSV with exactly <see cref="Columns"/>. Rows that cannot be read are kept as empty rows so the
    /// parser still counts them as skipped. Sanitizing an already sanitized CSV returns the same bytes.
    /// </summary>
    public static byte[] Sanitize(byte[] payload)
    {
        using var csv = OpenParser(payload);
        var output = new StringBuilder(payload.Length / 2);

        try
        {
            var headers = csv.ReadFields() ?? throw new InvalidDataException("The response CSV has no header.");
            headers[0] = headers[0].TrimStart('﻿');
            var indexes = Columns.Select(column => FindColumn(headers, column)).ToArray();
            AppendRow(output, Columns);

            var projected = new string[Columns.Length];
            while (!csv.EndOfData)
            {
                string[]? fields;
                try
                {
                    fields = csv.ReadFields();
                }
                catch (MalformedLineException)
                {
                    fields = null;
                }

                if (fields is null || fields.Length != headers.Length)
                {
                    Array.Fill(projected, string.Empty);
                }
                else
                {
                    for (var i = 0; i < indexes.Length; i++)
                    {
                        projected[i] = fields[indexes[i]];
                    }

                    projected[CommentIndex] = ScrubContactDetails(projected[CommentIndex]);
                    // Some respondents put a private e-mail into the website field; such values are never published.
                    projected[WebsiteIndex] = ScrubContactDetails(projected[WebsiteIndex]);
                }

                AppendRow(output, projected);
            }
        }
        catch (DecoderFallbackException)
        {
            throw new InvalidDataException("The response CSV is not valid UTF-8.");
        }

        return Encoding.UTF8.GetBytes(output.ToString());
    }

    internal static TextFieldParser OpenParser(byte[] payload) =>
        new(new MemoryStream(payload, writable: false), new UTF8Encoding(false, true), detectEncoding: false)
        {
            Delimiters = [","],
            HasFieldsEnclosedInQuotes = true,
            TrimWhiteSpace = false
        };

    internal static int FindColumn(string[] headers, string name)
    {
        var index = Array.IndexOf(headers, name);
        if (index < 0 || index != Array.LastIndexOf(headers, name))
        {
            throw new InvalidDataException($"The response CSV must contain one '{name}' column.");
        }

        return index;
    }

    /// <summary>Masks e-mail addresses and phone-like numbers (nine or more digits, e.g. PESEL too).</summary>
    internal static string ScrubContactDetails(string text)
    {
        if (text.Length == 0)
        {
            return text;
        }

        text = Email().Replace(text, "[e-mail ukryty]");
        return Phone().Replace(text, "[numer ukryty]");
    }

    private static void AppendRow(StringBuilder output, string[] fields)
    {
        for (var i = 0; i < fields.Length; i++)
        {
            if (i > 0)
            {
                output.Append(',');
            }

            output.Append('"').Append(fields[i].Replace("\"", "\"\"", StringComparison.Ordinal)).Append('"');
        }

        output.Append('\n');
    }

    [GeneratedRegex(@"[\p{L}\p{N}._%+-]+@[\p{L}\p{N}-]+(?:\.[\p{L}\p{N}-]+)*\.\p{L}{2,}", RegexOptions.CultureInvariant)]
    private static partial Regex Email();

    // Nine-digit Polish numbers grouped 3-3-3, 3-2-2-2 or (2) 3-2-2, optionally with +48, and 11-digit PESEL.
    // Dates and times (4-2-2 …) do not fit these shapes.
    [GeneratedRegex(
        @"(?<![\d+])(?:(?:\+48[ .-]?)?(?:\d{3}[ .-]?\d{3}[ .-]?\d{3}|\d{3}[ .-]?\d{2}[ .-]?\d{2}[ .-]?\d{2}|\(?\d{2}\)?[ .-]?\d{3}[ .-]?\d{2}[ .-]?\d{2})|\d{11})(?!\d)",
        RegexOptions.CultureInvariant)]
    private static partial Regex Phone();
}
