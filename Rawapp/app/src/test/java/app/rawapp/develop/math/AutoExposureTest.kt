package app.rawapp.develop.math

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoExposureTest {

    @Test
    fun sceneAtTargetNeedsNoCorrection() {
        assertEquals(0.0, autoExposureEv(FloatArray(100) { 0.09f }), 1e-6)
    }

    @Test
    fun darkSceneIsBrightenedWithinLimit() {
        assertEquals(1.0, autoExposureEv(FloatArray(100) { 0.045f }), 1e-3)
        assertEquals(3.0, autoExposureEv(FloatArray(100) { 0.001f }), 1e-9)
    }

    @Test
    fun emptySamplesLeaveExposureAlone() {
        assertEquals(0.0, autoExposureEv(FloatArray(0)), 0.0)
    }
}
