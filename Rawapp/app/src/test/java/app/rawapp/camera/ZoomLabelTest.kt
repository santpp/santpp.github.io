package app.rawapp.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomLabelTest {

    @Test
    fun wideLensUsesDecimalComma() {
        assertEquals("0,5×", formatZoom(0.52f))
        assertEquals("0,6×", formatZoom(0.61f))
    }

    @Test
    fun mainLensIsOne() {
        assertEquals("1×", formatZoom(1.0f))
        assertEquals("1×", formatZoom(0.97f))
    }

    @Test
    fun teleLensRoundsToWholeNumber() {
        assertEquals("5×", formatZoom(4.9f))
    }
}
