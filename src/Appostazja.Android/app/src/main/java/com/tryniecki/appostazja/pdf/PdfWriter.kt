package com.tryniecki.appostazja.pdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument

/**
 * Lays out A4 letters: a sender and an addressee block, a centred title, justified paragraphs, label–value
 * rows and a signature line. Text wraps within its column and flows onto new pages.
 */
internal class PdfWriter(private val document: PdfDocument) {
    private var pageNumber = 0
    private var page: PdfDocument.Page? = null
    private var y = MARGIN_TOP

    // A serif face reads as a formal letter; labels and captions are set apart in grey.
    private val regular = paint(Typeface.NORMAL, INK)
    private val bold = paint(Typeface.BOLD, INK)
    private val muted = paint(Typeface.NORMAL, MUTED)
    private val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MUTED
        strokeWidth = 0.6f
    }

    init { newPage() }

    fun space(points: Float) { y += points }

    /** The place and date, at the top right. */
    fun dateLine(text: String) {
        val paint = sized(regular, BODY_SIZE)
        ensureSpace(leading(BODY_SIZE))
        drawText(text, PAGE_WIDTH - MARGIN - paint.measureText(text), paint)
        y += leading(BODY_SIZE)
    }

    /** The sender's name and address, at the top left. */
    fun sender(name: String, lines: List<String>) {
        block(name, sized(bold, BODY_SIZE), MARGIN, CONTENT_WIDTH / 2)
        lines.filter { it.isNotBlank() }.forEach { block(it, sized(regular, BODY_SIZE), MARGIN, CONTENT_WIDTH / 2) }
    }

    /** Who the letter goes to: a caption over the name, in a column in the right half, left-aligned. */
    fun addressee(caption: String, name: String) {
        block(caption, sized(muted, CAPTION_SIZE), ADDRESSEE_X, ADDRESSEE_WIDTH)
        y += 2f
        block(name, sized(bold, BODY_SIZE), ADDRESSEE_X, ADDRESSEE_WIDTH)
    }

    /** A centred title, letter-spaced; each string is a line of its own. */
    fun title(vararg lines: String) {
        val paint = sized(bold, TITLE_SIZE).apply { letterSpacing = 0.06f }
        for (line in lines) {
            for (wrapped in wrap(line, paint, CONTENT_WIDTH)) {
                ensureSpace(leading(TITLE_SIZE))
                drawText(wrapped, (PAGE_WIDTH - paint.measureText(wrapped)) / 2f, paint)
                y += leading(TITLE_SIZE)
            }
        }
        paint.letterSpacing = 0f
    }

    /** Justified body text; blank lines in [text] separate paragraphs. */
    fun paragraph(text: String, bold: Boolean = false) {
        val paint = sized(if (bold) this.bold else regular, BODY_SIZE)
        val paragraphs = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        for ((index, paragraph) in paragraphs.withIndex()) {
            if (paragraph.isBlank()) {
                if (index != 0 && index != paragraphs.lastIndex) y += PARAGRAPH_GAP
                continue
            }
            val lines = wrap(paragraph, paint, CONTENT_WIDTH)
            for ((lineIndex, line) in lines.withIndex()) {
                ensureSpace(leading(BODY_SIZE))
                // The last line of a paragraph stays ragged, as in print.
                if (lineIndex == lines.lastIndex) drawText(line, MARGIN, paint) else drawJustified(line, paint)
                y += leading(BODY_SIZE)
            }
        }
    }

    /** A short bold heading for a part of the letter, kept with the line after it. */
    fun heading(text: String) {
        ensureSpace(leading(BODY_SIZE) * 2)
        drawText(text, MARGIN, sized(bold, BODY_SIZE))
        y += leading(BODY_SIZE)
    }

    /** "Label: value" rows as two columns sized to the longest label; long values wrap within theirs. */
    fun fields(rows: List<Pair<String, String>>) {
        val label = sized(muted, BODY_SIZE)
        val value = sized(regular, BODY_SIZE)
        val labelWidth = (rows.maxOfOrNull { label.measureText(it.first) } ?: 0f)
            .plus(LABEL_GAP)
            .coerceIn(MIN_LABEL_WIDTH, CONTENT_WIDTH * 0.45f)
        for ((name, text) in rows) {
            val lines = wrap(text, value, CONTENT_WIDTH - labelWidth)
            ensureSpace(leading(BODY_SIZE) * minOf(lines.size, 2))
            drawText(name, MARGIN, label)
            for ((index, line) in lines.withIndex()) {
                if (index > 0) ensureSpace(leading(BODY_SIZE))
                drawText(line, MARGIN + labelWidth, value)
                y += leading(BODY_SIZE)
            }
            y += 2f
        }
    }

    /** A small grey note, e.g. the list of attachments. */
    fun note(text: String) {
        block(text, sized(muted, CAPTION_SIZE + 0.5f), MARGIN, CONTENT_WIDTH)
    }

    /** A line to sign on at the right, with [caption] centred under it. */
    fun signature(caption: String) {
        ensureSpace(SIGNATURE_SPACE + 30f)
        y += SIGNATURE_SPACE
        val startX = PAGE_WIDTH - MARGIN - SIGNATURE_WIDTH
        requireNotNull(page).canvas.drawLine(startX, y, startX + SIGNATURE_WIDTH, y, rule)
        y += 12f
        val paint = sized(muted, CAPTION_SIZE)
        drawText(caption, startX + (SIGNATURE_WIDTH - paint.measureText(caption)) / 2f, paint)
        y += leading(CAPTION_SIZE)
    }

    fun finish() {
        page?.let(document::finishPage)
        page = null
    }

    /** Left-aligned text wrapped within a column starting at [x]. */
    private fun block(text: String, paint: Paint, x: Float, width: Float) {
        val size = paint.textSize
        for (paragraph in text.replace("\r\n", "\n").split('\n')) {
            for (line in wrap(paragraph, paint, width)) {
                ensureSpace(leading(size))
                drawText(line, x, paint)
                y += leading(size)
            }
        }
    }

    /** Spreads the words of [line] over the full width. */
    private fun drawJustified(line: String, paint: Paint) {
        val words = line.split(' ').filter { it.isNotEmpty() }
        if (words.size < 2) {
            drawText(line, MARGIN, paint)
            return
        }
        val gap = (CONTENT_WIDTH - words.sumOf { paint.measureText(it).toDouble() }.toFloat()) / (words.size - 1)
        // A line with very few words would get holes; such a line is left ragged instead.
        if (gap > paint.measureText(" ") * MAX_JUSTIFY_STRETCH) {
            drawText(line, MARGIN, paint)
            return
        }
        var x = MARGIN
        for (word in words) {
            drawText(word, x, paint)
            x += paint.measureText(word) + gap
        }
    }

    private fun drawText(text: String, x: Float, paint: Paint) {
        requireNotNull(page).canvas.drawText(text, x, y, paint)
    }

    private fun ensureSpace(required: Float) {
        if (y + required <= CONTENT_BOTTOM) return
        page?.let(document::finishPage)
        newPage()
    }

    private fun newPage() {
        pageNumber++
        val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), pageNumber).create()
        page = document.startPage(info).also { started ->
            // From the second page on, a small page number at the bottom.
            if (pageNumber > 1) {
                val paint = sized(muted, CAPTION_SIZE)
                val label = pageNumber.toString()
                started.canvas.drawText(label, (PAGE_WIDTH - paint.measureText(label)) / 2f, PAGE_HEIGHT - 36f, paint)
            }
        }
        y = MARGIN_TOP
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("")
        val result = mutableListOf<String>()
        var current = StringBuilder()
        for (word in text.trim().split(Regex("\\s+"))) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
                continue
            }
            if (current.isNotEmpty()) {
                result += current.toString()
                current = StringBuilder()
            }
            if (paint.measureText(word) <= maxWidth) {
                current.append(word)
            } else {
                // A single word wider than the column is broken where it no longer fits.
                var part = ""
                for (character in word) {
                    val next = part + character
                    if (part.isNotEmpty() && paint.measureText(next) > maxWidth) {
                        result += part
                        part = character.toString()
                    } else {
                        part = next
                    }
                }
                current.append(part)
            }
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    private fun paint(style: Int, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        typeface = Typeface.create(Typeface.SERIF, style)
    }

    private fun sized(paint: Paint, size: Float) = paint.apply { textSize = size }

    private fun leading(size: Float) = size * 1.45f
}

private const val PAGE_WIDTH = 595f
private const val PAGE_HEIGHT = 842f
private const val MARGIN = 64f
private const val MARGIN_TOP = 64f
private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
private const val CONTENT_BOTTOM = PAGE_HEIGHT - 64f
private const val ADDRESSEE_X = PAGE_WIDTH / 2f
private const val ADDRESSEE_WIDTH = PAGE_WIDTH - MARGIN - ADDRESSEE_X
private const val MIN_LABEL_WIDTH = 110f
private const val LABEL_GAP = 16f
private const val BODY_SIZE = 11f
private const val TITLE_SIZE = 14f
private const val CAPTION_SIZE = 8.5f
private const val PARAGRAPH_GAP = 6f
private const val SIGNATURE_WIDTH = 200f
private const val SIGNATURE_SPACE = 52f
/** How many spaces wide a justified gap may grow. */
private const val MAX_JUSTIFY_STRETCH = 6f
private const val INK = Color.BLACK
private val MUTED = Color.rgb(0x55, 0x55, 0x55)
