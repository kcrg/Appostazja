package com.tryniecki.appostazja.ui.declaration

import com.tryniecki.appostazja.api.model.PlaceDto
import com.tryniecki.appostazja.api.model.RatingBreakdownDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParishHandoffTest {
    private val handoff = ParishHandoff()

    @Test
    fun `the form gets the church name with its address`() {
        assertEquals(
            "Kościół pw. Świętej Anny, Świętej Anny 13, 31-008 Kraków",
            parishText(" Kościół pw. Świętej Anny ", "Świętej Anny 13, 31-008 Kraków "),
        )
    }

    @Test
    fun `an address already in the name or missing is not repeated or left dangling`() {
        assertEquals(
            "Parafia św. Jakuba, Rynek 1, 00-001 Warszawa",
            parishText("Parafia św. Jakuba, Rynek 1, 00-001 Warszawa", "Rynek 1, 00-001 Warszawa"),
        )
        assertEquals("Kościół Mariacki", parishText("Kościół Mariacki", " "))
        assertEquals("Rynek 1, Kraków", parishText("", "Rynek 1, Kraków"))
    }

    @Test
    fun `the text fits the field limit`() {
        assertEquals(PARISH_MAX_LENGTH, parishText("K".repeat(250), "A".repeat(100)).length)
    }

    @Test
    fun `a pick is applied once`() {
        handoff.send(place("Kościół Mariacki"), ParishField.BAPTISM)
        val pick = handoff.pending.value!!

        assertTrue(handoff.take(pick))
        assertFalse(handoff.take(pick))
        assertNull(handoff.pending.value)
    }

    @Test
    fun `a newer pick replaces one the form never took`() {
        handoff.send(place("Kościół Mariacki"), ParishField.BAPTISM)
        val stale = handoff.pending.value!!
        handoff.send(place("Bazylika Archikatedralna"), ParishField.RESIDENCE)

        assertFalse(handoff.take(stale))
        val current = handoff.pending.value!!
        assertEquals(ParishField.RESIDENCE, current.field)
        assertEquals("Bazylika Archikatedralna", current.churchName)
        assertTrue(handoff.take(current))
    }

    private fun place(name: String) = PlaceDto(
        id = name,
        name = name,
        address = "Rynek 1, Kraków",
        latitude = 50.06,
        longitude = 19.94,
        score = 0.0,
        ratings = RatingBreakdownDto(positive = 0, neutral = 0, negative = 0, total = 0),
    )
}
