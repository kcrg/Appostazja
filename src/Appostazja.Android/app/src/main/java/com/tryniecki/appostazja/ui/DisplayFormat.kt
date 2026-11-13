package com.tryniecki.appostazja.ui

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Formats an API `yyyy-MM-dd` date for display, e.g. "8 lutego 2021"; unparsable values are shown as-is. */
internal fun formatApiDate(value: String, locale: Locale): String =
    runCatching { LocalDate.parse(value).format(longDate(locale)) }.getOrDefault(value)

/** Formats an API timestamp (ISO with offset) as the calendar day in [zone]; null when it cannot be parsed. */
internal fun formatApiTimestampDate(value: String, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String? =
    runCatching {
        OffsetDateTime.parse(value).atZoneSameInstant(zone).toLocalDate().format(longDate(locale))
    }.getOrNull()

private fun longDate(locale: Locale) = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)

/** "4,5" for a rating the API already rounded to one decimal. */
internal fun formatRating(rating: Double, locale: Locale): String = String.format(locale, "%.1f", rating)
