package app.rawapp.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualExposureTest {

    @Test
    fun isoStopsRespectSensorRange() {
        val stops = isoStops(29, 7213)
        assertEquals(32, stops.first())
        assertEquals(6400, stops.last())
    }

    @Test
    fun shutterStopsAreCappedAtOneSecondAndSorted() {
        val stops = shutterStops(56_000L, 16_000_000_000L)
        assertEquals(1_000_000_000L, stops.last())
        assertTrue(stops.zipWithNext().all { (a, b) -> a < b })
        assertEquals(125_000L, stops.first())
    }

    @Test
    fun nearestStopIsLogarithmic() {
        assertEquals(400, nearestStop(listOf(100, 200, 400, 800), 500.0))
        assertEquals(8_000_000L, nearestStop(listOf(4_000_000L, 8_000_000L, 16_666_666L), 9_500_000.0))
    }

    @Test
    fun formatsShutterSpeeds() {
        assertEquals("1/125", formatShutter(8_000_000L))
        assertEquals("1/8000", formatShutter(125_000L))
        assertEquals("1/2", formatShutter(500_000_000L))
        assertEquals("1 s", formatShutter(1_000_000_000L))
    }
}
