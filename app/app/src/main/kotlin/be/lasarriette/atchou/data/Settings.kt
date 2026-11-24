package be.lasarriette.atchou.data

import android.content.Context

class Settings(context: Context) {
  private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

  // Reached through `adb reverse tcp:8000 tcp:8000` during development.
  var serverUrl: String
    get() = prefs.getString("server_url", DEFAULT_URL)!!
    set(value) = prefs.edit().putString("server_url", value.trim().trimEnd('/')).apply()

  var lastAuthor: String
    get() = prefs.getString("last_author", "")!!
    set(value) = prefs.edit().putString("last_author", value).apply()

  var senderName: String
    get() = prefs.getString("sender", "")!!
    set(value) = prefs.edit().putString("sender", value).apply()

  // Session token issued by our server after a Google sign-in.
  var sessionToken: String?
    get() = prefs.getString("session_token", null)
    set(value) = prefs.edit().putString("session_token", value).apply()

  companion object {
    const val DEFAULT_URL = "https://atchou.lasarriette.be"
  }
}
