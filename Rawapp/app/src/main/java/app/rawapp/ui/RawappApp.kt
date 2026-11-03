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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import app.rawapp.RawappApplication
import app.rawapp.ui.camera.CameraScreen
import app.rawapp.ui.settings.SettingsScreen
import app.rawapp.ui.gallery.GalleryScreen
import app.rawapp.ui.gallery.GalleryViewModel
import app.rawapp.ui.gallery.PhotoScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rawapp.ui.components.MessageScreen
import app.rawapp.ui.diagnostics.DiagnosticsScreen

private enum class Screen { Camera, Diagnostics, Gallery, Photo, Settings }

@Composable
fun RawappApp() {
    var screen by rememberSaveable { mutableStateOf(Screen.Camera) }
    val galleryViewModel: GalleryViewModel = viewModel()
    when (screen) {
        Screen.Camera -> CameraPermissionGate {
            CameraScreen(
                viewModel(),
                onOpenDiagnostics = { screen = Screen.Diagnostics },
                onOpenGallery = { screen = Screen.Gallery },
                onOpenSettings = { screen = Screen.Settings },
            )
        }
        Screen.Gallery -> {
            BackHandler { screen = Screen.Camera }
            GalleryScreen(
                galleryViewModel,
                onBack = { screen = Screen.Camera },
                onOpen = { photo ->
                    galleryViewModel.open(photo)
                    screen = Screen.Photo
                },
            )
        }
        Screen.Photo -> {
            val closePhoto = {
                galleryViewModel.close()
                screen = Screen.Gallery
            }
            BackHandler(onBack = closePhoto)
            val editor by galleryViewModel.editor.collectAsStateWithLifecycle()
            LaunchedEffect(editor == null) { if (editor == null) screen = Screen.Gallery }
            PhotoScreen(galleryViewModel, onBack = closePhoto)
        }
        Screen.Settings -> {
            BackHandler { screen = Screen.Camera }
            SettingsScreen((LocalContext.current.applicationContext as RawappApplication).settings, onBack = { screen = Screen.Camera })
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
