package app.rawapp.develop

import app.rawapp.develop.math.FilmColor
import app.rawapp.develop.math.ToneCurve

/** User-facing adjustments. Neutral values reproduce the base Rawapp look. */
data class LookParams(
    val exposureEv: Float = 0f,
    val temperatureMired: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val grain: Float = 0.5f,
    val halation: Float = 0.6f,
)

/** The fixed character of the look, derived from [LookParams]. */
object FilmLook {
    private const val BASE_CONTRAST = 1.6
    const val HALATION_THRESHOLD = 0.75f
    const val VIGNETTE = 0.22f

    fun toneCurve(params: LookParams) = ToneCurve(
        contrast = BASE_CONTRAST * params.contrast,
        blackLift = 0.0015,
        midGreyOut = 0.12,
        peak = 0.86,
    )

    fun color(params: LookParams) = FilmColor(
        saturation = 0.9 * params.saturation,
        highlightDesaturation = 0.65,
        shadowDesaturation = 0.1,
        density = 0.6,
        shadowCool = 1.0,
        highlightWarm = 1.0,
        greenToOlive = 0.6,
        blueToTeal = 1.0,
    )
}
