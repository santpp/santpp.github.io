package app.rawapp.ui.develop

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.rawapp.develop.LookParams
import app.rawapp.develop.RawDeveloper
import app.rawapp.develop.RawFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class DevelopMode(val label: String) { Look("Rawapp"), Neutral("Sin look") }

sealed interface DevelopUiState {
    data object Loading : DevelopUiState
    data class Ready(val bitmap: Bitmap, val gpuMillis: Long, val width: Int, val height: Int) : DevelopUiState
    data class Error(val message: String) : DevelopUiState
}

class DevelopViewModel(application: Application) : AndroidViewModel(application) {

    private val developer = RawDeveloper(application.assets)
    private var frame: RawFrame? = null
    private var job: Job? = null

    private val _uiState = MutableStateFlow<DevelopUiState>(DevelopUiState.Loading)
    val uiState: StateFlow<DevelopUiState> = _uiState.asStateFlow()

    private val _mode = MutableStateFlow(DevelopMode.Look)
    val mode: StateFlow<DevelopMode> = _mode.asStateFlow()

    fun show(newFrame: RawFrame) {
        if (newFrame === frame) return
        frame = newFrame
        _mode.value = DevelopMode.Look
        develop()
    }

    fun setMode(newMode: DevelopMode) {
        if (newMode == _mode.value) return
        _mode.value = newMode
        develop()
    }

    override fun onCleared() {
        developer.close()
    }

    private fun develop() {
        val current = frame ?: return
        job?.cancel()
        _uiState.value = DevelopUiState.Loading
        job = viewModelScope.launch {
            _uiState.value = try {
                val result = developer.develop(current, LookParams(), lookEnabled = _mode.value == DevelopMode.Look)
                DevelopUiState.Ready(result.bitmap, result.gpuMillis, current.width, current.height)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DevelopUiState.Error(e.message ?: "No se pudo revelar la foto")
            }
        }
    }
}
