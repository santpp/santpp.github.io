package app.rawapp.develop.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorTransformTest {

    @Test
    fun d65NeutralIsRecognisedAsDaylight() {
        val cct = cctFromXy(whiteXyForNeutral(PIXEL_MAIN, D65_NEUTRAL))
        assertEquals(6504.0, cct, 150.0)
    }

    @Test
    fun cameraNeutralRendersAsNeutralWhite() {
        val transform = colorTransform(PIXEL_MAIN, D65_NEUTRAL)
        val balanced = Vec3(
            D65_NEUTRAL.x * transform.whiteBalanceGains.x,
            D65_NEUTRAL.y * transform.whiteBalanceGains.y,
            D65_NEUTRAL.z * transform.whiteBalanceGains.z,
        )
        val rgb = transform.cameraToWorking * balanced
        assertEquals(rgb.x, rgb.y, 0.01 * rgb.y)
        assertEquals(rgb.z, rgb.y, 0.01 * rgb.y)
    }

    @Test
    fun whiteBalanceGainsNeverAttenuate() {
        val gains = colorTransform(PIXEL_MAIN, D65_NEUTRAL).whiteBalanceGains
        assertEquals(1.0, gains.min(), 1e-12)
        assertTrue(gains.max() > 1.0)
    }

    @Test
    fun illuminantWeightFollowsInverseTemperature() {
        assertEquals(1.0, illuminantWeight(PIXEL_MAIN, 2856.0), 1e-9)
        assertEquals(0.0, illuminantWeight(PIXEL_MAIN, 6504.0), 1e-9)
        assertEquals(0.0, illuminantWeight(PIXEL_MAIN, 9000.0), 1e-9)
        assertTrue(illuminantWeight(PIXEL_MAIN, 4000.0) in 0.4..0.7)
    }

    @Test
    fun planckianLocusRoundTripsThroughMcCamy() {
        for (cct in listOf(3000.0, 4500.0, 6500.0)) {
            assertEquals(cct, cctFromXy(xyFromCct(cct)), cct * 0.02)
        }
    }
}
