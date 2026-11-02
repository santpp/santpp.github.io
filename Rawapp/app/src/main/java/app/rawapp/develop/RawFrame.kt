package app.rawapp.develop

import app.rawapp.develop.math.SensorColor
import app.rawapp.develop.math.Vec3
import java.nio.ByteBuffer

/** Position of the red photosite inside the 2×2 Bayer tile. */
enum class CfaPattern(val redX: Int, val redY: Int) {
    RGGB(0, 0), GRBG(1, 0), GBRG(0, 1), BGGR(1, 1),
}

/** Low-resolution per-channel gain map (R, G even row, G odd row, B), row-major. */
class GainMap(val columns: Int, val rows: Int, val gains: FloatArray)

/**
 * Bayer RAW16 data plus everything needed to develop it. Independent of
 * Camera2, so captures and DNG files share the same path.
 */
class RawFrame(
    val width: Int,
    val height: Int,
    val pixels: ByteBuffer,
    val cfa: CfaPattern,
    val blackLevels: FloatArray,
    val whiteLevel: Float,
    val asShotNeutral: Vec3,
    val sensor: SensorColor,
    val lensShading: GainMap?,
    val rotationDegrees: Int,
)
