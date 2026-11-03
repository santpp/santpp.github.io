package app.rawapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rawapp.storage.ExportFormat
import app.rawapp.storage.SettingsStore
import app.rawapp.ui.components.GlassIconButton
import app.rawapp.ui.components.GlassSegmented
import app.rawapp.ui.components.glass
import app.rawapp.ui.theme.RawappColors

@Composable
fun SettingsScreen(store: SettingsStore, onBack: () -> Unit) {
    val settings by store.settings.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Volver a la cámara", onBack)
            Text("Ajustes", style = MaterialTheme.typography.titleLarge, color = RawappColors.TextPrimary)
        }
        Section(
            title = "Formato de exportación",
            body = "HEIC ocupa aproximadamente la mitad que JPEG con la misma calidad. JPEG es compatible con todo.",
        ) {
            GlassSegmented(
                options = ExportFormat.entries,
                selected = settings.exportFormat,
                label = ExportFormat::label,
                onSelect = store::setExportFormat,
            )
        }
        Section(
            title = "Auto-exportar",
            body = "Cada disparo guarda además una foto revelada con el look en Pictures/Rawapp/Exports.",
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (settings.autoExport) "Activado" else "Desactivado",
                    style = MaterialTheme.typography.bodyLarge,
                    color = RawappColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = settings.autoExport,
                    onCheckedChange = store::setAutoExport,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = RawappColors.Background,
                        checkedTrackColor = RawappColors.Accent,
                        uncheckedThumbColor = RawappColors.TextSecondary,
                        uncheckedTrackColor = RawappColors.GlassFill,
                    ),
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, body: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().glass().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = RawappColors.TextPrimary)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = RawappColors.TextSecondary)
        content()
    }
}
