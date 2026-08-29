package com.invictus.xmd.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.invictus.xmd.R
import com.invictus.xmd.core.Settings

/**
 * Parallel connections per download, global speed limit, max concurrent
 * downloads. Save logic moved verbatim from the old Settings dialog's
 * positive-button handler (connections + speed + concurrency portion only).
 */
class SettingsConnectionsFragment : Fragment() {

    private val idForConnections = mapOf(
        2 to R.id.conn2, 4 to R.id.conn4, 8 to R.id.conn8, 16 to R.id.conn16
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings_connections, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val group = view.findViewById<RadioGroup>(R.id.connectionsGroup)
        val speedInput = view.findViewById<EditText>(R.id.speedLimitInput)
        val concurrentInput = view.findViewById<EditText>(R.id.maxConcurrentInput)

        (view.findViewById<RadioButton>(
            idForConnections[Settings.connectionsPerDownload()] ?: R.id.conn4
        )).isChecked = true
        speedInput.setText(Settings.speedLimitKBps().toString())
        concurrentInput.setText(Settings.maxConcurrentDownloads().toString())

        view.findViewById<MaterialButton>(R.id.connectionsSaveButton).setOnClickListener {
            val checkedId = group.checkedRadioButtonId
            val connections = idForConnections.entries
                .firstOrNull { it.value == checkedId }?.key ?: 4
            Settings.setConnectionsPerDownload(connections)
            Settings.setSpeedLimitKBps(speedInput.text?.toString()?.toIntOrNull() ?: 0)
            Settings.setMaxConcurrentDownloads(concurrentInput.text?.toString()?.toIntOrNull() ?: 2)
            Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show()
        }
    }
}
