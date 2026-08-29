package com.invictus.xmd.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.invictus.xmd.BuildConfig
import com.invictus.xmd.R

/**
 * Root of the redesigned Settings screen: a list of category rows (each an
 * [R.layout.item_settings_category] include) that push the matching
 * sub-fragment via [SettingsActivity.openCategory].
 */
class SettingsRootFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings_root, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindRow(
            view.findViewById(R.id.rowAppearance),
            iconRes = R.drawable.ic_settings_appearance,
            titleRes = R.string.settings_category_appearance,
            subtitleRes = R.string.settings_category_appearance_desc,
        ) { open(SettingsAppearanceFragment(), "settings_appearance") }

        bindRow(
            view.findViewById(R.id.rowConnections),
            iconRes = R.drawable.ic_settings_connections,
            titleRes = R.string.settings_category_connections,
            subtitleRes = R.string.settings_category_connections_desc,
        ) { open(SettingsConnectionsFragment(), "settings_connections") }

        bindRow(
            view.findViewById(R.id.rowBrowser),
            iconRes = R.drawable.ic_settings_browser,
            titleRes = R.string.settings_category_browser,
            subtitleRes = R.string.settings_category_browser_desc,
        ) { open(SettingsBrowserFragment(), "settings_browser") }

        bindRow(
            view.findViewById(R.id.rowDownloads),
            iconRes = R.drawable.ic_settings_downloads,
            titleRes = R.string.settings_category_downloads,
            subtitleRes = R.string.settings_category_downloads_desc,
        ) { open(SettingsDownloadsFragment(), "settings_downloads") }

        val rowYoutube = view.findViewById<View>(R.id.rowYoutube)
        if (BuildConfig.HAS_YOUTUBE_SUPPORT) {
            bindRow(
                rowYoutube,
                iconRes = R.drawable.ic_settings_youtube,
                titleRes = R.string.settings_category_youtube,
                subtitleRes = R.string.settings_category_youtube_desc,
            ) { open(SettingsYoutubeFragment(), "settings_youtube") }
        } else {
            // Lite build has no yt-dlp engine behind this screen -- drop
            // the row entirely rather than show a category that leads
            // nowhere useful, same reasoning as the other
            // BuildConfig.HAS_YOUTUBE_SUPPORT gates in MainActivity /
            // AboutFragment / SettingsYoutubeFragment.
            rowYoutube.visibility = View.GONE
        }

        bindRow(
            view.findViewById(R.id.rowAbout),
            iconRes = R.drawable.ic_settings_about,
            titleRes = R.string.settings_category_about,
            subtitleRes = R.string.settings_category_about_desc,
        ) { open(AboutFragment(), "settings_about") }
    }

    private fun bindRow(
        row: View,
        iconRes: Int,
        titleRes: Int,
        subtitleRes: Int,
        onClick: () -> Unit,
    ) {
        row.findViewById<ImageView>(R.id.categoryIcon).setImageResource(iconRes)
        row.findViewById<TextView>(R.id.categoryTitle).setText(titleRes)
        row.findViewById<TextView>(R.id.categorySubtitle).setText(subtitleRes)
        row.setOnClickListener { onClick() }
    }

    private fun open(fragment: Fragment, tag: String) {
        (activity as? SettingsActivity)?.openCategory(fragment, tag)
    }
}
