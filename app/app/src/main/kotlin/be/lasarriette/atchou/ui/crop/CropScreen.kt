package be.lasarriette.atchou.ui.crop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import be.lasarriette.atchou.R
import be.lasarriette.atchou.data.loadForUpload
import kotlin.math.min

private const val FRAME_ASPECT = 5f / 3f // panel width / height, in pixels
private const val MAX_ZOOM = 6f

/** Full-screen photo with a 5:3 box to move, pinch-resize and rotate (90° steps) before the send screen. */
@Composable
fun CropScreen(uri: String, onBack: () -> Unit, onNext: (rotate: Int, crop: String) -> Unit) {
  val context = LocalContext.current
  val source by produceState<Bitmap?>(null, uri) {
    value = runCatching {
      val bytes = loadForUpload(context, uri.toUri())
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
  }
  var rotate by remember { mutableIntStateOf(0) }
  var cx by remember { mutableFloatStateOf(0.5f) }
  var cy by remember { mutableFloatStateOf(0.5f) }
  var zoom by remember { mutableFloatStateOf(1f) }
  var area by remember { mutableStateOf(IntSize.Zero) }

  val image = remember(source, rotate) {
    source?.let { Bitmap.createBitmap(it, 0, 0, it.width, it.height, Matrix().apply { postRotate(rotate.toFloat()) }, true) }
  }

  /** The box in normalized coordinates of the rotated image: (width, height) at the current zoom. */
  fun boxSize(img: Bitmap, z: Float): Pair<Float, Float> {
    val w = img.width.toFloat()
    val h = img.height.toFloat()
    val (bw, bh) = if (w / h > FRAME_ASPECT) (h * FRAME_ASPECT / w) to 1f else 1f to (w / FRAME_ASPECT / h)
    return bw / z to bh / z
  }

  fun clampCenter(img: Bitmap) {
    val (bw, bh) = boxSize(img, zoom)
    cx = cx.coerceIn(bw / 2, 1 - bw / 2)
    cy = cy.coerceIn(bh / 2, 1 - bh / 2)
  }

  Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
    if (image == null) {
      CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    } else {
      val bitmap = remember(image) { image.asImageBitmap() }
      Canvas(
        modifier = Modifier.fillMaxSize()
          .onSizeChanged { area = it }
          .pointerInput(image) {
            detectTransformGestures { _, pan, scale, _ ->
              val fit = min(size.width.toFloat() / image.width, size.height.toFloat() / image.height)
              zoom = (zoom * scale).coerceIn(1f, MAX_ZOOM)
              cx += pan.x / (image.width * fit)
              cy += pan.y / (image.height * fit)
              clampCenter(image)
            }
          },
      ) {
        val fit = min(size.width / image.width, size.height / image.height)
        val imgW = image.width * fit
        val imgH = image.height * fit
        val left = (size.width - imgW) / 2
        val top = (size.height - imgH) / 2
        drawImage(
          bitmap,
          dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
          dstSize = IntSize(imgW.toInt(), imgH.toInt()),
        )
        val (bw, bh) = boxSize(image, zoom)
        val bx = left + (cx - bw / 2) * imgW
        val by = top + (cy - bh / 2) * imgH
        val boxW = bw * imgW
        val boxH = bh * imgH
        val dim = Color.Black.copy(alpha = 0.6f)
        drawRect(dim, Offset(0f, 0f), Size(size.width, by))
        drawRect(dim, Offset(0f, by + boxH), Size(size.width, size.height - by - boxH))
        drawRect(dim, Offset(0f, by), Size(bx, boxH))
        drawRect(dim, Offset(bx + boxW, by), Size(size.width - bx - boxW, boxH))
        drawRect(Color.White, Offset(bx, by), Size(boxW, boxH), style = Stroke(width = 4f))
      }

      Column(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(stringResource(R.string.crop_hint), color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          TextButton(onClick = onBack) { Text(stringResource(R.string.send_retake), color = Color.White) }
          TextButton(onClick = {
            rotate = (rotate + 90) % 360
            cx = 0.5f
            cy = 0.5f
            zoom = 1f
          }) { Text(stringResource(R.string.send_rotate), color = Color.White) }
          Button(onClick = {
            val (bw, bh) = boxSize(image, zoom)
            onNext(rotate, "${cx - bw / 2},${cy - bh / 2},$bw,$bh")
          }) { Text(stringResource(R.string.crop_next)) }
        }
      }
    }
  }
}
