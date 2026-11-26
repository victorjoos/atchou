package be.lasarriette.atchou.ui.send

import be.lasarriette.atchou.R
import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import be.lasarriette.atchou.AtchouApp
import be.lasarriette.atchou.data.PostOptions
import be.lasarriette.atchou.data.loadForUpload
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SendState(
  val hasImage: Boolean = false,
  val preview: ByteArray? = null,
  val previewLoading: Boolean = false,
  val authors: List<String> = emptyList(),
  val author: String = "",
  val showAuthor: Boolean = true,
  val mode: String = "dither",
  val rotate: Int = 0,
  val brightness: Float = 1f,
  val contrast: Float = 1f,
  val zoom: Float = 1f,
  val showOn: String? = null,
  val pinned: Boolean = false,
  val excludeFromRotation: Boolean = false,
  val sending: Boolean = false,
  val done: Boolean = false,
  val error: String? = null,
)

class SendViewModel(app: Application) : AndroidViewModel(app) {
  private val atchou = app as AtchouApp
  private val _state = MutableStateFlow(SendState(author = atchou.settings.lastAuthor))
  val state: StateFlow<SendState> = _state

  private var isDrawing = false
  private var image: ByteArray? = null
  private var previewJob: Job? = null
  private var imageWidth = 1
  private var imageHeight = 1
  private var centerX = 0.5f
  private var centerY = 0.5f

  init {
    viewModelScope.launch {
      runCatching { atchou.api.authors() }.onSuccess { a -> _state.update { it.copy(authors = a) } }
    }
  }

  fun onPicked(uri: Uri, drawing: Boolean = false) {
    // Drawings use the exact palette: dithering would add noise to flat colors.
    isDrawing = drawing
    if (drawing) _state.update { it.copy(mode = "drawing") }
    viewModelScope.launch {
      try {
        val bytes = loadForUpload(getApplication(), uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        imageWidth = bounds.outWidth
        imageHeight = bounds.outHeight
        image = bytes
        _state.update { it.copy(hasImage = true, error = null) }
        refreshPreview(immediately = true)
      } catch (e: Exception) {
        _state.update { it.copy(error = getApplication<Application>().getString(R.string.send_error_read, e.message)) }
      }
    }
  }

  fun setAuthor(value: String) {
    _state.update { it.copy(author = value) }
    refreshPreview()
  }

  fun setShowAuthor(value: Boolean) {
    _state.update { it.copy(showAuthor = value) }
    refreshPreview(immediately = true)
  }

  fun setDither(on: Boolean) = setMode(if (on) "dither" else if (isDrawing) "drawing" else "exact")

  private fun setMode(value: String) {
    _state.update { it.copy(mode = value) }
    refreshPreview(immediately = true)
  }

  fun rotate() {
    _state.update { it.copy(rotate = (it.rotate + 90) % 360, zoom = 1f) }
    centerX = 0.5f
    centerY = 0.5f
    refreshPreview(immediately = true)
  }

  fun setBrightness(value: Float) {
    _state.update { it.copy(brightness = value) }
    refreshPreview()
  }

  fun setContrast(value: Float) {
    _state.update { it.copy(contrast = value) }
    refreshPreview()
  }

  fun setShowOn(value: String?) = _state.update { it.copy(showOn = value) }
  fun setPinned(value: Boolean) = _state.update { it.copy(pinned = value) }
  fun setExclude(value: Boolean) = _state.update { it.copy(excludeFromRotation = value) }

  /** Size of the crop box in the rotated image, normalized, for a given zoom (a 5:3 pixel box). */
  private fun cropSize(zoom: Float): Pair<Float, Float> {
    val quarter = _state.value.rotate % 180 != 0
    val w = (if (quarter) imageHeight else imageWidth).toFloat()
    val h = (if (quarter) imageWidth else imageHeight).toFloat()
    val (cw, ch) = if (w / h > 5f / 3f) (h * 5f / 3f / w) to 1f else 1f to (w * 3f / 5f / h)
    return cw / zoom to ch / zoom
  }

  /** Pinch/drag on the preview: [panX]/[panY] are fractions of the visible box, [zoomBy] a factor. */
  fun transform(panX: Float, panY: Float, zoomBy: Float) {
    val zoom = (_state.value.zoom * zoomBy).coerceIn(1f, 6f)
    val (cw, ch) = cropSize(zoom)
    centerX = (centerX - panX * cw).coerceIn(cw / 2, 1 - cw / 2)
    centerY = (centerY - panY * ch).coerceIn(ch / 2, 1 - ch / 2)
    _state.update { it.copy(zoom = zoom) }
    refreshPreview()
  }

  private fun options(): PostOptions {
    val s = _state.value
    val (cw, ch) = cropSize(s.zoom)
    val x = (centerX - cw / 2).coerceIn(0f, 1 - cw)
    val y = (centerY - ch / 2).coerceIn(0f, 1 - ch)
    return PostOptions(
      author = s.author.trim(), showAuthor = s.showAuthor, mode = s.mode, sender = atchou.settings.senderName,
      rotate = s.rotate, brightness = s.brightness, contrast = s.contrast,
      crop = if (s.zoom > 1f) "$x,$y,$cw,$ch" else null,
      showOn = s.showOn, pinned = s.pinned, excludeFromRotation = s.excludeFromRotation,
    )
  }

  private fun refreshPreview(immediately: Boolean = false) {
    val img = image ?: return
    previewJob?.cancel()
    previewJob = viewModelScope.launch {
      if (!immediately) delay(500)
      _state.update { it.copy(previewLoading = true) }
      try {
        val png = atchou.api.preview(img, options())
        _state.update { it.copy(preview = png, previewLoading = false, error = null) }
      } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
      } catch (e: Exception) {
        _state.update { it.copy(previewLoading = false, error = getApplication<Application>().getString(R.string.send_error_preview, e.message)) }
      }
    }
  }

  fun send() {
    val img = image ?: return
    viewModelScope.launch {
      _state.update { it.copy(sending = true, error = null) }
      try {
        atchou.api.createPost(img, options())
        atchou.settings.lastAuthor = _state.value.author.trim()
        _state.update { it.copy(sending = false, done = true) }
      } catch (e: Exception) {
        _state.update { it.copy(sending = false, error = getApplication<Application>().getString(R.string.send_error_send, e.message)) }
      }
    }
  }
}
