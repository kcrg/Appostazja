package com.tryniecki.appostazja.ui.declaration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun filled() = DeclarationUiState(
        isLoaded = true,
        fullName = "Jan Kowalski",
        homeAddress = "ul. Przykładowa 1, Warszawa",
        baptismParish = "Parafia św. Anny",
        residenceParish = "Parafia NMP",
        motivation = presets[0],
    )
}
