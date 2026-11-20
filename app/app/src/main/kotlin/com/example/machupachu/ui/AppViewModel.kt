package com.example.machupachu.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

/** Entry-scoped ViewModel stores carry no Application extra, so supply it explicitly. */
@Composable
inline fun <reified T : AndroidViewModel> appViewModel(crossinline create: (Application) -> T): T {
  val app = LocalContext.current.applicationContext as Application
  return viewModel { create(app) }
}
