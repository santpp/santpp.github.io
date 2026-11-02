package app.rawapp.develop.math

import kotlin.math.pow

data class Xy(val x: Double, val y: Double) {
    fun toXyz(): Vec3 = Vec3(x / y, 1.0, (1.0 - x - y) / y)
}

fun Vec3.toXy(): Xy {
    val sum = x + y + z
    return Xy(x / sum, y / sum)
}

/** Correlated colour temperature (McCamy's approximation). */
fun cctFromXy(xy: Xy): Double {
    val n = (xy.x - 0.3320) / (0.1858 - xy.y)
    return 449.0 * n.pow(3) + 3525.0 * n.pow(2) + 6823.3 * n + 5520.33
}

/** Chromaticity on the Planckian locus (Kim et al. cubic fit), valid 1667–25000 K. */
fun xyFromCct(cct: Double): Xy {
    val t = cct.coerceIn(1667.0, 25000.0)
    val x = if (t <= 4000) {
        -0.2661239e9 / t.pow(3) - 0.2343589e6 / t.pow(2) + 0.8776956e3 / t + 0.179910
    } else {
        -3.0258469e9 / t.pow(3) + 2.1070379e6 / t.pow(2) + 0.2226347e3 / t + 0.240390
    }
    val y = when {
        t <= 2222 -> -1.1063814 * x.pow(3) - 1.34811020 * x.pow(2) + 2.18555832 * x - 0.20219683
        t <= 4000 -> -0.9549476 * x.pow(3) - 1.37418593 * x.pow(2) + 2.09137015 * x - 0.16748867
        else -> 3.0817580 * x.pow(3) - 5.87338670 * x.pow(2) + 3.75112997 * x - 0.37001483
    }
    return Xy(x, y)
}
