package com.invictus.xmd.utils

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import com.invictus.xmd.preferences.Settings
import java.util.WeakHashMap

/**
 * App-wide UI size (ported from Replay's "App UI scale"). Only the screen density is overridden, so
 * every dp/sp in Compose and XML scales together. Orientation-dependent fields such as
 * `screenWidthDp` are left alone: the framework re-merges this delta on every configuration change.
 * At 100% nothing is overridden at all, so the system font size keeps working untouched.
 */
object AppUiScale {
    const val MIN = 0.75f
    const val MAX = 1.30f
    const val DEFAULT = 1f
    const val STEP = 0.05f

    /** Slider steps strictly between MIN and MAX (75..130 in 5% steps = 12 stops). */
    const val SLIDER_STEPS = 10

    /** The scale each live activity was created with, so a changed setting can recreate stale ones. */
    private val applied = WeakHashMap<Activity, Float>()

    fun snap(value: Float): Float {
        val steps = Math.round((value.coerceIn(MIN, MAX) - MIN) / STEP)
        return (MIN + steps * STEP).coerceIn(MIN, MAX)
    }

    /** Call from `attachBaseContext`, before `super.attachBaseContext`. */
    fun attach(activity: Activity, newBase: Context?) {
        val scale = Settings.appUiScale()
        applied[activity] = scale
        if (newBase == null || scale == DEFAULT) return
        val config = Configuration().apply {
            densityDpi = (newBase.resources.configuration.densityDpi * scale).toInt()
        }
        activity.applyOverrideConfiguration(config)
    }

    /** Recreates [activity] if the saved scale changed since it was created. */
    fun recreateIfStale(activity: Activity) {
        val was = applied[activity] ?: return
        if (was != Settings.appUiScale()) activity.recreate()
    }
}
