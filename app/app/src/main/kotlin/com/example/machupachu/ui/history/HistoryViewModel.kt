package com.example.machupachu.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.machupachu.MachuApp
import com.example.machupachu.data.Post
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryState(
  val posts: List<Post> = emptyList(),
  val loading: Boolean = false,
  val error: String? = null,
)

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
  private val machu = app as MachuApp
  private val _state = MutableStateFlow(HistoryState())
  val state: StateFlow<HistoryState> = _state

  var serverUrl: String
    get() = machu.settings.serverUrl
    set(value) {
      machu.settings.serverUrl = value
      refresh()
    }

  fun url(path: String) = machu.api.url(path)

  fun authHeader() = machu.api.authHeader()

  fun refresh() {
    viewModelScope.launch {
      _state.update { it.copy(loading = true, error = null) }
      try {
        val posts = machu.api.listPosts()
        _state.update { it.copy(posts = posts, loading = false) }
      } catch (e: Exception) {
        _state.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
      }
    }
  }

  fun delete(post: Post) {
    viewModelScope.launch {
      try {
        machu.api.deletePost(post.id)
        refresh()
      } catch (e: Exception) {
        _state.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
      }
    }
  }
}
