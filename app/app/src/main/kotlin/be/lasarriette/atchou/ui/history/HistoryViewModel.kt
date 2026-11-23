package be.lasarriette.atchou.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import be.lasarriette.atchou.AtchouApp
import be.lasarriette.atchou.data.Post
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
  private val atchou = app as AtchouApp
  private val _state = MutableStateFlow(HistoryState())
  val state: StateFlow<HistoryState> = _state

  var serverUrl: String
    get() = atchou.settings.serverUrl
    set(value) {
      atchou.settings.serverUrl = value
      refresh()
    }

  fun url(path: String) = atchou.api.url(path)

  fun authHeader() = atchou.api.authHeader()

  fun refresh() {
    viewModelScope.launch {
      _state.update { it.copy(loading = true, error = null) }
      try {
        val posts = atchou.api.listPosts()
        _state.update { it.copy(posts = posts, loading = false) }
      } catch (e: Exception) {
        _state.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
      }
    }
  }

  fun delete(post: Post) {
    viewModelScope.launch {
      try {
        atchou.api.deletePost(post.id)
        refresh()
      } catch (e: Exception) {
        _state.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
      }
    }
  }
}
