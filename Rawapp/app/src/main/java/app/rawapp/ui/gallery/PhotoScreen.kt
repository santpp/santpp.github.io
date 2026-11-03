package app.rawapp.ui.gallery

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rawapp.develop.LookParams
import app.rawapp.ui.components.GlassButton
import app.rawapp.ui.components.GlassIconButton
import app.rawapp.ui.components.GlassPill
import app.rawapp.ui.components.GlassSegmented
import app.rawapp.ui.components.MessageScreen
import app.rawapp.ui.components.glass
import app.rawapp.ui.theme.RawappColors

private enum class ViewMode(val label: String) { Look("Rawapp"), Neutral("Sin look") }

@Composable
fun PhotoScreen(viewModel: GalleryViewModel, onBack: () -> Unit) {
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.shareRequests.collect { file ->
            val send = Intent(Intent.ACTION_SEND)
                .setType(file.mimeType)
                .putExtra(Intent.EXTRA_STREAM, file.uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, "Compartir foto"))
        }
    }
    val state = editor ?: return
    var selected by rememberSaveable { mutableStateOf(Adjustment.Exposure) }

    Box(Modifier.fillMaxSize()) {
        val image = state.image
        when {
            state.error != null -> MessageScreen("No se pudo revelar", state.error)
            image == null -> CircularProgressIndicator(
                color = RawappColors.Accent,
                strokeWidth = 2.dp,
                modifier = Modifier.align(Alignment.Center),
            )
            else -> ZoomableImage(image, Modifier.padding(bottom = 220.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Volver a la galería", onBack)
            ExportStatus(exportState, state.fullResolution)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassIconButton(Icons.Filled.Refresh, "Restablecer ajustes", { viewModel.adjust(LookParams()) })
                if (state.image != null && exportState != ExportState.Saving) {
                    GlassIconButton(Icons.Filled.Share, "Compartir", viewModel::share)
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .glass(RoundedCornerShape(28.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AdjustmentPicker(selected) { selected = it }
            AdjustmentSlider(selected, state.params, enabled = state.lookEnabled, onChange = viewModel::adjust)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassSegmented(
                    options = ViewMode.entries,
                    selected = if (state.lookEnabled) ViewMode.Look else ViewMode.Neutral,
                    label = ViewMode::label,
                    onSelect = { viewModel.setLookEnabled(it == ViewMode.Look) },
                )
                if (exportState != ExportState.Saving && state.image != null) {
                    GlassButton("Guardar ${settings.exportFormat.label}", onClick = viewModel::export)
                }
            }
        }
    }
}

@Composable
private fun AdjustmentPicker(selected: Adjustment, onSelect: (Adjustment) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        GlassSegmented(
            options = Adjustment.entries,
            selected = selected,
            label = Adjustment::label,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun AdjustmentSlider(adjustment: Adjustment, params: LookParams, enabled: Boolean, onChange: (LookParams) -> Unit) {
    val value = adjustment.get(params)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(adjustment.label, style = MaterialTheme.typography.labelMedium, color = RawappColors.TextSecondary)
            Text(adjustment.format(value), style = MaterialTheme.typography.labelMedium, color = RawappColors.TextPrimary)
        }
        Slider(
            value = value,
            onValueChange = { onChange(adjustment.set(params, it)) },
            valueRange = adjustment.range,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = RawappColors.TextPrimary,
                activeTrackColor = RawappColors.Accent,
                inactiveTrackColor = RawappColors.GlassFill,
            ),
        )
    }
}

@Composable
private fun ExportStatus(state: ExportState, fullResolution: Boolean) {
    when (state) {
        ExportState.Idle -> if (!fullResolution) GlassPill("Vista previa")
        ExportState.Saving -> GlassPill("Exportando…")
        ExportState.Saved -> GlassPill("Guardado en Exports", color = RawappColors.Accent)
        is ExportState.Failed -> GlassPill(state.message, color = RawappColors.Error)
    }
}
