package app.rawapp.develop.math

/** DNG colour calibration of one sensor: two reference illuminants and their matrices. */
data class SensorColor(
    val colorMatrix1: Mat3,
    val colorMatrix2: Mat3,
    val forwardMatrix1: Mat3,
    val forwardMatrix2: Mat3,
    val calibration1: Mat3,
    val calibration2: Mat3,
    val illuminant1Cct: Double,
    val illuminant2Cct: Double,
)

/** D50 → linear sRGB/Rec.709 (D65), Bradford-adapted. */
val XYZ_D50_TO_LINEAR_SRGB = Mat3.of(
    3.1338561, -1.6168667, -0.4906146,
    -0.9787684, 1.9161415, 0.0334540,
    0.0719453, -0.2289914, 1.4052427,
)

private const val WHITE_SEARCH_STEPS = 6

/** Weight of illuminant 1 for a scene at [cct], interpolated in inverse temperature as the DNG spec does. */
fun illuminantWeight(sensor: SensorColor, cct: Double): Double {
    val inv = 1.0 / cct
    val inv1 = 1.0 / sensor.illuminant1Cct
    val inv2 = 1.0 / sensor.illuminant2Cct
    return ((inv - inv2) / (inv1 - inv2)).coerceIn(0.0, 1.0)
}

/** XYZ → camera native space for a scene lit at the given illuminant weight. */
fun xyzToCamera(sensor: SensorColor, weight: Double): Mat3 =
    Mat3.lerp(sensor.calibration2, sensor.calibration1, weight) *
        Mat3.lerp(sensor.colorMatrix2, sensor.colorMatrix1, weight)

/** Scene white chromaticity implied by a camera neutral, found by fixed-point iteration. */
fun whiteXyForNeutral(sensor: SensorColor, neutral: Vec3): Xy {
    var weight = 0.5
    var xy = Xy(0.3457, 0.3585)
    repeat(WHITE_SEARCH_STEPS) {
        xy = (xyzToCamera(sensor, weight).inverse() * neutral).toXy()
        weight = illuminantWeight(sensor, cctFromXy(xy))
    }
    return xy
}

/**
 * Splits the DNG camera → XYZ(D50) transform into per-channel white-balance
 * gains (applied before demosaic) and a 3×3 matrix to linear Rec.709 (after).
 */
data class ColorTransform(val whiteBalanceGains: Vec3, val cameraToWorking: Mat3)

fun colorTransform(sensor: SensorColor, neutral: Vec3): ColorTransform {
    val weight = illuminantWeight(sensor, cctFromXy(whiteXyForNeutral(sensor, neutral)))
    val calibration = Mat3.lerp(sensor.calibration2, sensor.calibration1, weight)
    val forward = Mat3.lerp(sensor.forwardMatrix2, sensor.forwardMatrix1, weight)
    val referenceNeutral = calibration.inverse() * neutral
    val dngWhite = referenceNeutral.map { 1.0 / it }
    val gains = neutral.map { 1.0 / it }.let { it / it.min() }
    val cameraToXyz = forward * Mat3.diagonal(dngWhite) * calibration.inverse() * Mat3.diagonal(gains.map { 1.0 / it })
    return ColorTransform(gains, XYZ_D50_TO_LINEAR_SRGB * cameraToXyz)
}
