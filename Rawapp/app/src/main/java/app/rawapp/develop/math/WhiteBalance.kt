package app.rawapp.develop.math

/**
 * Camera neutral after moving the assumed scene white by [shiftMired].
 * Positive values assume a bluer light, so the picture renders warmer.
 * The off-locus (tint) component of the original white is preserved.
 */
fun shiftedNeutral(sensor: SensorColor, asShotNeutral: Vec3, shiftMired: Double): Vec3 {
    if (shiftMired == 0.0) return asShotNeutral
    val white = whiteXyForNeutral(sensor, asShotNeutral)
    val cct = cctFromXy(white)
    val shiftedCct = 1e6 / (1e6 / cct - shiftMired).coerceAtLeast(40.0)
    val from = xyFromCct(cct)
    val to = xyFromCct(shiftedCct)
    val target = Xy(white.x + to.x - from.x, white.y + to.y - from.y)
    val neutral = xyzToCamera(sensor, illuminantWeight(sensor, shiftedCct)) * target.toXyz()
    return neutral / neutral.y
}
