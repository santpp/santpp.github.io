package app.rawapp.develop.math

import app.rawapp.develop.FilmLook
import app.rawapp.develop.LookParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToneCurveTest {
    private val curve = FilmLook.toneCurve(LookParams())

    @Test
    fun midGreyLandsOnTarget() {
        assertEquals(curve.midGreyOut, curve.apply(MID_GREY), 1e-9)
    }

    @Test
    fun blackIsLiftedNotCrushed() {
        assertEquals(curve.blackLift, curve.apply(0.0), 1e-12)
    }

    @Test
    fun shoulderApproachesPeakWithoutClipping() {
        assertTrue(curve.apply(1.0) < 0.9 * curve.peak)
        assertTrue(curve.apply(64.0) < curve.peak)
        assertTrue(curve.apply(64.0) > 0.97 * curve.peak)
    }

    @Test
    fun curveIsMonotonic() {
        val baked = curve.bake(512, -12.0, 10.0)
        for (i in 1 until baked.size) assertTrue(baked[i] >= baked[i - 1])
    }

    @Test
    fun moreContrastSteepensMidtones() {
        val strong = FilmLook.toneCurve(LookParams(contrast = 1.3f))
        val soft = curve.apply(0.3) - curve.apply(0.1)
        val steep = strong.apply(0.3) - strong.apply(0.1)
        assertTrue(steep > soft)
    }
}
