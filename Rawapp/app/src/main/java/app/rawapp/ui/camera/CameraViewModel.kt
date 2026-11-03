package app.rawapp.ui.camera

import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.rawapp.camera.CameraController
import app.rawapp.camera.Lens
import app.rawapp.camera.ManualExposure
import app.rawapp.camera.nearestStop
import app.rawapp.camera.captureRotationDegrees
import app.rawapp.camera.findRawLenses
import app.rawapp.RawappApplication
import app.rawapp.develop.LookParams
import app.rawapp.gallery.GalleryRepository
import app.rawapp.storage.DngWriter
import app.rawapp.storage.PhotoExporter
import app.rawapp.storage.SavedDng
import app.rawapp.storage.SharedFile
import app.rawapp.ui.gallery.MAX_CROP_ZOOM
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
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
    data class Ready(val lenses: List<Lens>, val selected: Lens) : CameraUiState
    data class Error(val message: String) : CameraUiState
}

sealed interface ShotState {
    data object Idle : ShotState
    data object Saving : ShotState
    data object Saved : ShotState
    data object Developing : ShotState
    data object Exported : ShotState
    data class Failed(val message: String) : ShotState
}

private const val SHOT_MESSAGE_MS = 2_500L
private const val DEFAULT_ISO = 100
private const val DEFAULT_EXPOSURE_NS = 8_000_000L

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = application.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val writer = DngWriter(application.contentResolver)
    private val app = application as RawappApplication
    private val repository = GalleryRepository(application)
    private val exporter = PhotoExporter(application.contentResolver)
    private val controller = CameraController(manager, onDeviceLost = { message ->
        viewModelScope.launch { showError(message) }
    })

    private val _uiState = MutableStateFlow<CameraUiState>(CameraUiState.Loading)
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _shotState = MutableStateFlow<ShotState>(ShotState.Idle)
    val shotState: StateFlow<ShotState> = _shotState.asStateFlow()

    private val _zoom = MutableStateFlow(1f)
    val zoom: StateFlow<Float> = _zoom.asStateFlow()

    /** Null in Auto mode; the fixed ISO and shutter in Pro mode. */
    private val _manual = MutableStateFlow<ManualExposure?>(null)
    val manual: StateFlow<ManualExposure?> = _manual.asStateFlow()

    private val _lastShot = MutableStateFlow<SavedDng?>(null)
    val lastShot: StateFlow<SavedDng?> = _lastShot.asStateFlow()

    private val shareChannel = Channel<SharedFile>(Channel.BUFFERED)
    val shareRequests: Flow<SharedFile> = shareChannel.receiveAsFlow()

    private var openJob: Job? = null
    private var shotJob: Job? = null

    init {
        selectCamera()
    }

    fun retry() {
        _uiState.value = CameraUiState.Loading
        selectCamera()
    }

    fun selectLens(lens: Lens) {
        val ready = _uiState.value as? CameraUiState.Ready ?: return
        if (_shotState.value == ShotState.Saving) return
        _uiState.value = ready.copy(selected = lens)
        _zoom.value = 1f
        applyManual(null)
    }

    /** Enters Pro mode starting from the exposure auto mode was using, snapped to standard stops. */
    fun setProMode(enabled: Boolean) {
        val range = (_uiState.value as? CameraUiState.Ready)?.selected?.manualRange ?: return
        if (!enabled) return applyManual(null)
        val seed = controller.lastAutoExposure ?: ManualExposure(DEFAULT_ISO, DEFAULT_EXPOSURE_NS)
        applyManual(
            ManualExposure(
                iso = nearestStop(range.isoStops, seed.iso.toDouble()),
                exposureNs = nearestStop(range.shutterStops, seed.exposureNs.toDouble()),
            ),
        )
    }

    fun setIso(iso: Int) {
        _manual.value?.let { applyManual(it.copy(iso = iso)) }
    }

    fun setShutter(exposureNs: Long) {
        _manual.value?.let { applyManual(it.copy(exposureNs = exposureNs)) }
    }

    private fun applyManual(value: ManualExposure?) {
        _manual.value = value
        controller.setManualExposure(value)
    }

    fun setZoom(value: Float) {
        _zoom.value = value.coerceIn(1f, MAX_CROP_ZOOM)
    }

    /** Develops the last DNG with the look and opens the share sheet in one step. */
    fun shareLastShot() {
        val shot = _lastShot.value ?: return
        if (_shotState.value == ShotState.Saving || _shotState.value == ShotState.Developing) return
        val format = app.settings.settings.value.exportFormat
        viewModelScope.launch {
            _shotState.value = ShotState.Developing
            val result = try {
                val file = SharedFile(developAndExport(shot), format.mimeType)
                shareChannel.send(file)
                ShotState.Exported
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ShotState.Failed("Compartir: ${e.message ?: "error desconocido"}")
            }
            showShotMessage(result)
        }
    }

    fun start(lens: Lens, surface: Surface) {
        openJob?.cancel()
        openJob = viewModelScope.launch {
            try {
                controller.open(lens, surface)
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
        val lens = (_uiState.value as? CameraUiState.Ready)?.selected ?: return
        if (_shotState.value == ShotState.Saving) return
        val zoom = _zoom.value
        shotJob = viewModelScope.launch {
            _shotState.value = ShotState.Saving
            val dng = try {
                val rotation = captureRotationDegrees(lens.sensorOrientation, deviceRotation, lens.frontFacing)
                controller.captureRaw().use { capture ->
                    withContext(Dispatchers.IO) {
                        writer.write(capture, rotation).also { saved ->
                            if (zoom > 1f) repository.saveParams(saved.displayName, LookParams(zoom = zoom))
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showShotMessage(ShotState.Failed(e.message ?: "Error desconocido al guardar"))
                return@launch
            }
            _lastShot.value = dng
            if (app.settings.settings.value.autoExport) autoExport(dng) else showShotMessage(ShotState.Saved)
        }
    }

    /** Develops the saved DNG with the default look and exports it, without blocking the next shot. */
    private fun autoExport(dng: SavedDng) {
        viewModelScope.launch {
            _shotState.value = ShotState.Developing
            val result = try {
                developAndExport(dng)
                ShotState.Exported
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ShotState.Failed("Auto-exportar: ${e.message ?: "error desconocido"}")
            }
            showShotMessage(result)
        }
    }

    /** Reads the DNG back, develops it with its saved adjustments and exports it in the chosen format. */
    private suspend fun developAndExport(dng: SavedDng): Uri {
        val format = app.settings.settings.value.exportFormat
        val (frame, params) = withContext(Dispatchers.IO) {
            repository.readFrame(dng.uri) to repository.loadParams(dng.displayName)
        }
        val bitmap = app.developer.develop(frame, params, lookEnabled = true).bitmap
        return withContext(Dispatchers.IO) { exporter.export(bitmap, format) }.also { bitmap.recycle() }
    }

    private suspend fun showShotMessage(state: ShotState) {
        _shotState.value = state
        delay(SHOT_MESSAGE_MS)
        if (_shotState.value == state) _shotState.value = ShotState.Idle
    }

    override fun onCleared() {
        controller.release()
    }

    private fun selectCamera() {
        _uiState.value = try {
            val lenses = findRawLenses(manager)
            val main = lenses.firstOrNull { it.label == "1×" } ?: lenses.firstOrNull()
            if (main == null) CameraUiState.RawUnsupported else CameraUiState.Ready(lenses, main)
        } catch (e: CameraAccessException) {
            CameraUiState.Error(e.message ?: "No se pudo consultar la cámara")
        }
    }

    private fun showError(message: String) {
        stop()
        _uiState.value = CameraUiState.Error(message)
    }
}
