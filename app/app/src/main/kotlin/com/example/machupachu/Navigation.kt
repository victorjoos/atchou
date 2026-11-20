package com.example.machupachu

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.machupachu.ui.camera.CameraScreen
import com.example.machupachu.ui.history.HistoryScreen
import com.example.machupachu.ui.send.SendScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Camera)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    // Per-entry ViewModels so every new post starts with a fresh send state.
    entryDecorators = listOf(
      rememberSaveableStateHolderNavEntryDecorator(),
      rememberViewModelStoreNavEntryDecorator(),
    ),
    entryProvider =
      entryProvider {
        entry<Camera> {
          CameraScreen(
            onImage = { uri -> backStack.add(Send(uri)) },
            onHistory = { backStack.add(History) },
          )
        }
        entry<History> { HistoryScreen(onNewPost = { backStack.removeLastOrNull() }) }
        entry<Send> { key ->
          SendScreen(
            uri = key.uri,
            onBack = { backStack.removeLastOrNull() },
            onDone = {
              backStack.removeLastOrNull()
              backStack.add(History)
            },
          )
        }
      },
  )
}
