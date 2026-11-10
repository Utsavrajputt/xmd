package com.invictus.xmd.ui.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.max

/**
 * Finds a QR code in a picked image. One pass is rarely enough for screenshots and photos, so
 * it tries, in order: the file as-is (EXIF rotation handled), a 2048px and a 1024px
 * downscale (huge modules decode better once shrunk), and a colour-inverted copy
 * (light-on-dark codes). Stops at the first hit.
 */
internal object QrImageDecoder {

    suspend fun decode(context: Context, uri: Uri): String? = withContext(Dispatchers.Default) {
        val client = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
        )
        try {
            runCatching { scan(client, InputImage.fromFilePath(context, uri)) }.getOrNull()
                ?: decodeScaled(context, uri, client, 2048, invert = false)
                ?: decodeScaled(context, uri, client, 1024, invert = false)
                ?: decodeScaled(context, uri, client, 2048, invert = true)
        } finally {
            client.close()
        }
    }

    private suspend fun decodeScaled(
        context: Context,
        uri: Uri,
        client: BarcodeScanner,
        maxSide: Int,
        invert: Boolean,
    ): String? {
        val bitmap = runCatching { loadBitmap(context, uri, maxSide) }.getOrNull() ?: return null
        val source = if (invert) invert(bitmap) else bitmap
        return runCatching { scan(client, InputImage.fromBitmap(source, 0)) }.getOrNull()
    }

    private suspend fun scan(client: BarcodeScanner, image: InputImage): String? =
        suspendCancellableCoroutine { cont ->
            client.process(image)
                .addOnSuccessListener { codes ->
                    cont.resume(codes.firstNotNullOfOrNull { it.rawValue?.trim()?.takeIf { v -> v.isNotEmpty() } })
                }
                .addOnFailureListener { cont.resume(null) }
        }

    private fun loadBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun invert(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }
}
