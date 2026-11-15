package com.tryniecki.appostazja.ui.declaration

import com.tryniecki.appostazja.pdf.BaptismDate
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeclarationFormTest {
    private val presets = listOf("Jestem osobą niewierzącą.", "Nie podzielam nauczania.", "Sprzeciw wobec instytucji.")

    @Test
    fun `a ready-made reason fills an empty field and replaces another ready-made one`() {
        assertEquals(presets[0], withMotivationPreset("", presets[0], presets))
        assertEquals(presets[1], withMotivationPreset("  ${presets[0]}\n", presets[1], presets))
    }

    @Test
    fun `own words are kept and the ready-made reason follows them`() {
        val own = "Od lat nie praktykuję."

        assertEquals("$own\n\n${presets[2]}", withMotivationPreset("$own\n", presets[2], presets))
        // Choosing it again does not add it twice.
        val both = "$own\n\n${presets[2]}"
        assertEquals(both, withMotivationPreset(both, presets[2], presets))
    }

    @Test
    fun `with the same parish ticked the declaration goes to the parish of baptism`() {
        val form = filled().copy(baptismParish = "Parafia św. Anny", residenceParish = "", residenceSameAsBaptism = true)

        assertEquals("Parafia św. Anny", form.effectiveResidenceParish)
        assertTrue(form.canGenerate)
        assertFalse(form.copy(residenceSameAsBaptism = false).canGenerate)
        assertFalse(form.copy(baptismParish = " ").canGenerate)
    }

    @Test
    fun `unticking the same parish brings back what was typed for the residence`() {
        val form = filled().copy(residenceParish = "Parafia NMP", residenceSameAsBaptism = true)

        assertEquals("Parafia NMP", form.copy(residenceSameAsBaptism = false).effectiveResidenceParish)
    }

    @Test
    fun `missing fields are listed in the order of the form`() {
        val form = filled().copy(fullName = " ", baptismParish = "", motivation = "")

        assertEquals(listOf(FormField.FULL_NAME, FormField.BAPTISM_PARISH, FormField.MOTIVATION), form.missingFields())
        // A hidden residence field is not asked for.
        assertEquals(
            listOf(FormField.FULL_NAME, FormField.BAPTISM_PARISH, FormField.MOTIVATION),
            form.copy(residenceParish = "", residenceSameAsBaptism = true).missingFields(),
        )
        assertEquals(FormField.RESIDENCE_PARISH, filled().copy(residenceParish = "").missingFields().single())
    }

    @Test
    fun `an unknown baptism date gives the year when it is plausible and a blank otherwise`() {
        val today = LocalDate.of(2026, 11, 15)
        val unknown = filled().copy(baptismDateUnknown = true)

        assertEquals(BaptismDate.Unknown, unknown.baptismDateOrNull(today))
        assertEquals(BaptismDate.Year(1998), unknown.copy(baptismYear = "1998").baptismDateOrNull(today))
        // A year still being typed, from the future or before 1900 keeps the form from being generated.
        for (year in listOf("199", "2027", "1850")) {
            assertNull(year, unknown.copy(baptismYear = year).baptismDateOrNull(today))
            assertEquals(listOf(FormField.BAPTISM_DATE), unknown.copy(baptismYear = year).missingFields(today))
        }
        // Unticked, the picked date counts again, whatever year was typed.
        assertEquals(
            BaptismDate.Exact(DEFAULT_BAPTISM_DATE),
            unknown.copy(baptismYear = "2027", baptismDateUnknown = false).baptismDateOrNull(today),
        )
    }

    private fun filled() = DeclarationUiState(
        isLoaded = true,
        fullName = "Jan Kowalski",
        homeAddress = "ul. Przykładowa 1, Warszawa",
        baptismParish = "Parafia św. Anny",
        residenceParish = "Parafia NMP",
        motivation = presets[0],
    )
}
