package com.tryniecki.appostazja.api.client

import org.junit.Assert.assertEquals
import org.junit.Test

class AppostazjaApiClientFactoryTest {
    @Test
    fun normalizeBaseUrl_appendsApiPath() {
        assertEquals(
            "https://example.test/api/v1/",
            AppostazjaApiClientFactory.normalizeBaseUrl("https://example.test/"),
        )
    }

    @Test
    fun normalizeBaseUrl_doesNotDuplicateExistingApiPath() {
        assertEquals(
            "https://example.test/api/v1/",
            AppostazjaApiClientFactory.normalizeBaseUrl("https://example.test/api/v1"),
        )
    }
}
