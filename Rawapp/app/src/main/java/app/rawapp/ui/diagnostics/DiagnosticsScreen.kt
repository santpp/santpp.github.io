package app.rawapp.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rawapp.camera.CameraCapabilities
import app.rawapp.ui.components.GlassIconButton
import app.rawapp.ui.components.GlassPill
import app.rawapp.ui.components.MessageScreen
import app.rawapp.ui.components.glass
import app.rawapp.ui.theme.RawappColors
import java.util.Locale

@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Volver", onBack)
            Text("Diagnóstico", style = MaterialTheme.typography.titleLarge, color = RawappColors.TextPrimary)
        }
        when (val state = uiState) {
            DiagnosticsUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = RawappColors.Accent, strokeWidth = 2.dp)
            }
            is DiagnosticsUiState.Error -> MessageScreen("No se pudo leer", state.message)
            is DiagnosticsUiState.Loaded -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.cameras, key = { "${it.isPhysical}-${it.id}" }) { CameraCard(it) }
            }
        }
    }
}

@Composable
private fun CameraCard(camera: CameraCapabilities) {
    Column(
        modifier = Modifier.fillMaxWidth().glass().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val kind = if (camera.isPhysical) "física" else "lógica"
            Text(
                "Cámara ${camera.id} · ${camera.facing} · $kind",
                style = MaterialTheme.typography.titleMedium,
                color = RawappColors.TextPrimary,
            )
            if (camera.rawSupported) {
                GlassPill("RAW", color = RawappColors.Accent)
            } else {
                GlassPill("Sin RAW", color = RawappColors.TextSecondary)
            }
        }
        Field("Resolución RAW", camera.rawSize?.let { "${it.width} × ${it.height}" } ?: "No disponible")
        Field("ISO", camera.isoRange?.let { "${it.lower} – ${it.upper}" } ?: "—")
        Field("Exposición", camera.exposureRangeNs?.let { "${formatExposure(it.lower)} – ${formatExposure(it.upper)}" } ?: "—")
        Field("Black level", camera.blackLevels.takeIf { it.isNotEmpty() }?.joinToString(" / ") ?: "—")
        Field("White level", camera.whiteLevel?.toString() ?: "—")
        Field("Patrón CFA", camera.cfaPattern)
        camera.illuminants.forEach { Field("Iluminante", it) }
        camera.colorMatrices.forEach { (name, rows) -> MatrixField(name, rows) }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = RawappColors.TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = RawappColors.TextPrimary)
    }
}

@Composable
private fun MatrixField(name: String, rows: List<List<Double>>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(name, style = MaterialTheme.typography.bodyMedium, color = RawappColors.TextSecondary)
        rows.forEach { row ->
            Text(
                row.joinToString("   ") { String.format(Locale.US, "%8.4f", it) },
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = RawappColors.TextPrimary,
            )
        }
    }
}

private fun formatExposure(ns: Long): String =
    if (ns >= 1_000_000_000L) {
        String.format(Locale.US, "%.1f s", ns / 1e9)
    } else {
        "1/${(1e9 / ns).toLong()} s"
    }
