package app.rawapp.ui.gallery

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.rawapp.develop.LookParams
import app.rawapp.RawappApplication
import app.rawapp.develop.RawFrame
import app.rawapp.develop.subsampled
import app.rawapp.gallery.GalleryRepository
import app.rawapp.gallery.Photo
import app.rawapp.storage.PhotoExporter
import app.rawapp.storage.SharedFile
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface GalleryState {
    data object Loading : GalleryState
    data object Empty : GalleryState
    data class Loaded(val photos: List<Photo>) : GalleryState
    data class Error(val message: String) : GalleryState
}

sealed interface ExportState {
    data object Idle : ExportState
    data object Saving : ExportState
    data object Saved : ExportState
    data class Failed(val message: String) : ExportState
}

data class EditorState(
    val photo: Photo,
    val params: LookParams,
    val lookEnabled: Boolean = true,
    val image: Bitmap? = null,
    val fullResolution: Boolean = false,
    val error: String? = null,
)

private const val THUMB_SUBSAMPLE = 4
private const val PREVIEW_SUBSAMPLE = 2
private const val THUMB_MAX_SIDE = 360
private const val FULL_RENDER_DELAY_MS = 350L
private const val MESSAGE_MS = 2_500L

private data class RenderRequest(val params: LookParams, val lookEnabled: Boolean)

/** Gallery grid, thumbnail rendering and the live editor of one photo. */
class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GalleryRepository(application)
    private val app = application as RawappApplication
    private val developer = app.developer
    private val exporter = PhotoExporter(application.contentResolver)

    val settings = app.settings.settings

    private val shareChannel = Channel<SharedFile>(Channel.BUFFERED)
    val shareRequests: Flow<SharedFile> = shareChannel.receiveAsFlow()

    private val _gallery = MutableStateFlow<GalleryState>(GalleryState.Loading)
    val gallery: StateFlow<GalleryState> = _gallery.asStateFlow()

    private val _thumbnails = MutableStateFlow<Map<Long, Bitmap>>(emptyMap())
    val thumbnails: StateFlow<Map<Long, Bitmap>> = _thumbnails.asStateFlow()

    private val _editor = MutableStateFlow<EditorState?>(null)
    val editor: StateFlow<EditorState?> = _editor.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val renderRequests = MutableStateFlow<RenderRequest?>(null)
    private var thumbnailJob: Job? = null
    private var editorJob: Job? = null
    private var saveJob: Job? = null
    private var frame: RawFrame? = null
    private var preview: RawFrame? = null

    fun refresh() {
        viewModelScope.launch {
            _gallery.value = try {
                val photos = withContext(Dispatchers.IO) { repository.listPhotos() }
                if (photos.isEmpty()) GalleryState.Empty else GalleryState.Loaded(photos)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                GalleryState.Error(e.message ?: "No se pudo leer la galería")
            }
            startThumbnails()
        }
    }

    fun open(photo: Photo) {
        thumbnailJob?.cancel()
        editorJob?.cancel()
        _exportState.value = ExportState.Idle
        val params = repository.loadParams(photo)
        _editor.value = EditorState(photo, params)
        editorJob = viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { repository.readFrame(photo) }
                frame = loaded
                preview = withContext(Dispatchers.Default) { loaded.subsampled(PREVIEW_SUBSAMPLE) }
                renderRequests.value = RenderRequest(params, lookEnabled = true)
                renderRequests.filterNotNull().collectLatest(::render)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _editor.update { it?.copy(error = e.message ?: "No se pudo abrir la foto") }
            }
        }
    }

    fun close() {
        editorJob?.cancel()
        frame = null
        preview = null
        renderRequests.value = null
        _editor.value = null
        startThumbnails()
    }

    fun adjust(params: LookParams) {
        val state = _editor.value ?: return
        _editor.value = state.copy(params = params)
        renderRequests.value = RenderRequest(params, state.lookEnabled)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(FULL_RENDER_DELAY_MS)
            withContext(Dispatchers.IO) { repository.saveParams(state.photo, params) }
            _thumbnails.update { it - state.photo.id }
        }
    }

    fun setLookEnabled(enabled: Boolean) {
        val state = _editor.value ?: return
        _editor.value = state.copy(lookEnabled = enabled)
        renderRequests.value = RenderRequest(state.params, enabled)
    }

    fun export() = exportCurrent(share = false)

    fun share() = exportCurrent(share = true)

    private fun exportCurrent(share: Boolean) {
        val state = _editor.value ?: return
        val full = frame ?: return
        if (_exportState.value == ExportState.Saving) return
        val format = settings.value.exportFormat
        viewModelScope.launch {
            _exportState.value = ExportState.Saving
            _exportState.value = try {
                val bitmap = developer.develop(full, state.params, state.lookEnabled).bitmap
                val uri = withContext(Dispatchers.IO) { exporter.export(bitmap, format) }
                bitmap.recycle()
                if (share) shareChannel.send(SharedFile(uri, format.mimeType))
                ExportState.Saved
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ExportState.Failed(e.message ?: "Error desconocido al exportar")
            }
            delay(MESSAGE_MS)
            _exportState.value = ExportState.Idle
        }
    }

    private suspend fun render(request: RenderRequest) {
        val small = preview ?: return
        val full = frame ?: return
        try {
            show(developer.develop(small, request.params, request.lookEnabled).bitmap, fullResolution = false)
            delay(FULL_RENDER_DELAY_MS)
            show(developer.develop(full, request.params, request.lookEnabled).bitmap, fullResolution = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _editor.update { it?.copy(error = e.message ?: "No se pudo revelar la foto") }
        }
    }

    private fun show(bitmap: Bitmap, fullResolution: Boolean) {
        _editor.update { it?.copy(image = bitmap, fullResolution = fullResolution, error = null) }
    }

    private fun startThumbnails() {
        val photos = (_gallery.value as? GalleryState.Loaded)?.photos ?: return
        thumbnailJob?.cancel()
        thumbnailJob = viewModelScope.launch {
            for (photo in photos) {
                if (photo.id in _thumbnails.value) continue
                val thumb = try {
                    thumbnailFor(photo)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                } ?: continue
                _thumbnails.update { it + (photo.id to thumb) }
            }
        }
    }

    private suspend fun thumbnailFor(photo: Photo): Bitmap {
        val params = withContext(Dispatchers.IO) { repository.loadParams(photo) }
        withContext(Dispatchers.IO) { repository.cachedThumbnail(photo, params) }?.let { return it }
        val small = withContext(Dispatchers.IO) { repository.readFrame(photo).subsampled(THUMB_SUBSAMPLE) }
        val developed = developer.develop(small, params, lookEnabled = true).bitmap
        val scale = THUMB_MAX_SIDE.toFloat() / maxOf(developed.width, developed.height)
        val thumb = Bitmap.createScaledBitmap(developed, (developed.width * scale).toInt(), (developed.height * scale).toInt(), true)
        developed.recycle()
        withContext(Dispatchers.IO) { repository.cacheThumbnail(photo, params, thumb) }
        return thumb
    }
}
