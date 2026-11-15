package com.tryniecki.appostazja.pdf

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CertificateRequestTextTest {
    private val request = BaptismCertificateRequest(
        fullName = " Jan Kowalski ",
        homeAddress = "ul. Długa 1\n00-950 Warszawa",
        baptismParish = "Parafia św. Anny",
        baptismDate = BaptismDate.Year(1995),
        birthDate = LocalDate.of(1995, 4, 2),
        birthPlace = "Warszawa",
        fatherName = "Adam",
        motherName = "Anna Nowak",
        delivery = CertificateDelivery.POST,
        contact = "",
        requestDate = LocalDate.of(2026, 11, 15),
    )

    @Test
    fun `the request lists what helps find the baptism record`() {
        val text = certificateRequestText(request)

        assertEquals("Prośba o wydanie świadectwa chrztu – Jan Kowalski", text.subject)
        assertEquals(
            listOf(
                "Imię i nazwisko: Jan Kowalski",
                "Data urodzenia: 02.04.1995",
                "Miejsce urodzenia: Warszawa",
                "Imię ojca: Adam",
                "Imię i nazwisko rodowe matki: Anna Nowak",
                "Rok chrztu: 1995",
            ),
            text.details,
        )
        assertEquals("Proszę o przesłanie świadectwa pocztą na adres: ul. Długa 1 00-950 Warszawa.", text.delivery)
        assertNull(text.contact)
    }

    @Test
    fun `details that were not given are left out rather than printed empty`() {
        val text = certificateRequestText(
            request.copy(
                baptismDate = BaptismDate.Unknown,
                birthDate = null,
                birthPlace = " ",
                fatherName = "",
                motherName = "",
                delivery = CertificateDelivery.IN_PERSON,
                contact = " 600 100 200 ",
            ),
        )

        assertEquals(listOf("Imię i nazwisko: Jan Kowalski"), text.details)
        assertTrue(text.delivery.startsWith("Świadectwo odbiorę osobiście"))
        assertEquals("Kontakt: 600 100 200", text.contact)
    }

    @Test
    fun `the e-mail carries the same request, signed with the name`() {
        val text = certificateRequestText(request.copy(contact = "jan@example.com"))
        val body = certificateRequestEmail(text, request.fullName)

        assertTrue(body.startsWith("Dzień dobry,\n\n${text.request}"))
        text.details.forEach { assertTrue(it, it in body) }
        assertTrue(body.endsWith("${text.delivery}\nKontakt: jan@example.com\n\nZ poważaniem\nJan Kowalski"))
    }
}
