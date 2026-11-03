package app.rawapp.camera

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Owns one CameraDevice with a preview stream and a RAW_SENSOR stream.
 * All camera callbacks run on a dedicated background thread.
 */
class CameraController(
    private val manager: CameraManager,
    private val onDeviceLost: (String) -> Unit,
) {
    private val thread = HandlerThread("rawapp-camera").apply { start() }
    private val handler = Handler(thread.looper)
    private val executor = Executor { handler.post(it) }

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var rawReader: ImageReader? = null
    private var characteristics: CameraCharacteristics? = null
    private var previewSurface: Surface? = null
    private var physicalId: String? = null
    private var manual: ManualExposure? = null

    /** Latest auto-exposure values from the preview, used to seed Pro mode. */
    @Volatile
    var lastAutoExposure: ManualExposure? = null
        private set

    /** Switches between auto exposure (null) and fixed ISO/shutter; applies to preview and captures. */
    fun setManualExposure(value: ManualExposure?) {
        manual = value
        val dev = device ?: return
        val ses = session ?: return
        val preview = previewSurface ?: return
        startPreview(dev, ses, preview)
    }

    suspend fun open(lens: Lens, preview: Surface) {
        close()
        val chars = manager.getCameraCharacteristics(lens.physicalId ?: lens.logicalId)
        val rawSize = largestRawSize(chars) ?: throw CameraException("La lente ${lens.label} no ofrece RAW_SENSOR")
        val reader = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 2)
        rawReader = reader
        characteristics = chars
        previewSurface = preview
        physicalId = lens.physicalId

        val opened = openDevice(lens.logicalId)
        device = opened
        val created = createSession(opened, listOf(preview, reader.surface), lens.physicalId)
        session = created
        startPreview(opened, created, preview)
    }

    suspend fun captureRaw(): RawCapture {
        val dev = device ?: throw CameraException("La cámara no está abierta")
        val ses = session ?: throw CameraException("La sesión de cámara no está lista")
        val reader = rawReader ?: throw CameraException("El lector RAW no está listo")
        val chars = characteristics ?: throw CameraException("Faltan las características de la cámara")

        val imageReady = CompletableDeferred<Image>()
        val resultReady = CompletableDeferred<TotalCaptureResult>()
        reader.setOnImageAvailableListener({ r ->
            r.acquireNextImage()?.let { if (!imageReady.complete(it)) it.close() }
        }, handler)

        val request = dev.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(reader.surface)
            previewSurface?.let(::addTarget)
            set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON)
            applyExposure(this)
        }.build()

        ses.capture(request, object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) {
                resultReady.complete(result)
            }

            override fun onCaptureFailed(s: CameraCaptureSession, r: CaptureRequest, failure: CaptureFailure) {
                val error = CameraException("La captura falló (motivo ${failure.reason})")
                resultReady.completeExceptionally(error)
                imageReady.completeExceptionally(error)
            }
        }, handler)

        val result = try {
            resultReady.await()
        } catch (e: Exception) {
            discard(imageReady)
            throw e
        }
        val lensResult = physicalId?.let { id ->
            result.physicalCameraTotalResults[id] ?: run {
                discard(imageReady)
                throw CameraException("Falta el resultado de la lente física $id")
            }
        } ?: result
        return RawCapture(imageReady.await(), lensResult, chars)
    }

    private suspend fun discard(pending: CompletableDeferred<Image>) {
        if (pending.isCompleted) runCatching { pending.await() }.getOrNull()?.close() else pending.cancel()
    }

    fun close() {
        session?.close()
        session = null
        device?.close()
        device = null
        rawReader?.close()
        rawReader = null
        characteristics = null
        previewSurface = null
        physicalId = null
        lastAutoExposure = null
    }

    fun release() {
        close()
        thread.quitSafely()
    }

    @SuppressLint("MissingPermission")
    private suspend fun openDevice(cameraId: String): CameraDevice =
        suspendCancellableCoroutine { cont ->
            val callback = object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (cont.isActive) cont.resume(camera) else camera.close()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    if (cont.isActive) {
                        cont.resumeWithException(CameraException("La cámara se desconectó"))
                    } else {
                        onDeviceLost("Otra app está usando la cámara")
                    }
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    val message = deviceErrorMessage(error)
                    if (cont.isActive) cont.resumeWithException(CameraException(message)) else onDeviceLost(message)
                }
            }
            try {
                manager.openCamera(cameraId, executor, callback)
            } catch (e: CameraAccessException) {
                cont.resumeWithException(CameraException("No se pudo abrir la cámara", e))
            }
        }

    private suspend fun createSession(
        dev: CameraDevice,
        surfaces: List<Surface>,
        physicalId: String?,
    ): CameraCaptureSession =
        suspendCancellableCoroutine { cont ->
            val outputs = surfaces.map { surface ->
                OutputConfiguration(surface).apply { physicalId?.let(::setPhysicalCameraId) }
            }
            val config = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputs,
                executor,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        if (cont.isActive) cont.resume(s) else s.close()
                    }

                    override fun onConfigureFailed(s: CameraCaptureSession) {
                        if (cont.isActive) cont.resumeWithException(CameraException("No se pudo configurar la sesión RAW"))
                    }
                },
            )
            dev.createCaptureSession(config)
        }

    private fun startPreview(dev: CameraDevice, ses: CameraCaptureSession, preview: Surface) {
        val request = dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(preview)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            applyExposure(this)
        }.build()
        ses.setRepeatingRequest(request, autoExposureTracker, handler)
    }

    private val autoExposureTracker = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) {
            if (manual != null) return
            val lensResult = physicalId?.let { result.physicalCameraTotalResults[it] } ?: result
            val iso = lensResult.get(CaptureResult.SENSOR_SENSITIVITY) ?: return
            val exposure = lensResult.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: return
            lastAutoExposure = ManualExposure(iso, exposure)
        }
    }

    /** Manual sensor keys go to the physical lens when streaming from one, and to the logical camera otherwise. */
    private fun applyExposure(builder: CaptureRequest.Builder) {
        val m = manual ?: return
        val physical = physicalId
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        fun <T> put(key: CaptureRequest.Key<T>, value: T) {
            builder.set(key, value)
            if (physical != null) builder.setPhysicalCameraKey(key, value, physical)
        }
        put(CaptureRequest.SENSOR_SENSITIVITY, m.iso)
        put(CaptureRequest.SENSOR_EXPOSURE_TIME, m.exposureNs)
        put(CaptureRequest.SENSOR_FRAME_DURATION, m.exposureNs)
    }

    private fun deviceErrorMessage(error: Int): String = when (error) {
        CameraDevice.StateCallback.ERROR_CAMERA_IN_USE,
        CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE -> "La cámara está en uso por otra app"
        CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> "La cámara está desactivada por política del sistema"
        CameraDevice.StateCallback.ERROR_CAMERA_DEVICE -> "Error interno de la cámara"
        CameraDevice.StateCallback.ERROR_CAMERA_SERVICE -> "Error del servicio de cámara"
        else -> "Error de cámara ($error)"
    }
}
