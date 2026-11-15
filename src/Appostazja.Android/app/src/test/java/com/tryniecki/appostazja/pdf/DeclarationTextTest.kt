package com.tryniecki.appostazja.pdf

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeclarationTextTest {
    private val date = LocalDate.of(2026, 11, 15)

    @Test
    fun `the letter is dated from the town after the postal code`() {
        assertEquals("Warszawa, 15.11.2026", letterDateLine("ul. Przykładowa 12/3, 00-001 Warszawa", date))
        assertEquals("Kraków-Podgórze, 15.11.2026", letterDateLine("ul. Wielicka 1\n30-552 Kraków-Podgórze", date))
        assertEquals("Bielsko-Biała, 15.11.2026", letterDateLine("43-300 Bielsko-Biała, ul. Cicha 5", date))
    }

    @Test
    fun `without a postal code the letter carries only the date`() {
        assertEquals("15.11.2026", letterDateLine("ul. Długa 5 m. 2, Gdańsk", date))
        assertNull(townFromAddress("ul. Długa 5"))
        // A number that only looks like part of a postal code is not one.
        assertNull(townFromAddress("ul. Polna 123-456 lok. 2"))
    }

    @Test
    fun `the baptism date line follows what is known`() {
        assertEquals("Data chrztu: 20.01.2000", baptismDateLine(BaptismDate.Exact(LocalDate.of(2000, 1, 20))))
        assertEquals("Rok chrztu: 1998", baptismDateLine(BaptismDate.Year(1998)))
        assertEquals(true, baptismDateLine(BaptismDate.Unknown).startsWith("Data chrztu: ...."))
    }
}
