package app.rawapp.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val EXPORTS_RELATIVE_PATH = "Pictures/Rawapp/Exports/"
private const val JPEG_QUALITY = 95

/** Saves a developed, upright bitmap as JPEG into Pictures/Rawapp/Exports via MediaStore. */
class JpegExporter(private val resolver: ContentResolver) {

    fun export(bitmap: Bitmap, takenAt: Date = Date()): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName(takenAt))
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, EXPORTS_RELATIVE_PATH)
            put(MediaStore.Images.Media.DATE_TAKEN, takenAt.time)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore no creó el archivo")
        try {
            val written = resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (written != true) throw IOException("No se pudo escribir el JPEG")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw IOException("No se pudo guardar el JPEG: ${e.message}", e)
        }
    }

    private fun fileName(date: Date): String =
        "RAWAPP_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(date) + ".jpg"
}
