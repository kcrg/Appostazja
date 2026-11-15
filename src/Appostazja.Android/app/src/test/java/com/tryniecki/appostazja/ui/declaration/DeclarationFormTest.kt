package com.tryniecki.appostazja.ui.declaration

import com.tryniecki.appostazja.pdf.BaptismDate
import com.tryniecki.appostazja.pdf.CertificateDelivery
import com.tryniecki.appostazja.security.DeclarationDraft
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
    fun `living in the parish of baptism the declaration goes there and needs no certificate`() {
        val form = filled().copy(residenceParish = "", livesInBaptismParish = true, hasBaptismCertificate = null)

        assertEquals("Parafia św. Anny", form.effectiveResidenceParish)
        assertTrue(form.canGenerate)
        assertFalse(form.needsBaptismCertificate)
        // Living elsewhere the residence parish is asked for, and whether a certificate is at hand.
        val elsewhere = form.copy(livesInBaptismParish = false)
        assertEquals(listOf(FormField.SITUATION, FormField.RESIDENCE_PARISH), elsewhere.missingFields())
        assertTrue(elsewhere.needsBaptismCertificate)
    }

    @Test
    fun `the situation must be answered before anything else`() {
        val form = filled().copy(livesInBaptismParish = null)

        assertEquals(listOf(FormField.SITUATION), form.missingFields())
        assertEquals(DeclarationStep.SITUATION, form.initialStep())
        // Not having a certificate yet is an answer; the declaration can still be prepared.
        assertTrue(filled().copy(hasBaptismCertificate = false).canGenerate)
    }

    @Test
    fun `a restored draft opens on the first step with something missing, or on the last one`() {
        assertEquals(DeclarationStep.MOTIVATION, filled().initialStep())
        assertEquals(DeclarationStep.PERSONAL, filled().copy(homeAddress = "", motivation = "").initialStep())
        assertEquals(DeclarationStep.BAPTISM, filled().copy(residenceParish = "").initialStep())
    }

    @Test
    fun `missing fields are listed in the order of the wizard`() {
        val form = filled().copy(fullName = " ", baptismParish = "", motivation = "")

        assertEquals(listOf(FormField.FULL_NAME, FormField.BAPTISM_PARISH, FormField.MOTIVATION), form.missingFields())
        // Living in the parish of baptism, the residence field is not asked for.
        assertEquals(
            listOf(FormField.FULL_NAME, FormField.BAPTISM_PARISH, FormField.MOTIVATION),
            form.copy(residenceParish = "", livesInBaptismParish = true).missingFields(),
        )
        assertEquals(FormField.RESIDENCE_PARISH, filled().copy(residenceParish = "").missingFields().single())
    }

    @Test
    fun `the certificate request needs only who asks, the parish and a sensible date`() {
        val request = DeclarationUiState(
            isLoaded = true,
            fullName = "Jan Kowalski",
            homeAddress = "ul. Długa 1, 00-950 Warszawa",
            baptismParish = "Parafia św. Anny",
            baptismDateUnknown = true,
        )

        assertEquals(emptyList<FormField>(), request.requestMissingFields())
        assertEquals(
            listOf(FormField.BAPTISM_PARISH, FormField.HOME_ADDRESS, FormField.BAPTISM_DATE),
            request.copy(baptismParish = "", homeAddress = "", baptismYear = "19").requestMissingFields(),
        )
    }

    @Test
    fun `a draft keeps every answer and an old draft with the same parish ticked keeps it`() {
        val form = filled().copy(
            hasBaptismCertificate = false,
            requestAnnotatedCertificate = true,
            birthDate = LocalDate.of(1995, 4, 2),
            fatherName = "Adam",
            certificateDelivery = CertificateDelivery.IN_PERSON,
            step = DeclarationStep.BAPTISM,
        )

        // The step is where the user was, not part of the draft.
        assertEquals(form.copy(step = DeclarationStep.SITUATION), form.toDraft().toUiState())
        val old = DeclarationDraft(residenceSameAsBaptism = true)
        assertEquals(true, old.toUiState().livesInBaptismParish)
        assertNull(DeclarationDraft().toUiState().livesInBaptismParish)
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
        livesInBaptismParish = false,
        hasBaptismCertificate = true,
        motivation = presets[0],
    )
}
