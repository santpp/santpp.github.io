package app.rawapp.develop.math

import kotlin.math.pow

fun srgbEncode(linear: Double): Double {
    val v = linear.coerceIn(0.0, 1.0)
    return if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1.0 / 2.4) - 0.055
}

fun srgbDecode(encoded: Double): Double {
    val v = encoded.coerceIn(0.0, 1.0)
    return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
}

fun luminance709(c: Vec3): Double = 0.2126 * c.x + 0.7152 * c.y + 0.0722 * c.z
