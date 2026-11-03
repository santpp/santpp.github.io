package app.rawapp.ui.develop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rawapp.ui.components.GlassButton
import app.rawapp.ui.components.GlassIconButton
import app.rawapp.ui.components.GlassPill
import app.rawapp.ui.components.GlassSegmented
import app.rawapp.ui.components.MessageScreen
import app.rawapp.ui.theme.RawappColors

@Composable
fun DevelopScreen(viewModel: DevelopViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        when (val state = uiState) {
            DevelopUiState.Loading -> CircularProgressIndicator(
                color = RawappColors.Accent,
                strokeWidth = 2.dp,
                modifier = Modifier.align(Alignment.Center),
            )
            is DevelopUiState.Error -> MessageScreen("No se pudo revelar", state.message)
            is DevelopUiState.Ready -> {
                val image = remember(state.bitmap) { state.bitmap.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = "Foto revelada",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Volver a la cámara", onBack)
            (uiState as? DevelopUiState.Ready)?.let {
                GlassPill("${it.width}×${it.height} · ${it.gpuMillis} ms")
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ExportStatus(exportState)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassSegmented(
                    options = DevelopMode.entries,
                    selected = mode,
                    label = DevelopMode::label,
                    onSelect = viewModel::setMode,
                    enabled = uiState !is DevelopUiState.Loading,
                )
                if (uiState is DevelopUiState.Ready && exportState != ExportState.Saving) {
                    GlassButton("Guardar JPEG", onClick = viewModel::exportJpeg)
                }
            }
        }
    }
}

@Composable
private fun ExportStatus(state: ExportState) {
    when (state) {
        ExportState.Idle -> Unit
        ExportState.Saving -> GlassPill("Guardando JPEG…")
        ExportState.Saved -> GlassPill("Guardado en Pictures/Rawapp/Exports", color = RawappColors.Accent)
        is ExportState.Failed -> GlassPill(state.message, color = RawappColors.Error)
    }
}
