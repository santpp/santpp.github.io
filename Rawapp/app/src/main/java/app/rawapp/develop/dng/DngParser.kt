package app.rawapp.develop.dng

import app.rawapp.develop.CfaPattern
import app.rawapp.develop.RawFrame
import app.rawapp.develop.math.Mat3
import app.rawapp.develop.math.SensorColor
import app.rawapp.develop.math.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG_WIDTH = 256
private const val TAG_HEIGHT = 257
private const val TAG_BITS = 258
private const val TAG_COMPRESSION = 259
private const val TAG_PHOTOMETRIC = 262
private const val TAG_STRIP_OFFSETS = 273
private const val TAG_ORIENTATION = 274
private const val TAG_STRIP_BYTES = 279
private const val TAG_CFA_PATTERN = 33422
private const val TAG_BLACK_LEVEL = 50714
private const val TAG_WHITE_LEVEL = 50717
private const val TAG_COLOR_MATRIX_1 = 50721
private const val TAG_COLOR_MATRIX_2 = 50722
private const val TAG_CALIBRATION_1 = 50723
private const val TAG_CALIBRATION_2 = 50724
private const val TAG_AS_SHOT_NEUTRAL = 50728
private const val TAG_ILLUMINANT_1 = 50778
private const val TAG_ILLUMINANT_2 = 50779
private const val TAG_FORWARD_MATRIX_1 = 50964
private const val TAG_FORWARD_MATRIX_2 = 50965
private const val TAG_OPCODE_LIST_2 = 51009
private const val PHOTOMETRIC_CFA = 32803

/** Parses an uncompressed 16-bit Bayer DNG (as written by DngCreator) into a [RawFrame]. */
fun parseDng(bytes: ByteArray): RawFrame {
    val tiff = TiffReader(bytes)
    val directories = tiff.directories()
    val raw = directories.firstOrNull { it[TAG_PHOTOMETRIC]?.int() == PHOTOMETRIC_CFA }
        ?: throw DngFormatException("El DNG no contiene datos Bayer")
    val meta = directories.first()
    fun tag(t: Int) = raw[t] ?: meta.require(t)

    if (raw[TAG_COMPRESSION]?.int() != 1) throw DngFormatException("DNG comprimido no soportado")
    if (raw[TAG_BITS]?.int() != 16) throw DngFormatException("Solo se admiten DNG de 16 bits")
    val width = raw.require(TAG_WIDTH).int()
    val height = raw.require(TAG_HEIGHT).int()
    val cfa = cfaFromPattern(raw.require(TAG_CFA_PATTERN).ints())

    return RawFrame(
        width = width,
        height = height,
        pixels = readStrips(tiff.buffer, raw, width, height),
        cfa = cfa,
        blackLevels = blackLevels(tag(TAG_BLACK_LEVEL).doubles()),
        whiteLevel = tag(TAG_WHITE_LEVEL).int().toFloat(),
        asShotNeutral = tag(TAG_AS_SHOT_NEUTRAL).doubles().let { Vec3(it[0], it[1], it[2]) },
        sensor = SensorColor(
            colorMatrix1 = matrix(tag(TAG_COLOR_MATRIX_1)),
            colorMatrix2 = matrix(tag(TAG_COLOR_MATRIX_2)),
            forwardMatrix1 = matrix(tag(TAG_FORWARD_MATRIX_1)),
            forwardMatrix2 = matrix(tag(TAG_FORWARD_MATRIX_2)),
            calibration1 = (raw[TAG_CALIBRATION_1] ?: meta[TAG_CALIBRATION_1])?.let(::matrix) ?: Mat3.IDENTITY,
            calibration2 = (raw[TAG_CALIBRATION_2] ?: meta[TAG_CALIBRATION_2])?.let(::matrix) ?: Mat3.IDENTITY,
            illuminant1Cct = illuminantCct(tag(TAG_ILLUMINANT_1).int()),
            illuminant2Cct = illuminantCct(tag(TAG_ILLUMINANT_2).int()),
        ),
        lensShading = (raw[TAG_OPCODE_LIST_2] ?: meta[TAG_OPCODE_LIST_2])?.let { gainMapFromOpcodeList(it.bytes(), cfa) },
        rotationDegrees = rotationFromOrientation((meta[TAG_ORIENTATION] ?: raw[TAG_ORIENTATION])?.int() ?: 1),
    )
}

private fun readStrips(buffer: ByteBuffer, raw: TiffDirectory, width: Int, height: Int): ByteBuffer {
    val offsets = raw.require(TAG_STRIP_OFFSETS).ints()
    val counts = raw.require(TAG_STRIP_BYTES).ints()
    val expected = width * height * 2
    if (counts.sum() < expected) throw DngFormatException("Datos RAW incompletos")
    val out = ByteBuffer.allocateDirect(expected).order(ByteOrder.nativeOrder())
    val shorts = out.asShortBuffer()
    var remaining = width * height
    for (strip in offsets.indices) {
        var pos = offsets[strip]
        val samples = minOf(counts[strip] / 2, remaining)
        if (pos + samples * 2 > buffer.limit()) throw DngFormatException("Tira RAW fuera del archivo")
        repeat(samples) {
            shorts.put(buffer.getShort(pos))
            pos += 2
        }
        remaining -= samples
    }
    out.rewind()
    return out
}

private fun cfaFromPattern(pattern: IntArray): CfaPattern {
    val red = pattern.indexOf(0)
    if (pattern.size != 4 || red < 0 || pattern.indexOf(2) != 3 - red) throw DngFormatException("Patrón CFA no Bayer")
    return CfaPattern.entries.first { it.redX == red % 2 && it.redY == red / 2 }
}

private fun blackLevels(values: DoubleArray): FloatArray =
    FloatArray(4) { i -> values[if (values.size >= 4) i else 0].toFloat() }

private fun matrix(entry: TiffEntry): Mat3 {
    val v = entry.doubles()
    if (v.size != 9) throw DngFormatException("Matriz de color de tamaño ${v.size}")
    return Mat3(v)
}

/** EXIF light-source codes used by DngCreator for the reference illuminants. */
internal fun illuminantCct(code: Int): Double = when (code) {
    17, 3 -> 2856.0
    23 -> 5003.0
    20 -> 5503.0
    21, 1 -> 6504.0
    22 -> 7504.0
    else -> throw DngFormatException("Iluminante de referencia no soportado ($code)")
}

internal fun rotationFromOrientation(orientation: Int): Int = when (orientation) {
    6 -> 90
    3 -> 180
    8 -> 270
    else -> 0
}
