package app.rawapp.develop.math

import kotlin.math.pow

/**
 * Colour character applied after the tone curve, in display-linear Rec.709.
 * All strengths are 0..1.
 */
data class FilmColor(
    val saturation: Double,
    val highlightDesaturation: Double,
    val shadowDesaturation: Double,
    val density: Double,
    val shadowCool: Double,
    val highlightWarm: Double,
    val greenToOlive: Double,
    val blueToTeal: Double,
)

/** Applies [FilmColor] to one display-linear colour. */
fun gradeColor(c: Vec3, look: FilmColor): Vec3 {
    var rgb = c
    val y = luminance709(rgb)

    val highlightMask = smoothstep(0.3, 0.85, y)
    val shadowMask = 1.0 - smoothstep(0.0, 0.12, y)
    val sat = look.saturation *
        (1.0 - look.highlightDesaturation * highlightMask) *
        (1.0 - look.shadowDesaturation * shadowMask)
    rgb = Vec3(y + (rgb.x - y) * sat, y + (rgb.y - y) * sat, y + (rgb.z - y) * sat)

    val mx = rgb.max()
    val chroma = if (mx > 1e-6) (mx - rgb.min()) / mx else 0.0
    rgb *= 1.0 - look.density * chroma.pow(1.5) * 0.3 * (1.0 - 0.6 * blueness(rgb))

    val greenExcess = (rgb.y - maxOf(rgb.x, rgb.z)).coerceAtLeast(0.0)
    val blueExcess = (rgb.z - maxOf(rgb.x, rgb.y)).coerceAtLeast(0.0)
    rgb = Vec3(
        rgb.x + look.greenToOlive * 0.35 * greenExcess,
        rgb.y - look.greenToOlive * 0.1 * greenExcess + look.blueToTeal * 0.15 * blueExcess,
        rgb.z - look.blueToTeal * 0.1 * blueExcess,
    )

    val teal = look.shadowCool * shadowMask * smoothstep(0.0, 0.04, y) * 0.01
    val warm = look.highlightWarm * highlightMask
    rgb = Vec3(
        (rgb.x - teal) * (1.0 + 0.07 * warm),
        (rgb.y + teal) * (1.0 - 0.03 * warm),
        (rgb.z + teal * 0.6) * (1.0 - 0.2 * warm),
    )
    return rgb.map { it.coerceIn(0.0, 1.0) }
}

/** 3D LUT indexed by sRGB-encoded input, holding sRGB-encoded output, red fastest. */
fun bakeFilmLut(size: Int, look: FilmColor): FloatArray {
    val out = FloatArray(size * size * size * 3)
    var i = 0
    for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
        val input = Vec3(r.toDouble(), g.toDouble(), b.toDouble()).map { srgbDecode(it / (size - 1.0)) }
        val graded = gradeColor(input, look)
        out[i++] = srgbEncode(graded.x).toFloat()
        out[i++] = srgbEncode(graded.y).toFloat()
        out[i++] = srgbEncode(graded.z).toFloat()
    }
    return out
}

/** 0..1, how far a colour is dominated by blue (skies). */
private fun blueness(c: Vec3): Double {
    val mx = c.max()
    if (mx <= 1e-6) return 0.0
    return ((c.z - maxOf(c.x, c.y)) / mx).coerceIn(0.0, 1.0)
}

internal fun smoothstep(edge0: Double, edge1: Double, x: Double): Double {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t)
}
