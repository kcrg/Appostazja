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
    fun `refused thumbnails still offer the panorama while server errors ask for a retry`() {
        // Google answers an unsigned request it will not serve with a 403 and an error PNG, not a message.
        assertEquals(ImageFailure.ThumbnailRefused, classifyImageFailure(403, ""))
        assertEquals(ImageFailure.ThumbnailRefused, classifyImageFailure(429, ""))
        assertEquals(ImageFailure.NoPanorama, classifyImageFailure(404, ""))
        assertEquals(ImageFailure.Other, classifyImageFailure(500, ""))
    }

    @Test
    fun `bearing points from panorama towards the parish`() {
        assertEquals(0, bearing(52.0, 19.0, 52.001, 19.0))
        assertEquals(90, bearing(52.0, 19.0, 52.0, 19.001))
        assertEquals(180, bearing(52.001, 19.0, 52.0, 19.0))
        assertEquals(270, bearing(52.0, 19.001, 52.0, 19.0))
    }

    @Test
    fun `user photo spheres are skipped even when closer than Google imagery`() {
        val church = 50.0616 to 19.9394
        val candidates = listOf(
            PanoramaCandidate("inside", 50.06161, 19.93941, "© Michał Kot"),
            PanoramaCandidate("street-far", 50.0620, 19.9400, "© Google"),
            PanoramaCandidate("street-near", 50.0618, 19.9396, "© Google"),
        )

        assertEquals("street-near", pickGoogleImagery(church.first, church.second, candidates)?.panoId)
    }

    @Test
    fun `without any Google imagery nothing is chosen`() {
        val candidates = listOf(PanoramaCandidate("sphere", 50.0617, 19.9395, "© Toño Piña"))

        assertEquals(null, pickGoogleImagery(50.0616, 19.9394, candidates))
    }

    @Test
    fun `probe points ring the parish at 40 and 80 metres`() {
        val distances = probePoints(50.0616, 19.9394).map { (lat, lng) -> distanceMeters(50.0616, 19.9394, lat, lng) }

        assertEquals(16, distances.size)
        distances.take(8).forEach { assertEquals(40.0, it, 0.5) }
        distances.drop(8).forEach { assertEquals(80.0, it, 0.5) }
    }
}
