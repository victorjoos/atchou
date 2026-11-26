package be.lasarriette.atchou.ui.send

import be.lasarriette.atchou.R
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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
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
import be.lasarriette.atchou.ui.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(uri: String, drawing: Boolean, rotate: Int, crop: String?, onBack: () -> Unit, onDone: () -> Unit, viewModel: SendViewModel = appViewModel { SendViewModel(it) }) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  var boxSize by remember { mutableStateOf(IntSize.Zero) }
  var pickingDate by remember { mutableStateOf(false) }

  LaunchedEffect(uri) { viewModel.onPicked(uri.toUri(), drawing, rotate, crop) }
  LaunchedEffect(state.done) { if (state.done) onDone() }

  Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.send_title)) }) }) { padding ->
    Column(
      modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(5f / 3f).clipToBounds()
          .onSizeChanged { boxSize = it }
          .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
              if (boxSize.width > 0) viewModel.transform(pan.x / boxSize.width, pan.y / boxSize.height, zoom)
            }
          },
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
      Text(stringResource(R.string.send_crop_hint), style = MaterialTheme.typography.bodySmall)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.send_retake)) }
        OutlinedButton(onClick = viewModel::rotate) { Text(stringResource(R.string.send_rotate)) }
      }
      Text(stringResource(R.string.send_brightness))
      Slider(value = state.brightness, onValueChange = viewModel::setBrightness, valueRange = 0.5f..1.5f)
      Text(stringResource(R.string.send_contrast))
      Slider(value = state.contrast, onValueChange = viewModel::setContrast, valueRange = 0.5f..1.5f)

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

      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.send_pinned), modifier = Modifier.weight(1f))
        Switch(checked = state.pinned, onCheckedChange = viewModel::setPinned)
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.send_exclude), modifier = Modifier.weight(1f))
        Switch(checked = state.excludeFromRotation, onCheckedChange = viewModel::setExclude)
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.send_show_on), modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { pickingDate = true }) { Text(state.showOn ?: stringResource(R.string.send_show_on_today)) }
        if (state.showOn != null) TextButton(onClick = { viewModel.setShowOn(null) }) { Text(stringResource(R.string.send_show_on_clear)) }
      }

      state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

      Button(
        onClick = viewModel::send,
        enabled = state.hasImage && !state.sending,
        modifier = Modifier.fillMaxWidth(),
      ) { Text(stringResource(if (state.sending) R.string.send_sending else R.string.send_submit)) }
    }
  }
  if (pickingDate) DateDialog { picked ->
    pickingDate = false
    if (picked != null) viewModel.setShowOn(picked)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(onPicked: (String?) -> Unit) {
  val pickerState = rememberDatePickerState()
  DatePickerDialog(
    onDismissRequest = { onPicked(null) },
    confirmButton = {
      TextButton(onClick = {
        val millis = pickerState.selectedDateMillis
        onPicked(millis?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() })
      }) { Text(stringResource(android.R.string.ok)) }
    },
    dismissButton = { TextButton(onClick = { onPicked(null) }) { Text(stringResource(android.R.string.cancel)) } },
  ) { DatePicker(state = pickerState) }
}
