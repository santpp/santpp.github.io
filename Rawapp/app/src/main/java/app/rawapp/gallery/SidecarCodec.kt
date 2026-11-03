package app.rawapp.gallery

import app.rawapp.develop.LookParams
import java.util.Locale

/** Flat JSON for per-photo adjustments. Unknown or missing keys fall back to defaults. */
object SidecarCodec {
    private val number = Regex("\"(\\w+)\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?(?:[eE][-+]?[0-9]+)?)")

    fun encode(p: LookParams): String = listOf(
        "version" to 1f,
        "exposureEv" to p.exposureEv,
        "temperatureMired" to p.temperatureMired,
        "contrast" to p.contrast,
        "saturation" to p.saturation,
        "grain" to p.grain,
        "halation" to p.halation,
        "zoom" to p.zoom,
    ).joinToString(",\n  ", "{\n  ", "\n}\n") { (k, v) -> "\"$k\": ${String.format(Locale.US, "%.4f", v)}" }

    fun decode(json: String): LookParams {
        val values = number.findAll(json).associate { it.groupValues[1] to it.groupValues[2].toFloat() }
        val d = LookParams()
        return LookParams(
            exposureEv = values["exposureEv"] ?: d.exposureEv,
            temperatureMired = values["temperatureMired"] ?: d.temperatureMired,
            contrast = values["contrast"] ?: d.contrast,
            saturation = values["saturation"] ?: d.saturation,
            grain = values["grain"] ?: d.grain,
            halation = values["halation"] ?: d.halation,
            zoom = values["zoom"] ?: d.zoom,
        )
    }
}
