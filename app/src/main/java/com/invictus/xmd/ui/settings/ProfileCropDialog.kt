package com.invictus.xmd.ui.settings

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.invictus.xmd.R
import com.invictus.xmd.ui.icons.Icon
import com.invictus.xmd.ui.icons.Icons
import com.invictus.xmd.ui.theme.XmdShapes

private const val AVATAR_OUTPUT_PX = 384

/**
 * Crop screen shown after picking a profile photo: pinch to zoom, drag to
 * move, rotate left/right, reset. A circular cut-out shows what the avatar
 * will look like; [onCropped] receives a square bitmap.
 */
@Composable
fun ProfileCropDialog(
    sourceBitmap: Bitmap,
    onDismiss: () -> Unit,
    onCropped: (Bitmap) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val isTablet = configuration.smallestScreenWidthDp >= 600

    val cropBoxDp = minOf(configuration.screenWidthDp.dp - 64.dp, if (isTablet) 360.dp else 280.dp)
    val cropBoxPx = with(density) { cropBoxDp.toPx() }

    var rotation by remember { mutableStateOf(0) }
    var userScale by remember { mutableStateOf(1f) }
    var userOffset by remember { mutableStateOf(Offset.Zero) }

    val rotatedBitmap = remember(sourceBitmap, rotation) { rotateBitmap(sourceBitmap, rotation) }
    val rotatedImage = remember(rotatedBitmap) { rotatedBitmap.asImageBitmap() }

    val baseScale = remember(rotatedImage, cropBoxPx) {
        maxOf(cropBoxPx / rotatedImage.width, cropBoxPx / rotatedImage.height)
    }

    // Pan limits so the image always fully covers the crop box.
    val maxPanX = ((rotatedImage.width * baseScale * userScale - cropBoxPx) / 2f).coerceAtLeast(0f)
    val maxPanY = ((rotatedImage.height * baseScale * userScale - cropBoxPx) / 2f).coerceAtLeast(0f)
    LaunchedEffect(maxPanX, maxPanY) {
        userOffset = Offset(
            userOffset.x.coerceIn(-maxPanX, maxPanX),
            userOffset.y.coerceIn(-maxPanY, maxPanY),
        )
    }

    fun resetView() {
        userScale = 1f
        userOffset = Offset.Zero
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (isTablet) 0.65f else 0.94f)
                .clip(XmdShapes.L),
            shape = XmdShapes.L,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_profile_crop_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                }

                // Viewport
                Box(
                    modifier = Modifier
                        .size(cropBoxDp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .pointerInput(rotatedImage, cropBoxPx) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                userScale = (userScale * zoom).coerceIn(1f, 5f)
                                val px = ((rotatedImage.width * baseScale * userScale - cropBoxPx) / 2f).coerceAtLeast(0f)
                                val py = ((rotatedImage.height * baseScale * userScale - cropBoxPx) / 2f).coerceAtLeast(0f)
                                userOffset = Offset(
                                    (userOffset.x + pan.x).coerceIn(-px, px),
                                    (userOffset.y + pan.y).coerceIn(-py, py),
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = rotatedImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(
                                width = with(density) { (rotatedImage.width * baseScale).toDp() },
                                height = with(density) { (rotatedImage.height * baseScale).toDp() },
                            )
                            .graphicsLayer {
                                scaleX = userScale
                                scaleY = userScale
                                translationX = userOffset.x
                                translationY = userOffset.y
                            },
                    )

                    // Circular cut-out overlay.
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val radius = minOf(size.width, size.height) / 2f
                        val path = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(0f, 0f, size.width, size.height))
                            addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
                        }
                        drawPath(path, color = Color.Black.copy(alpha = 0.60f))
                        drawCircle(
                            color = Color.White.copy(alpha = 0.85f),
                            radius = radius - 1f,
                            center = center,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }

                // Rotate left / right / reset
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = { rotation = (rotation - 90 + 360) % 360; resetView() },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.RotateLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.settings_profile_crop_left), style = MaterialTheme.typography.labelMedium)
                    }
                    FilledTonalButton(
                        onClick = { rotation = (rotation + 90) % 360; resetView() },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.settings_profile_crop_right), style = MaterialTheme.typography.labelMedium)
                    }
                    FilledTonalButton(
                        onClick = { rotation = 0; resetView() },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.settings_profile_crop_reset), style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Cancel / Crop
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    Button(
                        onClick = {
                            onCropped(
                                performCrop(
                                    bitmap = sourceBitmap,
                                    rotation = rotation,
                                    userScale = userScale,
                                    userOffset = userOffset,
                                    viewportSizePx = cropBoxPx,
                                ),
                            )
                        },
                    ) { Text(stringResource(R.string.settings_profile_crop_confirm)) }
                }
            }
        }
    }
}

private fun rotateBitmap(bitmap: Bitmap, rotation: Int): Bitmap =
    if (rotation % 360 != 0) {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }

/** Cuts the square the user framed out of [bitmap] and scales it to [AVATAR_OUTPUT_PX]. */
private fun performCrop(
    bitmap: Bitmap,
    rotation: Int,
    userScale: Float,
    userOffset: Offset,
    viewportSizePx: Float,
): Bitmap {
    val rotated = rotateBitmap(bitmap, rotation)
    val bw = rotated.width.toFloat()
    val bh = rotated.height.toFloat()

    val baseScale = maxOf(viewportSizePx / bw, viewportSizePx / bh)
    val totalScale = baseScale * userScale

    val cropSizeInImage = (viewportSizePx / totalScale).coerceAtMost(minOf(bw, bh))
    val cropCenterX = bw / 2f - userOffset.x / totalScale
    val cropCenterY = bh / 2f - userOffset.y / totalScale

    val srcX = (cropCenterX - cropSizeInImage / 2f).toInt().coerceIn(0, (bw - cropSizeInImage).toInt().coerceAtLeast(0))
    val srcY = (cropCenterY - cropSizeInImage / 2f).toInt().coerceIn(0, (bh - cropSizeInImage).toInt().coerceAtLeast(0))
    val srcW = cropSizeInImage.toInt().coerceAtMost(rotated.width - srcX)
    val srcH = cropSizeInImage.toInt().coerceAtMost(rotated.height - srcY)
    val cropSide = minOf(srcW, srcH).coerceAtLeast(1)

    val scaleMatrix = Matrix().apply {
        val scale = AVATAR_OUTPUT_PX.toFloat() / cropSide
        postScale(scale, scale)
    }
    return Bitmap.createBitmap(rotated, srcX, srcY, cropSide, cropSide, scaleMatrix, true)
}
