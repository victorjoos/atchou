package be.lasarriette.atchou

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import be.lasarriette.atchou.ui.camera.CameraScreen
import be.lasarriette.atchou.ui.history.HistoryScreen
import be.lasarriette.atchou.ui.send.SendScreen

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
            onImage = { uri, drawing -> backStack.add(Send(uri, drawing)) },
            onHistory = { backStack.add(History) },
          )
        }
        entry<History> { HistoryScreen(onNewPost = { backStack.removeLastOrNull() }) }
        entry<Send> { key ->
          SendScreen(
            uri = key.uri,
            drawing = key.drawing,
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
