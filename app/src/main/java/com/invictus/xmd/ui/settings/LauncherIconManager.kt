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
    Black("Black", R.color.icon_bg_black, R.string.app_icon_bg_black),
    White("White", R.color.icon_bg_white, R.string.app_icon_bg_white),
    Charcoal("Charcoal", R.color.icon_bg_charcoal, R.string.app_icon_bg_charcoal),
    Indigo("Indigo", R.color.icon_bg_indigo, R.string.app_icon_bg_indigo),
    Violet("Violet", R.color.icon_bg_violet, R.string.app_icon_bg_violet),
    Lagoon("Lagoon", R.color.icon_bg_lagoon, R.string.app_icon_bg_lagoon),
    Azure("Azure", R.color.icon_bg_azure, R.string.app_icon_bg_azure),
    Lilac("Lilac", R.color.icon_bg_lilac, R.string.app_icon_bg_lilac),
    Mint("Mint", R.color.icon_bg_mint, R.string.app_icon_bg_mint),
    Ember("Ember", R.color.icon_bg_ember, R.string.app_icon_bg_ember),
}

/** The xmd logo drawn over a background, as a 108dp adaptive foreground vector layer. */
internal enum class IconMark(
    val key: String,
    @DrawableRes val layer: Int,
    @StringRes val label: Int,
) {
    /** The original logo, pale body with blue arrow: reads on dark and saturated backgrounds. */
    Color("Color", R.drawable.ic_launcher_fg_color, R.string.app_icon_logo_color),
    /** Same logo in deeper blues, for the light backgrounds the original does not read on. */
    Blue("Blue", R.drawable.ic_launcher_fg_blue, R.string.app_icon_logo_blue),
    White("White", R.drawable.ic_launcher_fg_white, R.string.app_icon_logo_white),
    Black("Black", R.drawable.ic_launcher_fg_black, R.string.app_icon_logo_black),
}

/**
 * One launcher icon: a background with a logo over it. Only pairs that read well together exist
 * (a white logo on a white background does not), so this catalog is both the list of icons and
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
            LauncherIcon(IconBackground.Black, IconMark.Color),
            LauncherIcon(IconBackground.Black, IconMark.White),
            LauncherIcon(IconBackground.White, IconMark.Blue),
            LauncherIcon(IconBackground.White, IconMark.Black),
            LauncherIcon(IconBackground.Charcoal, IconMark.Color),
            LauncherIcon(IconBackground.Charcoal, IconMark.White),
            LauncherIcon(IconBackground.Indigo, IconMark.Color),
            LauncherIcon(IconBackground.Indigo, IconMark.White),
            LauncherIcon(IconBackground.Violet, IconMark.Color),
            LauncherIcon(IconBackground.Violet, IconMark.White),
            LauncherIcon(IconBackground.Lagoon, IconMark.White),
            LauncherIcon(IconBackground.Lagoon, IconMark.Black),
            LauncherIcon(IconBackground.Azure, IconMark.Blue),
            LauncherIcon(IconBackground.Azure, IconMark.Black),
            LauncherIcon(IconBackground.Lilac, IconMark.Blue),
            LauncherIcon(IconBackground.Lilac, IconMark.Black),
            LauncherIcon(IconBackground.Mint, IconMark.Blue),
            LauncherIcon(IconBackground.Mint, IconMark.Black),
            LauncherIcon(IconBackground.Ember, IconMark.White),
            LauncherIcon(IconBackground.Ember, IconMark.Black),
        )

        /** The original xmd icon, and the alias that is enabled in the manifest. */
        val Default: LauncherIcon = of(IconBackground.Black, IconMark.Color)!!

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
