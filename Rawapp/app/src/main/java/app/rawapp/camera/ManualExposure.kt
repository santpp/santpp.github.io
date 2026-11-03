package app.rawapp.camera

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToLong

/** Manual sensor settings for Pro mode. */
data class ManualExposure(val iso: Int, val exposureNs: Long)

private val ISO_STOPS = intArrayOf(
    25, 32, 40, 50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800,
    1000, 1250, 1600, 2000, 2500, 3200, 4000, 5000, 6400,
)

/** Shutter speeds in third stops as fractions of a second, 1/8000 s … 1 s. */
private val SHUTTER_DENOMINATORS = intArrayOf(
    8000, 6400, 5000, 4000, 3200, 2500, 2000, 1600, 1250, 1000, 800, 640, 500, 400, 320, 250,
    200, 160, 125, 100, 80, 60, 50, 40, 30, 25, 20, 15, 13, 10, 8, 6, 5, 4, 3, 2,
)

private const val ONE_SECOND_NS = 1_000_000_000L

/** Longest shutter offered: longer exposures make the live preview unusable. */
const val MAX_MANUAL_EXPOSURE_NS = ONE_SECOND_NS

/** Standard ISO values the sensor supports. */
fun isoStops(min: Int, max: Int): List<Int> = ISO_STOPS.filter { it in min..max }

/** Standard shutter speeds (ns) the sensor supports, fastest first. */
fun shutterStops(minNs: Long, maxNs: Long): List<Long> =
    (SHUTTER_DENOMINATORS.map { ONE_SECOND_NS / it } + ONE_SECOND_NS)
        .filter { it in minNs..minOf(maxNs, MAX_MANUAL_EXPOSURE_NS) }

/** The stop closest to [value] on a logarithmic scale. */
fun <T : Number> nearestStop(stops: List<T>, value: Double): T =
    stops.minBy { abs(ln(it.toDouble()) - ln(value.coerceAtLeast(1e-9))) }

/** "1/125" for fractions, "1 s" or "0,5 s"-style for long exposures. */
fun formatShutter(ns: Long): String {
    if (ns >= ONE_SECOND_NS / 2) {
        val seconds = ns.toDouble() / ONE_SECOND_NS
        return if (seconds >= 1.0) "${seconds.roundToLong()} s" else "1/2"
    }
    return "1/${(ONE_SECOND_NS.toDouble() / ns).roundToLong()}"
}
