package app.rawapp.ui.camera

import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.rawapp.camera.CameraController
import app.rawapp.camera.SelectedCamera
import app.rawapp.camera.captureRotationDegrees
import app.rawapp.camera.selectRawCamera
import app.rawapp.storage.DngWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface CameraUiState {
    data object Loading : CameraUiState
    data object RawUnsupported : CameraUiState
    data class Ready(val camera: SelectedCamera) : CameraUiState
    data class Error(val message: String) : CameraUiState
}

sealed interface ShotState {
    data object Idle : ShotState
    data object Saving : ShotState
    data object Saved : ShotState
    data class Failed(val message: String) : ShotState
}

private const val SHOT_MESSAGE_MS = 2_500L

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = application.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val writer = DngWriter(application.contentResolver)
    private val controller = CameraController(manager, onDeviceLost = { message ->
        viewModelScope.launch { showError(message) }
    })

    private val _uiState = MutableStateFlow<CameraUiState>(CameraUiState.Loading)
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _shotState = MutableStateFlow<ShotState>(ShotState.Idle)
    val shotState: StateFlow<ShotState> = _shotState.asStateFlow()

    private var openJob: Job? = null
    private var shotJob: Job? = null

    init {
        selectCamera()
    }

    fun retry() {
        _uiState.value = CameraUiState.Loading
        selectCamera()
    }

    fun start(surface: Surface) {
        val camera = (_uiState.value as? CameraUiState.Ready)?.camera ?: return
        openJob?.cancel()
        openJob = viewModelScope.launch {
            try {
                controller.open(camera.id, surface)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(e.message ?: "No se pudo abrir la cámara")
            }
        }
    }

    fun stop() {
        openJob?.cancel()
        shotJob?.cancel()
        controller.close()
        if (_shotState.value == ShotState.Saving) _shotState.value = ShotState.Idle
    }

    fun shoot(deviceRotation: Int) {
        val camera = (_uiState.value as? CameraUiState.Ready)?.camera ?: return
        if (_shotState.value == ShotState.Saving) return
        shotJob = viewModelScope.launch {
            _shotState.value = ShotState.Saving
            _shotState.value = try {
                val rotation = captureRotationDegrees(camera.sensorOrientation, deviceRotation)
                controller.captureRaw().use { capture ->
                    withContext(Dispatchers.IO) { writer.write(capture, rotation) }
                }
                ShotState.Saved
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ShotState.Failed(e.message ?: "Error desconocido al guardar")
            }
            delay(SHOT_MESSAGE_MS)
            _shotState.value = ShotState.Idle
        }
    }

    override fun onCleared() {
        controller.release()
    }

    private fun selectCamera() {
        _uiState.value = try {
            selectRawCamera(manager)?.let(CameraUiState::Ready) ?: CameraUiState.RawUnsupported
        } catch (e: CameraAccessException) {
            CameraUiState.Error(e.message ?: "No se pudo consultar la cámara")
        }
    }

    private fun showError(message: String) {
        stop()
        _uiState.value = CameraUiState.Error(message)
    }
}
