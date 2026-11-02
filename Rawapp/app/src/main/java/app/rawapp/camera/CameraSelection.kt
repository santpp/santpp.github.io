package app.rawapp.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
import android.view.SurfaceHolder

data class SelectedCamera(
    val id: String,
    val previewSize: Size,
    val sensorOrientation: Int,
)

private const val MAX_PREVIEW_PIXELS = 1920 * 1440

/** Picks the first back-facing logical camera that exposes RAW_SENSOR, or null. */
fun selectRawCamera(manager: CameraManager): SelectedCamera? {
    val id = manager.cameraIdList.firstOrNull { id ->
        val chars = manager.getCameraCharacteristics(id)
        chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK &&
            supportsRaw(chars) && largestRawSize(chars) != null
    } ?: return null
    val chars = manager.getCameraCharacteristics(id)
    val raw = largestRawSize(chars) ?: return null
    return SelectedCamera(
        id = id,
        previewSize = previewSizeMatching(chars, raw) ?: return null,
        sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0,
    )
}

private fun previewSizeMatching(chars: CameraCharacteristics, raw: Size): Size? {
    val sizes = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        ?.getOutputSizes(SurfaceHolder::class.java)
        ?: return null
    val targetRatio = raw.width.toDouble() / raw.height
    return sizes
        .filter { it.width * it.height <= MAX_PREVIEW_PIXELS }
        .sortedWith(
            compareBy<Size> { kotlin.math.abs(it.width.toDouble() / it.height - targetRatio) }
                .thenByDescending { it.width * it.height },
        )
        .firstOrNull()
}
