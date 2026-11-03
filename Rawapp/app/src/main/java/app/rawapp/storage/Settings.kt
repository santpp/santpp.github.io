package app.rawapp.storage

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(val exportFormat: ExportFormat = ExportFormat.Jpeg, val autoExport: Boolean = false)

/** User preferences persisted in SharedPreferences and exposed as a StateFlow. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("rawapp_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        Settings(
            exportFormat = ExportFormat.entries.firstOrNull { it.name == prefs.getString(KEY_FORMAT, null) } ?: ExportFormat.Jpeg,
            autoExport = prefs.getBoolean(KEY_AUTO_EXPORT, false),
        ),
    )
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun setExportFormat(format: ExportFormat) {
        prefs.edit().putString(KEY_FORMAT, format.name).apply()
        _settings.value = _settings.value.copy(exportFormat = format)
    }

    fun setAutoExport(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_EXPORT, enabled).apply()
        _settings.value = _settings.value.copy(autoExport = enabled)
    }

    private companion object {
        const val KEY_FORMAT = "export_format"
        const val KEY_AUTO_EXPORT = "auto_export"
    }
}
