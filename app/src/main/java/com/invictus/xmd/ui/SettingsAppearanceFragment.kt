package com.invictus.xmd.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.invictus.xmd.R
import com.invictus.xmd.core.Settings
import com.invictus.xmd.ui.theme.AppTheme

/**
 * Theme color + dark mode. Picker/switch logic moved verbatim from
 * MainActivity.setupThemePicker()/toggleDarkMode() (old Settings dialog) --
 * same recreate()-on-change approach. recreate() here targets
 * SettingsActivity (this fragment's host), which now applies the theme
 * itself in onCreate() (like MainActivity/ChallengeActivity do) so the
 * recreate actually repaints this screen. MainActivity picks up the change
 * on its own next onResume (it compares the currently-applied theme style
 * against Settings and recreates itself if they've diverged), so backing
 * out of Settings repaints it immediately too, no app restart needed.
 */
class SettingsAppearanceFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings_appearance, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupThemePicker(view.findViewById(R.id.themeSwatchContainer))

        val darkModeSwitch = view.findViewById<MaterialSwitch>(R.id.darkModeSwitch)
        darkModeSwitch.isChecked = Settings.isDarkMode()
        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != Settings.isDarkMode()) toggleDarkMode()
        }
    }

    private fun toggleDarkMode() {
        val nowDark = !Settings.isDarkMode()
        Settings.setDarkMode(nowDark)
        Toast.makeText(
            requireContext(),
            if (nowDark) getString(R.string.theme_mode_dark) else getString(R.string.theme_mode_light),
            Toast.LENGTH_SHORT,
        ).show()
        requireActivity().recreate()
    }

    private fun setupThemePicker(container: LinearLayout) {
        container.removeAllViews()
        val current = Settings.appTheme()
        val density = resources.displayMetrics.density
        val dp8 = (8 * density).toInt()
        val inflater = LayoutInflater.from(requireContext())

        fun circleDrawable(colorHex: String) = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(colorHex))
        }

        fun roundRectDrawable(colorHex: String, radiusDp: Float, strokeColor: Int? = null, strokeWidthPx: Int = 0) =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusDp * density
                setColor(Color.parseColor(colorHex))
                if (strokeColor != null) setStroke(strokeWidthPx, strokeColor)
            }

        AppTheme.entries.forEach { theme ->
            val item = inflater.inflate(R.layout.item_theme_swatch, container, false)
            val ring = item.findViewById<FrameLayout>(R.id.swatchRing)
            val box = item.findViewById<FrameLayout>(R.id.swatchBox)
            val dotPrimary = item.findViewById<View>(R.id.dotPrimary)
            val dotSecondary = item.findViewById<View>(R.id.dotSecondary)
            val dotTertiary = item.findViewById<View>(R.id.dotTertiary)
            val checkIcon = item.findViewById<ImageView>(R.id.checkIcon)
            val nameView = item.findViewById<TextView>(R.id.themeName)

            val isSelected = theme == current
            val ringStrokePx = (2 * density).toInt()
            ring.background = roundRectDrawable(
                colorHex = "#00000000",
                radiusDp = 16f,
                strokeColor = if (isSelected) Color.parseColor(theme.swatchPrimary) else Color.TRANSPARENT,
                strokeWidthPx = ringStrokePx,
            )
            box.background = roundRectDrawable(theme.swatchBackground, 13f)
            dotPrimary.background = circleDrawable(theme.swatchPrimary)
            dotSecondary.background = circleDrawable(theme.swatchSecondary)
            dotTertiary.background = circleDrawable(theme.swatchTertiary)
            checkIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
            checkIcon.setColorFilter(Color.parseColor(theme.swatchPrimary))

            nameView.text = getString(theme.titleRes)
            nameView.setTextColor(MaterialColors.getColor(nameView, com.google.android.material.R.attr.colorOnSurface))
            nameView.setTypeface(nameView.typeface, if (isSelected) Typeface.BOLD else Typeface.NORMAL)

            item.setOnClickListener {
                if (theme != Settings.appTheme()) {
                    Settings.setAppTheme(theme)
                    requireActivity().recreate()
                }
            }

            container.addView(item, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = dp8 })
        }
    }
}
