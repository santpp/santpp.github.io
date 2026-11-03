package app.rawapp.develop

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import app.rawapp.develop.gl.GlPipeline
import app.rawapp.develop.math.autoExposureEv
import app.rawapp.develop.math.colorTransform
import app.rawapp.develop.math.shiftedNeutral
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.pow

class DevelopResult(val bitmap: Bitmap, val gpuMillis: Long)

/** Develops [RawFrame]s on a dedicated GL thread. */
class RawDeveloper(private val assets: AssetManager) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "rawapp-gl") }
    private val dispatcher = executor.asCoroutineDispatcher()
    private var pipeline: GlPipeline? = null

    suspend fun develop(frame: RawFrame, params: LookParams, lookEnabled: Boolean): DevelopResult =
        withContext(dispatcher) {
            val gl = pipeline ?: GlPipeline(assets).also { pipeline = it }
            val neutral = shiftedNeutral(frame.sensor, frame.asShotNeutral, params.temperatureMired.toDouble())
            val color = colorTransform(frame.sensor, neutral)
            val ev = autoExposureEv(sampleGreen(frame)) + params.exposureEv
            val start = SystemClock.elapsedRealtime()
            val bitmap = gl.render(frame, color, 2.0.pow(ev).toFloat(), params, lookEnabled)
            val millis = SystemClock.elapsedRealtime() - start
            DevelopResult(bitmap.rotated(frame.rotationDegrees).centerCropped(params.zoom), millis)
        }

    override fun close() {
        executor.execute {
            pipeline?.close()
            pipeline = null
        }
        executor.shutdown()
    }
}

/** Digital zoom: keeps the centre 1/zoom of the frame. */
private fun Bitmap.centerCropped(zoom: Float): Bitmap {
    if (zoom <= 1f) return this
    val w = (width / zoom).toInt()
    val h = (height / zoom).toInt()
    val cropped = Bitmap.createBitmap(this, (width - w) / 2, (height - h) / 2, w, h)
    recycle()
    return cropped
}

private fun Bitmap.rotated(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val rotated = Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
    recycle()
    return rotated
}
