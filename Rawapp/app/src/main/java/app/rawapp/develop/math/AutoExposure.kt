package app.rawapp.develop.math

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2

private const val TARGET_LOG_AVERAGE = 0.13
private const val MIN_EV = -1.0
private const val MAX_EV = 3.0

/**
 * Baseline exposure, in stops, that brings the scene's log-average
 * luminance to a slightly-below-mid-grey target. Samples are linear 0..1.
 */
fun autoExposureEv(samples: FloatArray): Double {
    if (samples.isEmpty()) return 0.0
    val logMean = samples.sumOf { ln(it.toDouble().coerceAtLeast(1e-4)) } / samples.size
    return log2(TARGET_LOG_AVERAGE / exp(logMean)).coerceIn(MIN_EV, MAX_EV)
}
