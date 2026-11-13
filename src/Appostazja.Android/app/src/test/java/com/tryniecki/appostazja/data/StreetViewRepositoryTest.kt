package com.tryniecki.appostazja.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StreetViewRepositoryTest {
    @Test
    fun `rate limited responses are treated as quota exhaustion`() {
        assertEquals("OVER_QUERY_LIMIT", classifyHttpError(429, ""))
        assertEquals(
            "OVER_QUERY_LIMIT",
            classifyHttpError(403, "You have exceeded your daily request quota for this API."),
        )
    }

    @Test
    fun `other failures are not reported as quota exhaustion`() {
        assertEquals("ZERO_RESULTS", classifyHttpError(404, ""))
        assertEquals("HTTP_403", classifyHttpError(403, "This API project is not authorized to use this API."))
        assertEquals("HTTP_500", classifyHttpError(500, ""))
    }

    @Test
    fun `bearing points from panorama towards the parish`() {
        assertEquals(0, bearing(52.0, 19.0, 52.001, 19.0))
        assertEquals(90, bearing(52.0, 19.0, 52.0, 19.001))
        assertEquals(180, bearing(52.001, 19.0, 52.0, 19.0))
        assertEquals(270, bearing(52.0, 19.001, 52.0, 19.0))
    }
}
