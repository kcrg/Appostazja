package com.tryniecki.appostazja.pdf

import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal val DECLARATION_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** The baptism date line of the declaration; an unknown date leaves room to write it in by hand. */
internal fun baptismDateLine(date: BaptismDate): String = when (date) {
    is BaptismDate.Exact -> "Data chrztu: ${date.date.format(DECLARATION_DATE)}"
    is BaptismDate.Year -> "Rok chrztu: ${date.year}"
    BaptismDate.Unknown -> "Data chrztu: ........................................"
}

/** The top right line of the letter: "Warszawa, 15.11.2026", or only the date when the town is not known. */
internal fun letterDateLine(homeAddress: String, date: LocalDate): String {
    val town = townFromAddress(homeAddress)
    val formatted = date.format(DECLARATION_DATE)
    return if (town != null) "$town, $formatted" else formatted
}

/**
 * The town of a Polish address, read after its postal code ("ul. Długa 1, 00-950 Warszawa" → "Warszawa").
 * Without a postal code there is no reliable way to tell the town from the street, so the answer is null.
 */
internal fun townFromAddress(address: String): String? =
    POSTAL_CODE_TOWN.findAll(address).lastOrNull()
        ?.groupValues?.get(1)
        ?.trim()
        ?.trimEnd('.', ' ')
        ?.takeIf { it.isNotEmpty() }

// A postal code, then the town up to a comma, a line break or a digit (a street number after it).
private val POSTAL_CODE_TOWN = Regex("""(?<!\d)\d{2}\s?-\s?\d{3}(?!\d)\s+([^,\n\d]+)""")

/** The earliest baptism year the form accepts; anything before is a typo. */
internal const val MIN_BAPTISM_YEAR = 1900
