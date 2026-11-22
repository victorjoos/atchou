package com.example.machupachu.ui.camera

import com.example.machupachu.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.Context
import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.OrientationEventListener
import android.view.Surface
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.io.File

@Composable
fun CameraScreen(onImage: (String, Boolean) -> Unit, onHistory: () -> Unit) {
  val context = LocalContext.current
  var hasPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
    )
  }
  var permissionAsked by remember { mutableStateOf(false) }
  val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
    hasPermission = it
    permissionAsked = true
  }
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
    uri?.let { onImage(it.toString(), false) }
  }
  val pickFromGallery = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

  // Like a stock camera app: the UI stays put and only the labels turn with the phone.
  DisposableEffect(context) {
    val activity = context as? Activity
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
  }

  LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

  Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
    if (hasPermission) {
      CameraContent(onImage = onImage, onGallery = pickFromGallery, onHistory = onHistory)
    } else {
      Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          stringResource(if (permissionAsked) R.string.camera_permission_denied else R.string.camera_permission_needed),
          color = Color.White,
        )
        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.camera_allow)) }
        Button(onClick = pickFromGallery) { Text(stringResource(R.string.camera_choose_gallery)) }
        TextButton(onClick = onHistory) { Text(stringResource(R.string.camera_history)) }
      }
    }
  }
}

@Composable
private fun CameraContent(onImage: (String, Boolean) -> Unit, onGallery: () -> Unit, onHistory: () -> Unit) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  // FIT_CENTER shows the whole captured frame so the crop overlay lines up with it.
  val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
  val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
  var useBack by remember { mutableStateOf(true) }
  var capturing by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var drawingMode by remember { mutableStateOf(false) }

  // ML Kit's scanner detects the sheet, lets the user adjust the corners and returns a flattened, contrast-enhanced image.
  val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
    GmsDocumentScanningResult.fromActivityResultIntent(result.data)
      ?.pages?.firstOrNull()?.imageUri?.let { onImage(it.toString(), true) }
  }
  val scan = {
    val activity = context as Activity
    val options = GmsDocumentScannerOptions.Builder()
      .setGalleryImportAllowed(true)
      .setPageLimit(1)
      .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
      .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
      .build()
    GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
      .addOnSuccessListener { scanLauncher.launch(IntentSenderRequest.Builder(it).build()) }
      .addOnFailureListener { error = context.getString(R.string.camera_scan_failed, it.message) }
  }

  LaunchedEffect(useBack) {
    val provider = ProcessCameraProvider.getInstance(context).get()
    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
    val selector = if (useBack) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
    try {
      provider.unbindAll()
      provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
    } catch (e: Exception) {
      error = context.getString(R.string.camera_unavailable, e.message)
    }
  }

  // Physical orientation of the phone (the UI itself is locked to portrait).
  var deviceRotation by remember { mutableStateOf(Surface.ROTATION_0) }
  DisposableEffect(context) {
    val listener = object : OrientationEventListener(context) {
      override fun onOrientationChanged(orientation: Int) {
        if (orientation == ORIENTATION_UNKNOWN) return
        deviceRotation = when (orientation) {
          in 45..134 -> Surface.ROTATION_270
          in 225..314 -> Surface.ROTATION_90
          in 135..224 -> return // upside down: keep the last state
          else -> Surface.ROTATION_0
        }
        imageCapture.targetRotation = deviceRotation
      }
    }
    listener.enable()
    onDispose { listener.disable() }
  }
  val heldLandscape = deviceRotation != Surface.ROTATION_0
  val labelAngle by animateFloatAsState(
    when (deviceRotation) {
      Surface.ROTATION_90 -> 90f
      Surface.ROTATION_270 -> -90f
      else -> 0f
    },
    label = "labelAngle",
  )
  val flip = @Composable {
    TextButton(onClick = { useBack = !useBack }) { Text(stringResource(R.string.camera_flip), color = Color.White, modifier = Modifier.rotate(labelAngle)) }
  }
  val gallery = @Composable {
    TextButton(onClick = onGallery, modifier = Modifier.size(width = 96.dp, height = 48.dp)) {
      Text(stringResource(R.string.camera_gallery), color = Color.White, modifier = Modifier.rotate(labelAngle))
    }
  }
  val history = @Composable {
    TextButton(onClick = onHistory, modifier = Modifier.size(width = 96.dp, height = 48.dp)) {
      Text(stringResource(R.string.camera_history), color = Color.White, modifier = Modifier.rotate(labelAngle))
    }
  }
  val shutter = @Composable {
    Box(
      modifier = Modifier
        .size(76.dp)
        .border(4.dp, Color.White, CircleShape)
        .padding(8.dp)
        .background(if (capturing) Color.Gray else Color.White, CircleShape)
        .clickable(enabled = !capturing) {
          if (drawingMode) {
            error = null
            scan()
            return@clickable
          }
          capturing = true
          takePicture(
            context,
            imageCapture,
            onImage = { capturing = false; onImage(it, false) },
            onError = { capturing = false; error = context.getString(R.string.camera_capture_failed, it) },
          )
        },
    )
  }

  Box(modifier = Modifier.fillMaxSize()) {
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
    if (!drawingMode) CropOverlay(heldLandscape = heldLandscape, modifier = Modifier.fillMaxSize())

    run {
      Row(modifier = Modifier.statusBarsPadding().fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) { flip() }
      Column(modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp)) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.Center) {
          TextButton(onClick = { drawingMode = false }) {
            Text(stringResource(R.string.camera_mode_photo), color = if (drawingMode) Color.LightGray else Color.Yellow)
          }
          TextButton(onClick = { drawingMode = true }) {
            Text(stringResource(R.string.camera_mode_drawing), color = if (drawingMode) Color.Yellow else Color.LightGray)
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          gallery()
          shutter()
          history()
        }
      }
    }
  }
}

