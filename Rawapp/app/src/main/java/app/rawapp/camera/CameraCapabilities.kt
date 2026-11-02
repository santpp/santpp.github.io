package app.rawapp.camera

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.ColorSpaceTransform
import android.util.Range
import android.util.Size

data class CameraCapabilities(
    val id: String,
    val facing: String,
    val isPhysical: Boolean,
    val rawSupported: Boolean,
    val rawSize: Size?,
    val isoRange: Range<Int>?,
    val exposureRangeNs: Range<Long>?,
    val blackLevels: List<Int>,
    val whiteLevel: Int?,
    val cfaPattern: String,
    val colorMatrices: Map<String, List<List<Double>>>,
    val illuminants: List<String>,
)

fun readAllCapabilities(manager: CameraManager): List<CameraCapabilities> =
    manager.cameraIdList.flatMap { logicalId ->
        val logical = manager.getCameraCharacteristics(logicalId)
        val physical = logical.physicalCameraIds.map { physicalId ->
            readCapabilities(physicalId, manager.getCameraCharacteristics(physicalId), isPhysical = true)
        }
        listOf(readCapabilities(logicalId, logical, isPhysical = false)) + physical
    }

fun readCapabilities(
    id: String,
    chars: CameraCharacteristics,
    isPhysical: Boolean,
): CameraCapabilities {
    val rawSize = largestRawSize(chars)
    val blackPattern = chars.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)
    return CameraCapabilities(
        id = id,
        facing = facingLabel(chars.get(CameraCharacteristics.LENS_FACING)),
        isPhysical = isPhysical,
        rawSupported = supportsRaw(chars) && rawSize != null,
        rawSize = rawSize,
        isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE),
        exposureRangeNs = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE),
        blackLevels = blackPattern?.let { p ->
            listOf(p.getOffsetForIndex(0, 0), p.getOffsetForIndex(1, 0), p.getOffsetForIndex(0, 1), p.getOffsetForIndex(1, 1))
        }.orEmpty(),
        whiteLevel = chars.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL),
        cfaPattern = cfaLabel(chars.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)),
        colorMatrices = listOf(
            "ColorMatrix1" to CameraCharacteristics.SENSOR_COLOR_TRANSFORM1,
            "ColorMatrix2" to CameraCharacteristics.SENSOR_COLOR_TRANSFORM2,
            "ForwardMatrix1" to CameraCharacteristics.SENSOR_FORWARD_MATRIX1,
            "ForwardMatrix2" to CameraCharacteristics.SENSOR_FORWARD_MATRIX2,
            "Calibration1" to CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1,
            "Calibration2" to CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2,
        ).mapNotNull { (name, key) -> chars.get(key)?.let { name to it.toRows() } }.toMap(),
        illuminants = listOfNotNull(
            chars.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1)?.let { "1: ${illuminantLabel(it)}" },
            chars.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2)?.let { "2: ${illuminantLabel(it.toInt())}" },
        ),
    )
}

fun supportsRaw(chars: CameraCharacteristics): Boolean =
    chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        ?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW) == true

fun largestRawSize(chars: CameraCharacteristics): Size? =
    chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        ?.getOutputSizes(ImageFormat.RAW_SENSOR)
        ?.maxByOrNull { it.width.toLong() * it.height }

private fun ColorSpaceTransform.toRows(): List<List<Double>> =
    (0 until 3).map { row -> (0 until 3).map { col -> getElement(col, row).toDouble() } }

private fun facingLabel(facing: Int?): String = when (facing) {
    CameraCharacteristics.LENS_FACING_BACK -> "Trasera"
    CameraCharacteristics.LENS_FACING_FRONT -> "Frontal"
    CameraCharacteristics.LENS_FACING_EXTERNAL -> "Externa"
    else -> "Desconocida"
}

private fun cfaLabel(cfa: Int?): String = when (cfa) {
    CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB -> "RGGB"
    CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG -> "GRBG"
    CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG -> "GBRG"
    CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR -> "BGGR"
    CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGB -> "RGB"
    CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_MONO -> "Mono"
    CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_NIR -> "NIR"
    else -> "—"
}

private fun illuminantLabel(value: Int): String = when (value) {
    CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT -> "Luz de día"
    CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_D50 -> "D50"
    CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_D55 -> "D55"
    CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_D65 -> "D65"
    CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_D75 -> "D75"
    CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_STANDARD_A -> "Estándar A"
    CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_TUNGSTEN -> "Tungsteno"
    CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_FLUORESCENT -> "Fluorescente"
    CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_CLOUDY_WEATHER -> "Nublado"
    else -> "Código $value"
}
