package com.invictus.xmd.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.invictus.xmd.R
import com.invictus.xmd.core.Settings

/**
 * Browser settings: the global adblock toggle (see AdblockFilter/
 * Settings.adblockEnabled) and the website source-pack import/export
 * trigger (moved here from Downloads -- it's a browser bookmark/shortcut
 * list, not a download setting). Private DNS mode lives in its own
 * in-browser dialog (BrowserFragment's overflow menu) rather than here.
 */
class SettingsBrowserFragment : Fragment() {

    /** Implemented by [SettingsActivity]; import logic is host-owned since
     *  it needs an Activity-scoped lifecycleScope + dialog host. */
    interface Callbacks {
        fun startWebImportFlow()
        fun startWebExportFlow()
    }

    private var callbacks: Callbacks? = null

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        callbacks = context as? Callbacks
    }

    override fun onDetach() {
        super.onDetach()
        callbacks = null
    }

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

        view.findViewById<MaterialButton>(R.id.importWebsitesButton).setOnClickListener {
            callbacks?.startWebImportFlow()
        }

        view.findViewById<MaterialButton>(R.id.exportWebsitesButton).setOnClickListener {
            callbacks?.startWebExportFlow()
        }
    }
}
