package app.rawapp.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.TotalCaptureResult
import android.media.Image

class RawCapture(
    val image: Image,
    val result: TotalCaptureResult,
    val characteristics: CameraCharacteristics,
) : AutoCloseable {
    override fun close() = image.close()
}
