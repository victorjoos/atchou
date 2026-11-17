package com.example.machupachu

import android.app.Application
import com.example.machupachu.data.ApiClient
import com.example.machupachu.data.Settings

class MachuApp : Application() {
  lateinit var settings: Settings
  lateinit var api: ApiClient

  override fun onCreate() {
    super.onCreate()
    settings = Settings(this)
    api = ApiClient { settings.serverUrl }
  }
}
