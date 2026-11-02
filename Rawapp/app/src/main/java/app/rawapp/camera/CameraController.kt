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

    suspend fun open(cameraId: String, preview: Surface) {
        close()
        val chars = manager.getCameraCharacteristics(cameraId)
        val rawSize = largestRawSize(chars) ?: throw CameraException("La cámara $cameraId no ofrece RAW_SENSOR")
        val reader = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 2)
        rawReader = reader
        characteristics = chars
        previewSurface = preview

        val opened = openDevice(cameraId)
        device = opened
        val created = createSession(opened, listOf(preview, reader.surface))
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
            imageReady.cancel()
            throw e
        }
        return RawCapture(imageReady.await(), result, chars)
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

    private suspend fun createSession(dev: CameraDevice, surfaces: List<Surface>): CameraCaptureSession =
        suspendCancellableCoroutine { cont ->
            val config = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                surfaces.map(::OutputConfiguration),
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
        }.build()
        ses.setRepeatingRequest(request, null, handler)
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
