@file:OptIn(androidx.camera.core.ExperimentalGetImage::class)
package com.invictus.xmd.ui.qr

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Size
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.invictus.xmd.R
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import com.invictus.xmd.ui.theme.XmdTheme
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The app's own QR scanner: CameraX preview + analysis feeding ML Kit's *bundled* barcode
 * model (offline, no Google Play services). Restricted to QR so every frame is spent on
 * one format; 1080p analysis frames, continuous autofocus + tap-to-focus, and ML Kit's
 * auto-zoom (it asks for a zoom-in when a code is seen but too small to decode).
 * Also scans a picked image ([QrImageDecoder]) and has a torch toggle.
 *
 * Returns the raw text as [EXTRA_TEXT] with RESULT_OK; closing returns RESULT_CANCELED.
 */
class QrScannerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            XmdTheme {
                QrScannerScreen(
                    onResult = { text ->
                        setResult(RESULT_OK, Intent().putExtra(EXTRA_TEXT, text))
                        finish()
                    },
                    onClose = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_TEXT = "com.invictus.xmd.QR_TEXT"
    }
}

@Composable
private fun QrScannerScreen(onResult: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var askedOnce by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }
    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            askedOnce = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // First code wins; later frames/gallery results are ignored.
    val finished = remember { AtomicBoolean(false) }
    val deliver: (String) -> Unit = { text ->
        if (finished.compareAndSet(false, true)) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onResult(text)
        }
    }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
    LaunchedEffect(camera, torchOn) { camera?.cameraControl?.enableTorch(torchOn) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val text = QrImageDecoder.decode(context, uri)
                if (text != null) deliver(text)
                else Toast.makeText(context, R.string.qr_scanner_no_code_in_image, Toast.LENGTH_LONG).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (permissionGranted) {
            CameraPreview(
                onCamera = { camera = it },
                onCode = deliver,
                onError = {
                    Toast.makeText(context, R.string.qr_scanner_unavailable, Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.fillMaxSize(),
            )
            Viewfinder()
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.qr_scanner_permission_needed),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    if (askedOnce) {
                        // Already asked (and refused): only the app's settings page can grant it now.
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", context.packageName, null)),
                        )
                    } else {
                        askedOnce = true
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }) { Text(stringResource(R.string.qr_scanner_grant_permission)) }
            }
        }

        // Top bar: close / torch.
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScannerButton(onClick = onClose) {
                Icon(imageVector = Icons.Close, contentDescription = stringResource(android.R.string.cancel), tint = Color.White)
            }
            if (hasFlash) {
                ScannerButton(onClick = { torchOn = !torchOn }) {
                    Icon(
                        imageVector = if (torchOn) Icons.FlashOn else Icons.FlashOff,
                        contentDescription = stringResource(R.string.qr_scanner_torch),
                        tint = if (torchOn) Color.Yellow else Color.White,
                    )
                }
            }
        }

        // Bottom: hint + gallery.
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.qr_scanner_hint),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = { galleryLauncher.launch("image/*") }) {
                Icon(imageVector = Icons.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.qr_scanner_from_gallery))
            }
        }
    }
}

@Composable
private fun ScannerButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
    ) { content() }
}

/** Dimmed screen with a clear rounded square and a white outline. */
@Composable
private fun Viewfinder() {
    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        drawRect(Color.Black.copy(alpha = 0.55f))
        val side = minOf(size.width, size.height) * 0.68f
        val topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f - size.height * 0.05f)
        val box = androidx.compose.ui.geometry.Size(side, side)
        val corner = CornerRadius(28.dp.toPx())
        drawRoundRect(Color.Transparent, topLeft, box, corner, blendMode = BlendMode.Clear)
        drawRoundRect(Color.White, topLeft, box, corner, style = Stroke(width = 3.dp.toPx()))
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun CameraPreview(
    onCamera: (Camera?) -> Unit,
    onCode: (String) -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnCode by rememberUpdatedState(onCode)
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var scanner: BarcodeScanner? = null

        providerFuture.addListener({
            try {
                val cameraProvider = providerFuture.get()
                provider = cameraProvider

                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                // 1080p frames: dense / small codes need the pixels; KEEP_ONLY_LATEST never queues stale frames.
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(1920, 1080),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            )
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                cameraProvider.unbindAll()
                val cam = cameraProvider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
                )

                // ML Kit tells us when a code is visible but too small; zoom in as it suggests.
                val maxZoom = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
                val zoomOptions = ZoomSuggestionOptions.Builder(
                    ZoomSuggestionOptions.ZoomCallback { zoom ->
                        cam.cameraControl.setZoomRatio(zoom)
                        true
                    },
                ).setMaxSupportedZoomRatio(maxZoom).build()
                val client = BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .setZoomSuggestionOptions(zoomOptions)
                        .build(),
                )
                scanner = client

                analysis.setAnalyzer(analysisExecutor) { proxy ->
                    val media = proxy.image
                    if (media == null) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    try {
                        val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                        client.process(input)
                            .addOnSuccessListener { codes ->
                                codes.firstNotNullOfOrNull { it.rawValue?.trim()?.takeIf { v -> v.isNotEmpty() } }
                                    ?.let { latestOnCode(it) }
                            }
                            .addOnCompleteListener { proxy.close() }
                    } catch (e: Exception) {
                        proxy.close()
                    }
                }

                // Tap to focus.
                previewView.setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                        cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                        v.performClick()
                    }
                    true
                }
                onCamera(cam)
            } catch (e: Exception) {
                onError()
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            provider?.unbindAll()
            scanner?.close()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
