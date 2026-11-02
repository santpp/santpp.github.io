package app.rawapp.develop

import app.rawapp.develop.math.FilmColor
import app.rawapp.develop.math.ToneCurve

/** User-facing adjustments. Neutral values reproduce the base Rawapp look. */
data class LookParams(
    val exposureEv: Float = 0f,
    val temperatureMired: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val grain: Float = 0.35f,
    val halation: Float = 0.4f,
)

/** The fixed character of the look, derived from [LookParams]. */
object FilmLook {
    private const val BASE_CONTRAST = 1.32
    const val HALATION_THRESHOLD = 0.9f

    fun toneCurve(params: LookParams) = ToneCurve(
        contrast = BASE_CONTRAST * params.contrast,
        blackLift = 0.018,
        midGreyOut = 0.165,
    )

    fun color(params: LookParams) = FilmColor(
        saturation = 0.92 * params.saturation,
        highlightDesaturation = 0.55,
        shadowDesaturation = 0.25,
        density = 0.6,
        shadowCool = 0.7,
        highlightWarm = 0.6,
        greenToOlive = 0.5,
        blueToTeal = 0.4,
    )
}
