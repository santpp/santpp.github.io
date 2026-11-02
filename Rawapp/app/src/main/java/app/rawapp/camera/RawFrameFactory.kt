package app.rawapp.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import app.rawapp.develop.CfaPattern
import app.rawapp.develop.GainMap
import app.rawapp.develop.RawFrame
import app.rawapp.develop.math.Mat3
import app.rawapp.develop.math.SensorColor
import app.rawapp.develop.math.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Copies the RAW16 plane and metadata out of a capture so the Image can be closed. */
fun rawFrameFrom(capture: RawCapture, rotationDegrees: Int): RawFrame {
    val chars = capture.characteristics
    val result = capture.result
    val image = capture.image
    val neutral = result.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT)
        ?: throw CameraException("La captura no trae el punto neutro")
    return RawFrame(
        width = image.width,
        height = image.height,
        pixels = copyPlane(capture),
        cfa = cfaPattern(chars),
        blackLevels = result.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL) ?: patternBlackLevels(chars),
        whiteLevel = (result.get(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL)
            ?: chars.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL)
            ?: throw CameraException("Falta el white level")).toFloat(),
        asShotNeutral = Vec3(neutral[0].toDouble(), neutral[1].toDouble(), neutral[2].toDouble()),
        sensor = sensorColor(chars),
        lensShading = result.get(CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP)?.let { map ->
            val gains = FloatArray(map.gainFactorCount)
            map.copyGainFactors(gains, 0)
            GainMap(map.columnCount, map.rowCount, gains)
        },
        rotationDegrees = rotationDegrees,
    )
}

private fun copyPlane(capture: RawCapture): ByteBuffer {
    val image = capture.image
    val plane = image.planes[0]
    val rowBytes = image.width * 2
    val src = plane.buffer.duplicate()
    val dst = ByteBuffer.allocateDirect(rowBytes * image.height).order(ByteOrder.nativeOrder())
    for (row in 0 until image.height) {
        src.limit(row * plane.rowStride + rowBytes)
        src.position(row * plane.rowStride)
        dst.put(src)
    }
    dst.rewind()
    return dst
}

private fun cfaPattern(chars: CameraCharacteristics): CfaPattern =
    when (chars.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)) {
        CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB -> CfaPattern.RGGB
        CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG -> CfaPattern.GRBG
        CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG -> CfaPattern.GBRG
        CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR -> CfaPattern.BGGR
        else -> throw CameraException("Patrón de color del sensor no Bayer")
    }

private fun patternBlackLevels(chars: CameraCharacteristics): FloatArray {
    val pattern = chars.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)
        ?: throw CameraException("Falta el black level")
    return floatArrayOf(
        pattern.getOffsetForIndex(0, 0).toFloat(),
        pattern.getOffsetForIndex(1, 0).toFloat(),
        pattern.getOffsetForIndex(0, 1).toFloat(),
        pattern.getOffsetForIndex(1, 1).toFloat(),
    )
}

private fun sensorColor(chars: CameraCharacteristics): SensorColor {
    fun matrix(key: CameraCharacteristics.Key<ColorSpaceTransform>): Mat3 {
        val t = chars.get(key) ?: throw CameraException("Faltan matrices de color del sensor")
        return Mat3(DoubleArray(9) { i -> t.getElement(i % 3, i / 3).toDouble() })
    }
    return SensorColor(
        colorMatrix1 = matrix(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1),
        colorMatrix2 = matrix(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2),
        forwardMatrix1 = matrix(CameraCharacteristics.SENSOR_FORWARD_MATRIX1),
        forwardMatrix2 = matrix(CameraCharacteristics.SENSOR_FORWARD_MATRIX2),
        calibration1 = matrix(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1),
        calibration2 = matrix(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2),
        illuminant1Cct = illuminantCct(chars.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1)),
        illuminant2Cct = illuminantCct(chars.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2)?.toInt()),
    )
}

private fun illuminantCct(code: Int?): Double = when (code) {
    CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_STANDARD_A,
    CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_TUNGSTEN -> 2856.0
    CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_D50 -> 5003.0
    CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_D55 -> 5503.0
    CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_D65,
    CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT -> 6504.0
    CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_D75 -> 7504.0
    else -> throw CameraException("Iluminante de referencia no soportado ($code)")
}
