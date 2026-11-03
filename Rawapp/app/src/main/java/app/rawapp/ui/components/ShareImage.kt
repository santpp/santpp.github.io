package app.rawapp.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens the system share sheet for one image the app owns in MediaStore. */
fun Context.shareImage(uri: Uri, mimeType: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(send, "Compartir foto"))
}
