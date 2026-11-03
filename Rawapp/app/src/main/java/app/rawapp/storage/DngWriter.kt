package app.rawapp.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.hardware.camera2.DngCreator
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import app.rawapp.camera.RawCapture
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val RAWAPP_RELATIVE_PATH = "Pictures/Rawapp/"
private const val DNG_MIME = "image/x-adobe-dng"

data class SavedDng(val uri: Uri, val displayName: String)

/** Writes a RAW capture as DNG into Pictures/Rawapp via MediaStore. */
class DngWriter(private val resolver: ContentResolver) {

    /** Returns the new file's URI and display name. */
    fun write(capture: RawCapture, rotationDegrees: Int, takenAt: Date = Date()): SavedDng {
        val name = fileName(takenAt)
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, DNG_MIME)
            put(MediaStore.Images.Media.RELATIVE_PATH, RAWAPP_RELATIVE_PATH)
            put(MediaStore.Images.Media.DATE_TAKEN, takenAt.time)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore no creó el archivo")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                DngCreator(capture.characteristics, capture.result).use { dng ->
                    dng.setOrientation(exifOrientation(rotationDegrees))
                    dng.writeImage(out, capture.image)
                }
            } ?: throw IOException("No se pudo abrir el archivo de destino")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            return SavedDng(uri, name)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw IOException("No se pudo guardar el DNG: ${e.message}", e)
        }
    }

    private fun fileName(date: Date): String =
        "RAW_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(date) + ".dng"

    private fun exifOrientation(degrees: Int): Int = when (degrees) {
        90 -> ExifInterface.ORIENTATION_ROTATE_90
        180 -> ExifInterface.ORIENTATION_ROTATE_180
        270 -> ExifInterface.ORIENTATION_ROTATE_270
        else -> ExifInterface.ORIENTATION_NORMAL
    }
}
