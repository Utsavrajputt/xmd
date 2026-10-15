package com.invictus.xmd.ui.components

import android.graphics.drawable.Animatable
import android.os.Build
import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.invictus.xmd.ui.settings.LauncherIcon
import com.invictus.xmd.ui.settings.LauncherIconManager
import kotlinx.coroutines.delay

/** Process-wide: the splash plays once per cold start, not again when the activity is recreated. */
private object LaunchSplashState {
    @Volatile var played = false
}

private const val HOLD_MS = 800L
private const val FADE_MS = 250

/**
 * Splash drawn inside the app for Android 11 and older, which have no system splash API: the chosen
 * icon's background with its animated logo, held briefly and faded out over the content. Android 12+
 * shows the system splash (see LauncherIconManager.applySplashTheme), so this does nothing there.
 */
@Composable
fun AppLaunchSplash() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
    val show = remember {
        val first = !LaunchSplashState.played
        LaunchSplashState.played = true
        first
    }
    if (!show) return

    val context = LocalContext.current
    val icon = remember { runCatching { LauncherIconManager.current(context) }.getOrDefault(LauncherIcon.Default) }
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(HOLD_MS)
        visible = false
    }

    AnimatedVisibility(visible = visible, exit = fadeOut(tween(FADE_MS))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(icon.background.color))
                // Keeps taps from reaching the app while the splash is up.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                modifier = Modifier.size(288.dp),
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageResource(icon.mark.splash)
                        (drawable as? Animatable)?.start()
                    }
                },
            )
        }
    }
}
