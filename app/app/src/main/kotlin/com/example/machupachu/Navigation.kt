package com.example.machupachu

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.machupachu.ui.history.HistoryScreen
import com.example.machupachu.ui.send.SendScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(History)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<History> { HistoryScreen(onNewPost = { backStack.add(Send) }) }
        entry<Send> { SendScreen(onDone = { backStack.removeLastOrNull() }) }
      },
  )
}