private fun takePicture(context: Context, capture: ImageCapture, onImage: (String) -> Unit, onError: (String) -> Unit) {
  val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
  capture.takePicture(
    ImageCapture.OutputFileOptions.Builder(file).build(),
    ContextCompat.getMainExecutor(context),
    object : ImageCapture.OnImageSavedCallback {
      override fun onImageSaved(output: ImageCapture.OutputFileResults) = onImage(file.toUri().toString())
      override fun onError(exception: ImageCaptureException) = onError(exception.message ?: "unknown error")
    },
  )
}

private const val FRAME_ASPECT = 5f / 3f // panel width / height
private const val CAPTURE_LONG = 4f // ImageCapture default is 4:3
private const val CAPTURE_SHORT = 3f

/** Dims the parts of the captured frame that the server will crop away. */
@Composable
private fun CropOverlay(heldLandscape: Boolean, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val portrait = size.height >= size.width
    val imageAspect = if (portrait) CAPTURE_SHORT / CAPTURE_LONG else CAPTURE_LONG / CAPTURE_SHORT
    // Displayed image rect (FIT_CENTER inside the view).
    val imgW = if (size.width / size.height < imageAspect) size.width else size.height * imageAspect
    val imgH = imgW / imageAspect
    val imgLeft = (size.width - imgW) / 2
    val imgTop = (size.height - imgH) / 2
    // Centered 5:3 crop inside the image rect, matching the server's default crop.
    // Held sideways, the captured frame is landscape, so the 5:3 crop appears as 3:5 on the portrait screen.
    val frameAspect = if (heldLandscape) 1f / FRAME_ASPECT else FRAME_ASPECT
    val cropW = if (imageAspect < frameAspect) imgW else imgH * frameAspect
    val cropH = cropW / frameAspect
    val cropLeft = imgLeft + (imgW - cropW) / 2
    val cropTop = imgTop + (imgH - cropH) / 2
    val dim = Color.Black.copy(alpha = 0.55f)

    drawRect(dim, Offset(0f, 0f), Size(size.width, cropTop))
    drawRect(dim, Offset(0f, cropTop + cropH), Size(size.width, size.height - cropTop - cropH))
    drawRect(dim, Offset(0f, cropTop), Size(cropLeft, cropH))
    drawRect(dim, Offset(cropLeft + cropW, cropTop), Size(size.width - cropLeft - cropW, cropH))
    drawRect(Color.White, Offset(cropLeft, cropTop), Size(cropW, cropH), style = Stroke(width = 3f))
  }
}
