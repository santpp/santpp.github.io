package app.rawapp.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import app.rawapp.ui.camera.CameraScreen
import app.rawapp.ui.components.MessageScreen
import app.rawapp.ui.diagnostics.DiagnosticsScreen

private enum class Screen { Camera, Diagnostics }

@Composable
fun RawappApp() {
    var screen by rememberSaveable { mutableStateOf(Screen.Camera) }
    when (screen) {
        Screen.Camera -> CameraPermissionGate {
            CameraScreen(viewModel(), onOpenDiagnostics = { screen = Screen.Diagnostics })
        }
        Screen.Diagnostics -> {
            BackHandler { screen = Screen.Camera }
            DiagnosticsScreen(viewModel(), onBack = { screen = Screen.Camera })
        }
    }
}

@Composable
private fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    fun isGranted() = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    var granted by rememberSaveable { mutableStateOf(isGranted()) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        askedOnce = true
    }
    LifecycleResumeEffect(Unit) {
        granted = isGranted()
        onPauseOrDispose { }
    }

    if (granted) {
        content()
        return
    }
    val blocked = askedOnce && !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
    MessageScreen(
        title = "Rawapp necesita la cámara",
        body = if (blocked) {
            "El permiso está denegado. Actívalo en Ajustes › Aplicaciones › Rawapp › Permisos."
        } else {
            "Sin acceso a la cámara no se pueden capturar archivos RAW."
        },
        actionLabel = if (blocked) "Abrir ajustes" else "Permitir cámara",
        onAction = {
            if (blocked) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            } else {
                launcher.launch(Manifest.permission.CAMERA)
            }
        },
    )
}
