package app.rawapp.develop.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhiteBalanceTest {

    @Test
    fun zeroShiftKeepsNeutral() {
        assertEquals(D65_NEUTRAL, shiftedNeutral(PIXEL_MAIN, D65_NEUTRAL, 0.0))
    }

    @Test
    fun positiveShiftAssumesBluerLight() {
        val warmer = shiftedNeutral(PIXEL_MAIN, D65_NEUTRAL, 30.0)
        val cct = cctFromXy(whiteXyForNeutral(PIXEL_MAIN, warmer))
        assertTrue("CCT $cct", cct > 7000.0)
    }

    @Test
    fun negativeShiftRendersCooler() {
        val cooler = shiftedNeutral(PIXEL_MAIN, D65_NEUTRAL, -30.0)
        val cct = cctFromXy(whiteXyForNeutral(PIXEL_MAIN, cooler))
        assertTrue("CCT $cct", cct < 6000.0)
    }

    @Test
    fun shiftedNeutralIsGreenNormalised() {
        assertEquals(1.0, shiftedNeutral(PIXEL_MAIN, D65_NEUTRAL, 20.0).y, 1e-12)
    }
}
