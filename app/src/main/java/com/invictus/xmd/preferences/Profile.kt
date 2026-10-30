package com.invictus.xmd.preferences

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * The user's local profile: a display name (SharedPreferences, via
 * [Settings]) and an optional photo stored as a small square PNG in
 * filesDir. Nothing leaves the device. [name] and [avatarVersion] are
 * Compose state, so the Settings header updates live while editing.
 */
object Profile {

    private const val AVATAR_FILE = "profile_avatar.png"
    private const val AVATAR_PX = 256

    // Profile is first touched from the Settings screens, after Settings.init().
    private var nameState by mutableStateOf(Settings.profileName())

    /** Bumped whenever the photo changes, so composables know to reload it. */
    var avatarVersion by mutableIntStateOf(0)
        private set

    fun name(): String = nameState

    fun setName(value: String) {
        nameState = value
        Settings.setProfileName(value)
    }

    private fun avatarFile(context: Context) = File(context.applicationContext.filesDir, AVATAR_FILE)

    fun hasAvatar(context: Context): Boolean = avatarFile(context).exists()

    /** Blocking; call off the main thread. */
    fun loadAvatar(context: Context): Bitmap? =
        avatarFile(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    /** Blocking; call off the main thread. Crops to a centred square and downsizes. */
    fun saveAvatar(context: Context, uri: Uri): Boolean = runCatching {
        val resolver = context.applicationContext.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= AVATAR_PX && bounds.outHeight / (sample * 2) >= AVATAR_PX) {
            sample *= 2
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false

        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                when (android.media.ExifInterface(input).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL,
                )) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrNull() ?: 0f

        val side = minOf(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            if (rotation != 0f) postRotate(rotation)
            val scale = AVATAR_PX.toFloat() / side
            if (scale < 1f) postScale(scale, scale)
        }
        val square = Bitmap.createBitmap(
            decoded,
            (decoded.width - side) / 2,
            (decoded.height - side) / 2,
            side,
            side,
            matrix,
            true,
        )
        avatarFile(context).outputStream().use { square.compress(Bitmap.CompressFormat.PNG, 100, it) }
        avatarVersion++
        true
    }.getOrDefault(false)

    fun clearAvatar(context: Context) {
        avatarFile(context).delete()
        avatarVersion++
    }
}
