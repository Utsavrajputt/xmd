package com.invictus.xmd.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.invictus.xmd.R
import com.invictus.xmd.core.Settings

/**
 * Browser settings: currently just the global adblock toggle (see
 * AdblockFilter/Settings.adblockEnabled). Private DNS mode lives in its
 * own in-browser dialog (BrowserFragment's overflow menu) rather than
 * here -- this screen is for settings that belong in Settings, not a
 * duplicate home for that one.
 */
class SettingsBrowserFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings_browser, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adblockSwitch = view.findViewById<MaterialSwitch>(R.id.adblockSwitch)
        adblockSwitch.isChecked = Settings.adblockEnabled()
        adblockSwitch.setOnCheckedChangeListener { _, isChecked ->
            Settings.setAdblockEnabled(isChecked)
        }
    }
}
