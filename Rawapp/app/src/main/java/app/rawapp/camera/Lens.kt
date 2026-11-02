package app.rawapp.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
import android.view.SurfaceHolder
import kotlin.math.abs

/**
 * A selectable lens. Back lenses inside a logical multi-camera are reached by
 * opening [logicalId] and routing streams to [physicalId].
 */
data class Lens(
    val logicalId: String,
    val physicalId: String?,
    val label: String,
    val frontFacing: Boolean,
    val previewSize: Size,
    val sensorOrientation: Int,
)

private const val MAX_PREVIEW_PIXELS = 1920 * 1440
private const val ULTRA_WIDE_RATIO = 0.75f

private class Candidate(
    val logicalId: String,
    val physicalId: String?,
    val chars: CameraCharacteristics,
    val frontFacing: Boolean,
)

/** Every RAW-capable lens, back lenses ordered from widest to tightest, then the front one. */
fun findRawLenses(manager: CameraManager): List<Lens> {
    val candidates = manager.cameraIdList.flatMap { logicalId ->
        val logical = manager.getCameraCharacteristics(logicalId)
        val front = logical.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        val physical = logical.physicalCameraIds
            .map { Candidate(logicalId, it, manager.getCameraCharacteristics(it), front) }
            .filter { isRawCapable(it.chars) }
        physical.ifEmpty {
            listOfNotNull(Candidate(logicalId, null, logical, front).takeIf { isRawCapable(logical) })
        }
    }
    val back = candidates.filterNot { it.frontFacing }.sortedBy { normalisedFocal(it.chars) }
    val main = mainBackLens(back)
    val backLenses = back.mapNotNull { c ->
        val zoom = main?.let { zoomRelativeTo(c.chars, it.chars) } ?: 1f
        toLens(c, formatZoom(zoom))
    }
    val frontLenses = candidates.filter { it.frontFacing }.take(1).mapNotNull { toLens(it, "Frontal") }
    return backLenses + frontLenses
}

/** The "1×" lens: the second-widest when the widest is clearly an ultra-wide. */
private fun mainBackLens(sortedBack: List<Candidate>): Candidate? {
    if (sortedBack.size < 2) return sortedBack.firstOrNull()
    val widestIsUltraWide = zoomRelativeTo(sortedBack[0].chars, sortedBack[1].chars) < ULTRA_WIDE_RATIO
    return if (widestIsUltraWide) sortedBack[1] else sortedBack[0]
}

private fun isRawCapable(chars: CameraCharacteristics) = supportsRaw(chars) && largestRawSize(chars) != null

private fun toLens(c: Candidate, label: String): Lens? {
    val raw = largestRawSize(c.chars) ?: return null
    return Lens(
        logicalId = c.logicalId,
        physicalId = c.physicalId,
        label = label,
        frontFacing = c.frontFacing,
        previewSize = previewSizeMatching(c.chars, raw) ?: return null,
        sensorOrientation = c.chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0,
    )
}

/** Focal length divided by sensor width: proportional to the lens' magnification. */
private fun normalisedFocal(chars: CameraCharacteristics): Float {
    val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: return 1f
    val width = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.width ?: return 1f
    return focal / width
}

private fun zoomRelativeTo(chars: CameraCharacteristics, reference: CameraCharacteristics): Float =
    normalisedFocal(chars) / normalisedFocal(reference)

private fun previewSizeMatching(chars: CameraCharacteristics, raw: Size): Size? {
    val sizes = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        ?.getOutputSizes(SurfaceHolder::class.java)
        ?: return null
    val targetRatio = raw.width.toDouble() / raw.height
    return sizes
        .filter { it.width * it.height <= MAX_PREVIEW_PIXELS }
        .sortedWith(
            compareBy<Size> { abs(it.width.toDouble() / it.height - targetRatio) }
                .thenByDescending { it.width * it.height },
        )
        .firstOrNull()
}
