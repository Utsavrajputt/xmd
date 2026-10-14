package com.invictus.xmd.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.invictus.xmd.R

/** Backdrop of a launcher icon. Each one needs a color in colors_app_icon.xml. */
internal enum class IconBackground(
    val key: String,
    @ColorRes val color: Int,
    @StringRes val label: Int,
) {
    White("White", R.color.icon_bg_white, R.string.app_icon_bg_white),
    BlueGrey("BlueGrey", R.color.icon_bg_bluegrey, R.string.app_icon_bg_bluegrey),
    Brown("Brown", R.color.icon_bg_brown, R.string.app_icon_bg_brown),
}

/** The xmd logo drawn over a background, as a 108dp adaptive foreground layer (raster mipmap). */
internal enum class IconMark(
    val key: String,
    @DrawableRes val layer: Int,
    @StringRes val label: Int,
) {
    Color("Color", R.mipmap.ic_launcher_fg_color, R.string.app_icon_logo_color),
    White("White", R.mipmap.ic_launcher_fg_white, R.string.app_icon_logo_white),
    Black("Black", R.mipmap.ic_launcher_fg_black, R.string.app_icon_logo_black),
}

/**
 * One launcher icon: a background with a logo over it. Only pairs that read well together exist
 * (a white logo on the white background does not), so this catalog is both the list of icons and
 * the rule for which logos suit which backgrounds. A new pair needs a row here, a mipmap in
 * mipmap-anydpi-v26 and an activity-alias named [aliasName] in the manifest.
 */
internal class LauncherIcon(
    val background: IconBackground,
    val mark: IconMark,
) {
    /** Class name of the manifest alias. Alias names resolve against the namespace, not the applicationId. */
    val aliasName: String get() = "com.invictus.xmd.Icon${background.key}${mark.key}"

    override fun equals(other: Any?) =
        other is LauncherIcon && other.background == background && other.mark == mark

    override fun hashCode() = background.hashCode() * 31 + mark.hashCode()

    companion object {
        val entries: List<LauncherIcon> = listOf(
            LauncherIcon(IconBackground.White, IconMark.Color),
            LauncherIcon(IconBackground.White, IconMark.Black),
            LauncherIcon(IconBackground.BlueGrey, IconMark.Color),
            LauncherIcon(IconBackground.BlueGrey, IconMark.White),
            LauncherIcon(IconBackground.BlueGrey, IconMark.Black),
            LauncherIcon(IconBackground.Brown, IconMark.Color),
            LauncherIcon(IconBackground.Brown, IconMark.White),
            LauncherIcon(IconBackground.Brown, IconMark.Black),
        )

        /** The alias that is enabled in the manifest: white background, colored logo. */
        val Default: LauncherIcon = of(IconBackground.White, IconMark.Color)!!

        fun of(background: IconBackground, mark: IconMark): LauncherIcon? =
            entries.firstOrNull { it.background == background && it.mark == mark }

        /** The icon for [background] keeping [mark] when it reads there, else the background's own logo. */
        fun resolve(background: IconBackground, mark: IconMark): LauncherIcon =
            of(background, mark) ?: entries.first { it.background == background }
    }
}

internal object LauncherIconManager {
    private const val ENABLED = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    private const val DISABLED = PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    private fun component(context: Context, name: String) = ComponentName(context.packageName, name)

    private fun stateOf(context: Context, name: String) =
        context.packageManager.getComponentEnabledSetting(component(context, name))

    /** What the package manager says is shown; the manifest-default alias counts as on until something else is enabled. */
    fun current(context: Context): LauncherIcon =
        LauncherIcon.entries.firstOrNull { stateOf(context, it.aliasName) == ENABLED } ?: LauncherIcon.Default

    private fun applyStates(context: Context, changes: Map<ComponentName, Int>) {
        val pm = context.packageManager
        val flags = PackageManager.DONT_KILL_APP
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.setComponentEnabledSettings(
                changes.map { (name, state) -> PackageManager.ComponentEnabledSetting(name, state, flags) },
            )
        } else {
            // Turn the new one on before the rest go off, so the app never has no launcher entry.
            changes.filter { it.value == ENABLED }.forEach { (n, s) -> pm.setComponentEnabledSetting(n, s, flags) }
            changes.filter { it.value != ENABLED }.forEach { (n, s) -> pm.setComponentEnabledSetting(n, s, flags) }
        }
    }

    /** Makes exactly [icon] show on the launcher; rolls back if the change did not stick. */
    @Synchronized
    @Suppress("DEPRECATION")
    fun select(context: Context, icon: LauncherIcon) {
        val pm = context.packageManager
        // Fail before touching anything if this build lacks the alias.
        pm.getActivityInfo(component(context, icon.aliasName), PackageManager.MATCH_DISABLED_COMPONENTS)

        val changes = LauncherIcon.entries.associate {
            component(context, it.aliasName) to (if (it == icon) ENABLED else DISABLED)
        }
        val previous = changes.keys.associateWith { pm.getComponentEnabledSetting(it) }
        try {
            applyStates(context, changes)
            check(changes.all { (name, state) -> pm.getComponentEnabledSetting(name) == state }) {
                "Launcher alias changes were not applied"
            }
        } catch (failure: Exception) {
            runCatching { applyStates(context, previous) }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }
}
