package app.rawapp.ui.diagnostics

import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.rawapp.camera.CameraCapabilities
import app.rawapp.camera.readAllCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DiagnosticsUiState {
    data object Loading : DiagnosticsUiState
    data class Loaded(val cameras: List<CameraCapabilities>) : DiagnosticsUiState
    data class Error(val message: String) : DiagnosticsUiState
}

class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = application.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val _uiState = MutableStateFlow<DiagnosticsUiState>(DiagnosticsUiState.Loading)
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = try {
                DiagnosticsUiState.Loaded(withContext(Dispatchers.IO) { readAllCapabilities(manager) })
            } catch (e: CameraAccessException) {
                DiagnosticsUiState.Error(e.message ?: "No se pudo leer la información de las cámaras")
            }
        }
    }
}
