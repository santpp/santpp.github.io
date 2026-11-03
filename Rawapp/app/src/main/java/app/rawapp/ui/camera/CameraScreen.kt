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
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import app.rawapp.ui.components.shareImage
import app.rawapp.ui.gallery.zoomLabel
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
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
import app.rawapp.camera.ManualExposure
import app.rawapp.camera.ManualRange
import app.rawapp.camera.formatShutter
import app.rawapp.ui.components.GlassIconButton
import app.rawapp.ui.components.GlassPill
import app.rawapp.ui.components.GlassSegmented
import app.rawapp.ui.components.GlassButton
import app.rawapp.ui.components.MessageScreen
import app.rawapp.ui.components.glass
import app.rawapp.ui.theme.RawappColors

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onOpenDiagnostics: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(resumed) {
        if (resumed && uiState is CameraUiState.Error) viewModel.retry()
    }
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
        is CameraUiState.Ready -> CameraContent(state, viewModel, onOpenDiagnostics, onOpenGallery, onOpenSettings)
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
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val lens = state.selected
    val shotState by viewModel.shotState.collectAsStateWithLifecycle()
    val zoom by viewModel.zoom.collectAsStateWithLifecycle()
    val manual by viewModel.manual.collectAsStateWithLifecycle()
    val lastShot by viewModel.lastShot.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) { viewModel.shareRequests.collect { context.shareImage(it.uri, it.mimeType) } }
    val deviceRotation by rememberDeviceRotation()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    var surface by remember { mutableStateOf<Surface?>(null) }
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    LaunchedEffect(surface, resumed, lens) {
        val current = surface
        if (current != null && resumed) viewModel.start(lens, current) else viewModel.stop()
    }
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(RawappColors.Background)
            .pointerInput(viewModel) {
                detectTransformGestures { _, _, gestureZoom, _ -> viewModel.setZoom(viewModel.zoom.value * gestureZoom) }
            },
    ) {
        CameraPreview(
            previewSize = lens.previewSize,
            zoom = zoom,
            onSurfaceAvailable = { surface = it },
            onSurfaceDestroyed = { surface = null },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(lens.previewSize.height.toFloat() / lens.previewSize.width)
                .align(Alignment.Center)
                .clipToBounds(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val range = lens.manualRange
            if (range == null) {
                GlassPill("RAW · DNG")
            } else {
                GlassSegmented(
                    options = listOf(false, true),
                    selected = manual != null,
                    label = { pro -> if (pro) "Pro" else "Auto" },
                    onSelect = viewModel::setProMode,
                    enabled = shotState != ShotState.Saving,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassIconButton(Icons.Outlined.Settings, "Ajustes", onOpenSettings)
                GlassIconButton(Icons.Outlined.Info, "Diagnóstico de cámaras", onOpenDiagnostics)
            }
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
            if (zoom > 1.01f) GlassPill(zoomLabel(zoom), color = RawappColors.Accent)
            val m = manual
            val range = lens.manualRange
            if (m != null && range != null) {
                ProControls(m, range, onIso = viewModel::setIso, onShutter = viewModel::setShutter)
            }
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
                GlassButton("Galería", onClick = onOpenGallery, modifier = Modifier.align(Alignment.CenterStart))
                ShutterButton(
                    enabled = shotState != ShotState.Saving,
                    onClick = { viewModel.shoot(deviceRotation) },
                    modifier = Modifier.align(Alignment.Center),
                )
                if (lastShot != null && shotState != ShotState.Saving && shotState != ShotState.Developing) {
                    GlassIconButton(
                        Icons.Filled.Share,
                        "Compartir la última foto revelada",
                        viewModel::shareLastShot,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
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
            ShotState.Developing -> GlassPill("DNG guardado · revelando…")
            ShotState.Exported -> GlassPill("DNG y revelado guardados", color = RawappColors.Accent)
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

private enum class ProSetting(val label: String) { Iso("ISO"), Shutter("Obturación") }

/** ISO and shutter selection; each slider moves through standard stops only. */
@Composable
private fun ProControls(
    manual: ManualExposure,
    range: ManualRange,
    onIso: (Int) -> Unit,
    onShutter: (Long) -> Unit,
) {
    var setting by rememberSaveable { mutableStateOf(ProSetting.Iso) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .glass(RoundedCornerShape(28.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GlassSegmented(
            options = ProSetting.entries,
            selected = setting,
            label = { s ->
                when (s) {
                    ProSetting.Iso -> "ISO ${manual.iso}"
                    ProSetting.Shutter -> formatShutter(manual.exposureNs)
                }
            },
            onSelect = { setting = it },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        when (setting) {
            ProSetting.Iso -> StopSlider(range.isoStops, manual.iso, onIso)
            ProSetting.Shutter -> StopSlider(range.shutterStops, manual.exposureNs, onShutter)
        }
    }
}

@Composable
private fun <T> StopSlider(stops: List<T>, value: T, onChange: (T) -> Unit) {
    val index = stops.indexOf(value).coerceAtLeast(0)
    Slider(
        value = index.toFloat(),
        onValueChange = { i -> stops[i.roundToInt().coerceIn(0, stops.lastIndex)].let { if (it != value) onChange(it) } },
        valueRange = 0f..stops.lastIndex.toFloat().coerceAtLeast(1f),
        steps = (stops.size - 2).coerceAtLeast(0),
        colors = SliderDefaults.colors(
            thumbColor = RawappColors.TextPrimary,
            activeTrackColor = RawappColors.Accent,
            inactiveTrackColor = RawappColors.GlassFill,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
    )
}

