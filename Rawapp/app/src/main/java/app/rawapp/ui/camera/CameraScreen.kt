package app.rawapp.ui.camera

import android.view.Surface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rawapp.camera.Lens
import app.rawapp.ui.components.GlassIconButton
import app.rawapp.ui.components.GlassPill
import app.rawapp.ui.components.GlassSegmented
import app.rawapp.ui.components.GlassButton
import app.rawapp.develop.RawFrame
import app.rawapp.ui.components.MessageScreen
import app.rawapp.ui.components.glass
import app.rawapp.ui.theme.RawappColors

@Composable
fun CameraScreen(viewModel: CameraViewModel, onOpenDiagnostics: () -> Unit, onOpenDevelop: (RawFrame) -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    when (val state = uiState) {
        CameraUiState.Loading -> LoadingState()
        CameraUiState.RawUnsupported -> MessageScreen(
            title = "RAW no disponible",
            body = "Ninguna cámara trasera de este dispositivo ofrece captura RAW_SENSOR.",
            actionLabel = "Ver diagnóstico",
            onAction = onOpenDiagnostics,
        )
        is CameraUiState.Error -> MessageScreen(
            title = "Cámara no disponible",
            body = state.message,
            actionLabel = "Reintentar",
            onAction = viewModel::retry,
        )
        is CameraUiState.Ready -> CameraContent(state, viewModel, onOpenDiagnostics, onOpenDevelop)
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = RawappColors.Accent, strokeWidth = 2.dp)
    }
}

@Composable
private fun CameraContent(
    state: CameraUiState.Ready,
    viewModel: CameraViewModel,
    onOpenDiagnostics: () -> Unit,
    onOpenDevelop: (RawFrame) -> Unit,
) {
    val lens = state.selected
    val lastFrame by viewModel.lastFrame.collectAsStateWithLifecycle()
    val shotState by viewModel.shotState.collectAsStateWithLifecycle()
    val deviceRotation by rememberDeviceRotation()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    var surface by remember { mutableStateOf<Surface?>(null) }
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    LaunchedEffect(surface, resumed, lens) {
        val current = surface
        if (current != null && resumed) viewModel.start(lens, current) else viewModel.stop()
    }
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    Box(Modifier.fillMaxSize().background(RawappColors.Background)) {
        CameraPreview(
            previewSize = lens.previewSize,
            onSurfaceAvailable = { surface = it },
            onSurfaceDestroyed = { surface = null },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(lens.previewSize.height.toFloat() / lens.previewSize.width)
                .align(Alignment.Center),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassPill("RAW · DNG")
            GlassIconButton(Icons.Outlined.Info, "Diagnóstico de cámaras", onOpenDiagnostics)
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ShotStatus(shotState)
            if (state.lenses.size > 1) {
                GlassSegmented(
                    options = state.lenses,
                    selected = lens,
                    label = Lens::label,
                    onSelect = viewModel::selectLens,
                    enabled = shotState != ShotState.Saving,
                )
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                lastFrame?.let { frame ->
                    GlassButton("Revelar", onClick = { onOpenDevelop(frame) }, modifier = Modifier.align(Alignment.CenterStart))
                }
                ShutterButton(
                    enabled = shotState != ShotState.Saving,
                    onClick = { viewModel.shoot(deviceRotation) },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun ShotStatus(state: ShotState) {
    AnimatedVisibility(visible = state != ShotState.Idle, enter = fadeIn(), exit = fadeOut()) {
        when (state) {
            ShotState.Saving -> GlassPill("Guardando DNG…")
            ShotState.Saved -> GlassPill("Guardado en Pictures/Rawapp", color = RawappColors.Accent)
            is ShotState.Failed -> GlassPill("Error al guardar: ${state.message}", color = RawappColors.Error)
            ShotState.Idle -> Unit
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(84.dp)
            .glass(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Disparar" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .alpha(if (enabled) 1f else 0.4f)
                .background(RawappColors.TextPrimary, CircleShape),
        )
    }
}
