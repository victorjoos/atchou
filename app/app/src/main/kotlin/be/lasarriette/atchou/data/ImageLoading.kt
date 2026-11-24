package be.lasarriette.atchou.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_SIDE = 1600

/** Decodes (honoring EXIF rotation), downsizes and re-encodes as JPEG for upload. */
suspend fun loadForUpload(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
  val source = ImageDecoder.createSource(context.contentResolver, uri)
  val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    val longest = max(info.size.width, info.size.height)
    if (longest > MAX_SIDE) {
      val scale = MAX_SIDE.toFloat() / longest
      decoder.setTargetSize(
        (info.size.width * scale).roundToInt(),
        (info.size.height * scale).roundToInt(),
      )
    }
  }
  ByteArrayOutputStream().use {
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
    it.toByteArray()
  }
}
