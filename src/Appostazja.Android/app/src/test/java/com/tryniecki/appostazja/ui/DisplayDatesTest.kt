package com.tryniecki.appostazja.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

class DisplayDatesTest {
    private val polish = Locale.forLanguageTag("pl-PL")

    @Test
    fun `server timestamp late in the UTC evening is shown as local date and time`() {
        val formatted = formatApiTimestamp("2026-11-12T23:30:00+00:00", polish, ZoneId.of("Europe/Warsaw"))

        assertEquals("${formatApiDate("2026-11-13", polish)}, 00:30", formatted)
    }

    @Test
    fun `unparsable values do not crash the screen`() {
        assertEquals("kiedyś", formatApiDate("kiedyś", polish))
        assertNull(formatApiTimestamp("wczoraj", polish))
    }
}
