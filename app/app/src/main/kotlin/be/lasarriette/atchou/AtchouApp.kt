package be.lasarriette.atchou

import android.app.Application
import be.lasarriette.atchou.data.ApiClient
import be.lasarriette.atchou.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AtchouApp : Application() {
  lateinit var settings: Settings
  lateinit var api: ApiClient

  private val _loginRequired = MutableStateFlow(false)
  val loginRequired: StateFlow<Boolean> = _loginRequired

  fun requireLogin() {
    settings.sessionToken = null
    _loginRequired.value = true
  }

  fun loginDone(token: String) {
    settings.sessionToken = token
    _loginRequired.value = false
  }

  override fun onCreate() {
    super.onCreate()
    settings = Settings(this)
    api = ApiClient({ settings.serverUrl }, { settings.sessionToken }, ::requireLogin)
  }
}
