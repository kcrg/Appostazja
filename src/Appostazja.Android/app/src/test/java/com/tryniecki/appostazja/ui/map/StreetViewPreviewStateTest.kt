package com.tryniecki.appostazja.ui.map

import com.tryniecki.appostazja.data.StreetViewPreview
import org.junit.Assert.assertEquals
import org.junit.Test

class StreetViewPreviewStateTest {
    private fun state(preview: StreetViewPreview?, slowLoad: Boolean = false) =
        StreetViewPreviewState().apply {
            this.preview = preview
            this.slowLoad = slowLoad
        }

    @Test
    fun `a fast load shows nothing until the result is known`() {
        assertEquals(false, state(null).showsCard)
        assertEquals(true, state(null, slowLoad = true).showsCard)
    }

    @Test
    fun `without a thumbnail the card never shows and the action row gets the button`() {
        val refused = state(StreetViewPreview.Available(image = null, panoId = "p", heading = 0, copyright = "© Google", date = null))
        listOf(refused, state(StreetViewPreview.PreviewDenied)).forEach {
            assertEquals(false, it.showsCard)
            assertEquals(true, it.offersButton)
        }
        assertEquals("p", refused.panorama?.panoId)
    }

    @Test
    fun `no panorama shows neither card nor button, a network failure keeps the retry card`() {
        val none = state(StreetViewPreview.NotAvailable)
        assertEquals(false, none.showsCard)
        assertEquals(false, none.offersButton)
        assertEquals(true, state(StreetViewPreview.Failed).showsCard)
    }
}
