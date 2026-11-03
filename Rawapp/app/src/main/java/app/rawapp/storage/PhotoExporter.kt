package app.rawapp.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.heifwriter.HeifWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val EXPORTS_RELATIVE_PATH = "Pictures/Rawapp/Exports/"
private const val JPEG_QUALITY = 95
private const val HEIC_QUALITY = 92
private const val HEIC_TIMEOUT_MS = 10_000L

/** Saves a developed, upright bitmap as JPEG or HEIC into Pictures/Rawapp/Exports via MediaStore. */
class PhotoExporter(private val resolver: ContentResolver) {

    fun export(bitmap: Bitmap, format: ExportFormat, takenAt: Date = Date()): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName(takenAt, format))
            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, EXPORTS_RELATIVE_PATH)
            put(MediaStore.Images.Media.DATE_TAKEN, takenAt.time)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore no creó el archivo")
        try {
            when (format) {
                ExportFormat.Jpeg -> writeJpeg(uri, bitmap)
                ExportFormat.Heic -> writeHeic(uri, bitmap)
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw IOException("No se pudo guardar el ${format.label}: ${e.message}", e)
        }
    }

    private fun writeJpeg(uri: Uri, bitmap: Bitmap) {
        val written = resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        if (written != true) throw IOException("No se pudo escribir el JPEG")
    }

    private fun writeHeic(uri: Uri, bitmap: Bitmap) {
        val pfd = resolver.openFileDescriptor(uri, "w") ?: throw IOException("No se pudo abrir el archivo de destino")
        pfd.use {
            HeifWriter.Builder(it.fileDescriptor, bitmap.width, bitmap.height, HeifWriter.INPUT_MODE_BITMAP)
                .setQuality(HEIC_QUALITY)
                .build()
                .use { writer ->
                    writer.start()
                    writer.addBitmap(bitmap)
                    writer.stop(HEIC_TIMEOUT_MS)
                }
        }
    }

    private fun fileName(date: Date, format: ExportFormat): String =
        "RAWAPP_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(date) + "." + format.extension
}
