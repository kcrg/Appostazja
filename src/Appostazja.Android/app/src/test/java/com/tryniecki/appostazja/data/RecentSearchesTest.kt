package com.tryniecki.appostazja.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentSearchesTest {
    @Test
    fun `the newest search comes first and only three are kept`() {
        val recent = listOf("Kraków", "Mariacki", "Gdańsk")

        assertEquals(listOf("Wrocław", "Kraków", "Mariacki"), withRecentSearch(recent, "Wrocław"))
    }

    @Test
    fun `searching again moves the phrase to the top in its latest spelling`() {
        val recent = listOf("Kraków", "mariacki", "Gdańsk")

        assertEquals(listOf("Mariacki", "Kraków", "Gdańsk"), withRecentSearch(recent, "  Mariacki "))
    }

    @Test
    fun `blank searches are not remembered and spacing is tidied`() {
        val recent = listOf("Kraków")

        assertEquals(recent, withRecentSearch(recent, "   "))
        assertEquals(listOf("św. Anny", "Kraków"), withRecentSearch(recent, "św.   Anny\n"))
    }
}
