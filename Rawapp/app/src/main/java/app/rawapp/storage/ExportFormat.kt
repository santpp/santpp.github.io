package app.rawapp.storage

enum class ExportFormat(val label: String, val mimeType: String, val extension: String) {
    Jpeg("JPEG", "image/jpeg", "jpg"),
    Heic("HEIC", "image/heic", "heic"),
}
