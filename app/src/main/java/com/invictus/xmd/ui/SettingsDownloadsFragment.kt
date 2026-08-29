package com.invictus.xmd.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.invictus.xmd.R
import com.invictus.xmd.core.NetworkMonitor
import com.invictus.xmd.core.Settings
import com.invictus.xmd.service.DownloadService

/**
 * Auto-retry, save-to-Downloads, Wi-Fi-only, and the website source-pack
 * import trigger. Each switch persists immediately on change (no Save
 * button), including the wifi-only-just-enabled pause-in-flight-downloads
 * check, which now runs off the switch's own listener.
 */
class SettingsDownloadsFragment : Fragment() {

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
    ): View = inflater.inflate(R.layout.fragment_settings_downloads, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val autoRetrySwitch = view.findViewById<MaterialSwitch>(R.id.autoRetrySwitch)
        val saveToDownloadsSwitch = view.findViewById<MaterialSwitch>(R.id.saveToDownloadsSwitch)
        val wifiOnlySwitch = view.findViewById<MaterialSwitch>(R.id.wifiOnlySwitch)

        autoRetrySwitch.isChecked = Settings.autoRetryEnabled()
        saveToDownloadsSwitch.isChecked = Settings.saveToDownloadsFolder()
        wifiOnlySwitch.isChecked = Settings.wifiOnlyDownloads()

        autoRetrySwitch.setOnCheckedChangeListener { _, isChecked ->
            Settings.setAutoRetryEnabled(isChecked)
        }
        saveToDownloadsSwitch.setOnCheckedChangeListener { _, isChecked ->
            Settings.setSaveToDownloadsFolder(isChecked)
        }
        wifiOnlySwitch.setOnCheckedChangeListener { _, isChecked ->
            val wifiOnlyJustEnabled = isChecked && !Settings.wifiOnlyDownloads()
            Settings.setWifiOnlyDownloads(isChecked)
            if (wifiOnlyJustEnabled && !NetworkMonitor.isOnWifi(requireContext())) {
                // Turned ON while already on cellular -- the setting only
                // reacts to a live network *transition* otherwise, so
                // without this any download already in flight would keep
                // running on cellular until the next Wi-Fi drop/regain.
                DownloadService.pauseForWifiOnly(requireContext())
            }
        }

        view.findViewById<MaterialButton>(R.id.importWebsitesButton).setOnClickListener {
            callbacks?.startWebImportFlow()
        }

        view.findViewById<MaterialButton>(R.id.exportWebsitesButton).setOnClickListener {
            callbacks?.startWebExportFlow()
        }
    }
}
