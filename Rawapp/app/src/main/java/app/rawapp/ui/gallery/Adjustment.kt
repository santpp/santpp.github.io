package app.rawapp.ui.gallery

import app.rawapp.develop.LookParams
import java.util.Locale
import kotlin.math.roundToInt

/** One user-facing slider bound to a [LookParams] field. */
enum class Adjustment(
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val get: (LookParams) -> Float,
    val set: (LookParams, Float) -> LookParams,
    val format: (Float) -> String,
) {
    Exposure("Exposición", -3f..3f, LookParams::exposureEv, { p, v -> p.copy(exposureEv = v) }, { signed(it, 1) + " EV" }),
    Temperature("Temperatura", -80f..80f, LookParams::temperatureMired, { p, v -> p.copy(temperatureMired = v) }, { signed(it, 0) }),
    Contrast("Contraste", 0.6f..1.5f, LookParams::contrast, { p, v -> p.copy(contrast = v) }, ::percent),
    Saturation("Saturación", 0f..1.8f, LookParams::saturation, { p, v -> p.copy(saturation = v) }, ::percent),
    Grain("Grano", 0f..1f, LookParams::grain, { p, v -> p.copy(grain = v) }, ::percent),
    Halation("Halación", 0f..1.5f, LookParams::halation, { p, v -> p.copy(halation = v) }, ::percent),
    Zoom("Recorte", 1f..MAX_CROP_ZOOM, LookParams::zoom, { p, v -> p.copy(zoom = v) }, ::zoomLabel),
}

/** Digital zoom limit: at 3× a 12 MP frame still keeps ~1.3 MP. */
const val MAX_CROP_ZOOM = 3f

fun zoomLabel(v: Float): String = String.format(SPANISH, "%.1f×", v)

private val SPANISH: Locale = Locale.forLanguageTag("es-ES")

private fun signed(v: Float, decimals: Int): String =
    (if (v > 0) "+" else "") + String.format(SPANISH, "%.${decimals}f", v)

private fun percent(v: Float): String = "${(v * 100).roundToInt()} %"
