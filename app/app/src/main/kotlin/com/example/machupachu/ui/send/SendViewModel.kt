package com.example.machupachu.ui.send

import com.example.machupachu.R
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.machupachu.MachuApp
import com.example.machupachu.data.PostOptions
import com.example.machupachu.data.loadForUpload
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
  val sending: Boolean = false,
  val done: Boolean = false,
  val error: String? = null,
)

class SendViewModel(app: Application) : AndroidViewModel(app) {
  private val machu = app as MachuApp
  private val _state = MutableStateFlow(SendState(author = machu.settings.lastAuthor))
  val state: StateFlow<SendState> = _state

  private var isDrawing = false
  private var image: ByteArray? = null
  private var previewJob: Job? = null

  init {
    viewModelScope.launch {
      runCatching { machu.api.authors() }.onSuccess { a -> _state.update { it.copy(authors = a) } }
    }
  }

  fun onPicked(uri: Uri, drawing: Boolean = false) {
    // Drawings use the exact palette: dithering would add noise to flat colors.
    isDrawing = drawing
    if (drawing) _state.update { it.copy(mode = "drawing") }
    viewModelScope.launch {
      try {
        image = loadForUpload(getApplication(), uri)
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

  private fun options(): PostOptions {
    val s = _state.value
    return PostOptions(author = s.author.trim(), showAuthor = s.showAuthor, mode = s.mode, sender = machu.settings.senderName)
  }

  private fun refreshPreview(immediately: Boolean = false) {
    val img = image ?: return
    previewJob?.cancel()
    previewJob = viewModelScope.launch {
      if (!immediately) delay(500)
      _state.update { it.copy(previewLoading = true) }
      try {
        val png = machu.api.preview(img, options())
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
        machu.api.createPost(img, options())
        machu.settings.lastAuthor = _state.value.author.trim()
        _state.update { it.copy(sending = false, done = true) }
      } catch (e: Exception) {
        _state.update { it.copy(sending = false, error = getApplication<Application>().getString(R.string.send_error_send, e.message)) }
      }
    }
  }
}
