package com.example.machupachu.ui.send

import com.example.machupachu.R
import androidx.compose.ui.res.stringResource
import android.graphics.BitmapFactory
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.machupachu.ui.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(uri: String, drawing: Boolean, onBack: () -> Unit, onDone: () -> Unit, viewModel: SendViewModel = appViewModel { SendViewModel(it) }) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  LaunchedEffect(uri) { viewModel.onPicked(uri.toUri(), drawing) }
  LaunchedEffect(state.done) { if (state.done) onDone() }

  Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.send_title)) }) }) { padding ->
    Column(
      modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(5f / 3f),
        contentAlignment = Alignment.Center,
      ) {
        val bitmap = remember(state.preview) {
          state.preview?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }
        if (bitmap != null) {
          Image(bitmap, contentDescription = stringResource(R.string.send_preview), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
        }
        if (state.previewLoading || (!state.hasImage && state.error == null)) CircularProgressIndicator()
      }
      OutlinedButton(onClick = onBack) { Text(stringResource(R.string.send_retake)) }

      OutlinedTextField(
        value = state.author,
        onValueChange = viewModel::setAuthor,
        label = { Text(stringResource(R.string.send_author)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.authors.forEach { name ->
          FilterChip(selected = state.author == name, onClick = { viewModel.setAuthor(name) }, label = { Text(name) })
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.send_show_author), modifier = Modifier.weight(1f))
        Switch(checked = state.showAuthor, onCheckedChange = viewModel::setShowAuthor)
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.send_dither), modifier = Modifier.weight(1f))
        Switch(checked = state.mode == "dither", onCheckedChange = { viewModel.setDither(it) })
      }

      state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

      Button(
        onClick = viewModel::send,
        enabled = state.hasImage && !state.sending,
        modifier = Modifier.fillMaxWidth(),
      ) { Text(stringResource(if (state.sending) R.string.send_sending else R.string.send_submit)) }
    }
  }
}
