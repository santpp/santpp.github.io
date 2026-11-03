package app.rawapp.gallery

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import app.rawapp.develop.LookParams
import app.rawapp.develop.RawFrame
import app.rawapp.develop.dng.parseDng
import java.io.File
import java.io.IOException

private const val RAWAPP_PATH = "Pictures/Rawapp/"
private const val THUMB_QUALITY = 88

/**
 * Rawapp's DNGs in MediaStore, their sidecar adjustments and cached thumbnails.
 * Sidecars live in app storage keyed by file name (MediaStore does not accept
 * JSON files under Pictures/); the DNG itself is never written.
 */
class GalleryRepository(context: Context) {
    private val resolver = context.contentResolver
    private val sidecarDir = File(context.filesDir, "sidecars").apply { mkdirs() }
    private val thumbDir = File(context.cacheDir, "thumbs").apply { mkdirs() }

    fun listPhotos(): List<Photo> {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
        )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val args = arrayOf(RAWAPP_PATH, "%.dng")
        val order = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        val cursor = resolver.query(collection, projection, selection, args, order)
            ?: throw IOException("No se pudo consultar la galería")
        return cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            buildList {
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    add(Photo(id, ContentUris.withAppendedId(collection, id), c.getString(nameCol), c.getLong(dateCol)))
                }
            }
        }
    }

    fun readFrame(photo: Photo): RawFrame {
        val bytes = resolver.openInputStream(photo.uri)?.use { it.readBytes() }
            ?: throw IOException("No se pudo abrir ${photo.displayName}")
        return parseDng(bytes)
    }

    fun loadParams(photo: Photo): LookParams {
        val file = sidecarFile(photo)
        return if (file.exists()) SidecarCodec.decode(file.readText()) else LookParams()
    }

    fun saveParams(photo: Photo, params: LookParams) {
        val file = sidecarFile(photo)
        val tmp = File(file.path + ".tmp")
        tmp.writeText(SidecarCodec.encode(params))
        if (!tmp.renameTo(file)) throw IOException("No se pudo guardar los ajustes")
        thumbDir.listFiles { f -> f.name.startsWith("${photo.id}_") }?.forEach(File::delete)
    }

    fun cachedThumbnail(photo: Photo, params: LookParams): Bitmap? =
        thumbFile(photo, params).takeIf(File::exists)?.let { BitmapFactory.decodeFile(it.path) }

    fun cacheThumbnail(photo: Photo, params: LookParams, bitmap: Bitmap) {
        thumbFile(photo, params).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, it) }
    }

    private fun sidecarFile(photo: Photo) = File(sidecarDir, photo.displayName.substringBeforeLast('.') + ".json")

    private fun thumbFile(photo: Photo, params: LookParams) = File(thumbDir, "${photo.id}_${params.hashCode()}.jpg")
}
