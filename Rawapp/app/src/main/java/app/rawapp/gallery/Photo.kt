package app.rawapp.gallery

import android.net.Uri

data class Photo(val id: Long, val uri: Uri, val displayName: String, val takenAt: Long)
