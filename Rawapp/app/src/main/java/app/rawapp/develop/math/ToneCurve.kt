package app.rawapp.develop.math

import kotlin.math.pow

const val MID_GREY = 0.18

/**
 * Filmic curve: scene-linear → display-linear. A power/hyperbola gives a toe
 * and a long shoulder that approaches 1 without clipping; blacks are lifted
 * slightly and scene mid-grey lands on [midGreyOut].
 */
data class ToneCurve(
    val contrast: Double,
    val blackLift: Double,
    val midGreyOut: Double,
) {
    private val k: Double = run {
        val target = (midGreyOut - blackLift) / (1.0 - blackLift)
        MID_GREY.pow(contrast) * (1.0 / target - 1.0)
    }

    fun apply(sceneLinear: Double): Double {
        val p = sceneLinear.coerceAtLeast(0.0).pow(contrast)
        return blackLift + (1.0 - blackLift) * p / (p + k)
    }

    /** Samples the curve over stops relative to mid-grey, [minStops]..[maxStops]. */
    fun bake(size: Int, minStops: Double, maxStops: Double): FloatArray = FloatArray(size) { i ->
        val stops = minStops + (maxStops - minStops) * i / (size - 1)
        apply(MID_GREY * 2.0.pow(stops)).toFloat()
    }
}
