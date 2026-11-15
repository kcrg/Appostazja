package com.tryniecki.appostazja.pdf

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import kotlin.math.max

/** Lays out A4 pages of plain text: aligned, wrapped lines, paragraph spacing and a signature line. */
internal class PdfWriter(private val document: PdfDocument) {
    private var pageNumber = 0
    private var page: PdfDocument.Page? = null
    private var y = MARGIN
    private val regular = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    private val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.DKGRAY
        strokeWidth = 0.7f
    }

    init { newPage() }

    fun space(points: Float) { y += points }
    fun left(text: String, size: Float, bold: Boolean = false) = writeWrapped(text, size, Alignment.LEFT, bold)
    fun right(text: String, size: Float, bold: Boolean = false) = writeWrapped(text, size, Alignment.RIGHT, bold)
    fun center(text: String, size: Float, bold: Boolean = false) = writeWrapped(text, size, Alignment.CENTER, bold)
    fun paragraph(text: String, size: Float, bold: Boolean = false) = writeWrapped(text, size, Alignment.LEFT, bold)

    fun signature(label: String) {
        ensureSpace(100f)
        y += 56f
        val canvas = requireNotNull(page).canvas
        val lineWidth = 190f
        val startX = PAGE_WIDTH - MARGIN - lineWidth
        canvas.drawLine(startX, y, startX + lineWidth, y, linePaint)
        y += 14f
        drawLine(label, 8f, startX, Alignment.LEFT, false)
    }

    fun finish() {
        page?.let(document::finishPage)
        page = null
    }

    private fun writeWrapped(text: String, size: Float, alignment: Alignment, isBold: Boolean) {
        val paint = if (isBold) bold else regular
        paint.textSize = size
        val availableWidth = PAGE_WIDTH - 2 * MARGIN
        val paragraphs = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        for ((paragraphIndex, paragraph) in paragraphs.withIndex()) {
            val lines = wrap(paragraph, paint, availableWidth)
            val leading = max(size + 4f, 15f)
            for (line in lines) {
                ensureSpace(leading)
                val anchor = when (alignment) {
                    Alignment.LEFT -> MARGIN
                    Alignment.CENTER -> PAGE_WIDTH / 2f
                    Alignment.RIGHT -> PAGE_WIDTH - MARGIN
                }
                drawLine(line, size, anchor, alignment, isBold)
                y += leading
            }
            if (paragraphIndex != paragraphs.lastIndex) y += 3f
        }
    }

    private fun drawLine(text: String, size: Float, anchorX: Float, alignment: Alignment, isBold: Boolean) {
        val paint = if (isBold) bold else regular
        paint.textSize = size
        val width = paint.measureText(text)
        val x = when (alignment) {
            Alignment.LEFT -> anchorX
            Alignment.CENTER -> anchorX - width / 2f
            Alignment.RIGHT -> anchorX - width
        }
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
        page = document.startPage(info)
        y = MARGIN
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

    private enum class Alignment { LEFT, CENTER, RIGHT }
}

private const val PAGE_WIDTH = 595f
private const val PAGE_HEIGHT = 842f
private const val MARGIN = 57f
private const val CONTENT_BOTTOM = PAGE_HEIGHT - 72f
