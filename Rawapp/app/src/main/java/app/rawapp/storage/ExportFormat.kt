package app.rawapp.storage

import android.net.Uri

enum class ExportFormat(val label: String, val mimeType: String, val extension: String) {
    Jpeg("JPEG", "image/jpeg", "jpg"),
    Heic("HEIC", "image/heic", "heic"),
}

/** An exported image ready to hand to the share sheet. */
data class SharedFile(val uri: Uri, val mimeType: String)
