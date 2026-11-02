package app.rawapp.develop.math

/** Calibration reported by the Pixel 10a main camera (Diagnóstico screen). */
val PIXEL_MAIN = SensorColor(
    colorMatrix1 = Mat3.of(1.3051, -0.7144, 0.0272, -0.3461, 1.3205, 0.1678, 0.0021, 0.1578, 0.7296),
    colorMatrix2 = Mat3.of(0.8111, -0.1924, -0.1172, -0.5652, 1.4300, 0.0984, -0.1816, 0.3885, 0.4438),
    forwardMatrix1 = Mat3.of(0.5498, 0.3084, 0.1061, 0.1168, 0.9261, -0.0429, -0.0783, -0.5294, 1.4329),
    forwardMatrix2 = Mat3.of(0.6524, 0.1464, 0.1655, 0.2572, 0.7862, -0.0434, 0.0292, -0.4742, 1.2701),
    calibration1 = Mat3.diagonal(Vec3(0.9973, 1.0, 0.9923)),
    calibration2 = Mat3.diagonal(Vec3(1.0019, 1.0, 0.9927)),
    illuminant1Cct = 2856.0,
    illuminant2Cct = 6504.0,
)

/** Camera response to a D65 white, from the D65 colour matrix. */
val D65_NEUTRAL: Vec3 = run {
    val n = xyzToCamera(PIXEL_MAIN, 0.0) * Xy(0.3127, 0.3290).toXyz()
    n / n.y
}
