package com.example.machupachu.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.machupachu.data.Post

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onNewPost: () -> Unit, viewModel: HistoryViewModel = viewModel()) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  var showSettings by remember { mutableStateOf(false) }
  var toDelete by remember { mutableStateOf<Post?>(null) }

  LaunchedEffect(Unit) { viewModel.refresh() }

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text("Machu Pachu") },
        actions = { TextButton(onClick = { showSettings = true }) { Text("Server") } },
      )
    },
    floatingActionButton = { ExtendedFloatingActionButton(onClick = onNewPost) { Text("New post") } },
  ) { padding ->
    PullToRefreshBox(
      isRefreshing = state.loading,
      onRefresh = viewModel::refresh,
      modifier = Modifier.padding(padding).fillMaxSize(),
    ) {
      Column {
        state.error?.let {
          Text("Error: $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }
        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = 160.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          items(state.posts, key = { it.id }) { post ->
            PostCard(post, viewModel.url(post.imageUrl), onDelete = { toDelete = post })
          }
        }
      }
    }
  }

  toDelete?.let { post ->
    AlertDialog(
      onDismissRequest = { toDelete = null },
      title = { Text("Delete this post?") },
      text = { Text("It will be removed for everyone.") },
      confirmButton = {
        TextButton(onClick = { viewModel.delete(post); toDelete = null }) { Text("Delete") }
      },
      dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
    )
  }

  if (showSettings) {
    var url by remember { mutableStateOf(viewModel.serverUrl) }
    AlertDialog(
      onDismissRequest = { showSettings = false },
      title = { Text("Server URL") },
      text = { OutlinedTextField(value = url, onValueChange = { url = it }, singleLine = true) },
      confirmButton = {
        TextButton(onClick = { viewModel.serverUrl = url; showSettings = false }) { Text("Save") }
      },
      dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun PostCard(post: Post, imageUrl: String, onDelete: () -> Unit) {
  Card(modifier = Modifier.fillMaxWidth()) {
    AsyncImage(
      model = imageUrl,
      contentDescription = "Post by ${post.author}",
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxWidth().aspectRatio(5f / 3f),
    )
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(post.author.ifBlank { "(no author)" }, style = MaterialTheme.typography.titleSmall)
        Text(post.createdAt.take(10), style = MaterialTheme.typography.bodySmall)
      }
      TextButton(onClick = onDelete) { Text("Delete") }
    }
  }
}
