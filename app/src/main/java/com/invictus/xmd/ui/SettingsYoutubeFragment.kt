package com.invictus.xmd.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.invictus.xmd.BuildConfig
import com.invictus.xmd.R
import com.invictus.xmd.core.Settings
import com.invictus.xmd.core.YtDlpManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Default download quality, video preset ladder (container/fps/codec),
 * audio format, and the yt-dlp engine install/update/nightly-channel
 * controls. All logic here is moved verbatim from the old Settings dialog
 * (showSettingsDialog()'s BuildConfig.HAS_YOUTUBE_SUPPORT branch) -- same
 * dropdown bind/read-back pattern, same toast copy, same refreshYtDlpRow()
 * state machine. Only the container changed (dialog section -> full screen)
 * and quality/preset saving moved from the old shared Save button to this
 * screen's own Save button.
 */
class SettingsYoutubeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings_youtube, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rootColumn = view.findViewById<LinearLayout>(R.id.ytdlpRootColumn)

        if (!BuildConfig.HAS_YOUTUBE_SUPPORT) {
            // Lite build has no YtDlpManager to back this screen with --
            // hide everything below the hint text rather than show controls
            // that can't do anything. Matches the old dialog's approach of
            // hiding the divider + ytdlp section, just applied to the whole
            // screen since there's nothing else on it in the lite build.
            for (i in 1 until rootColumn.childCount) {
                rootColumn.getChildAt(i).visibility = View.GONE
            }
            return
        }

        val defaultQualityDropdown = view.findViewById<AutoCompleteTextView>(R.id.defaultQualityDropdown)
        val presetContainerDropdown = view.findViewById<AutoCompleteTextView>(R.id.presetContainerDropdown)
        val presetFpsDropdown = view.findViewById<AutoCompleteTextView>(R.id.presetFpsDropdown)
        val presetCodecDropdown = view.findViewById<AutoCompleteTextView>(R.id.presetCodecDropdown)
        val audioFormatDropdown = view.findViewById<AutoCompleteTextView>(R.id.audioFormatDropdown)
        val ytdlpStatus = view.findViewById<TextView>(R.id.ytdlpStatus)
        val ytdlpProgress = view.findViewById<ProgressBar>(R.id.ytdlpProgress)
        val ytdlpButton = view.findViewById<Button>(R.id.ytdlpActionButton)
        val ytdlpUpdateButton = view.findViewById<Button>(R.id.ytdlpUpdateButton)
        val ytdlpNightlyButton = view.findViewById<Button>(R.id.ytdlpNightlyButton)

        // Video preset (container/fps/codec) + audio format dropdowns --
        // each a fixed label<->enum pair list, same "pick by displayed
        // label, map back on Save" pattern as defaultQualityDropdown.
        val containerOptions = listOf(
            getString(R.string.preset_any) to Settings.ContainerPreset.ANY,
            getString(R.string.preset_container_mp4) to Settings.ContainerPreset.MP4,
            getString(R.string.preset_container_webm) to Settings.ContainerPreset.WEBM
        )
        val fpsOptions = listOf(
            getString(R.string.preset_any) to Settings.FpsPreset.ANY,
            getString(R.string.preset_fps_30) to Settings.FpsPreset.FPS30,
            getString(R.string.preset_fps_60) to Settings.FpsPreset.FPS60
        )
        val codecOptions = listOf(
            getString(R.string.preset_any) to Settings.CodecPreset.ANY,
            getString(R.string.preset_codec_avc) to Settings.CodecPreset.AVC,
            getString(R.string.preset_codec_vp9) to Settings.CodecPreset.VP9,
            getString(R.string.preset_codec_av1) to Settings.CodecPreset.AV1
        )
        val audioFormatOptions = listOf(
            getString(R.string.audio_format_mp3) to Settings.AudioFormatPreset.MP3,
            getString(R.string.audio_format_m4a) to Settings.AudioFormatPreset.M4A,
            getString(R.string.audio_format_opus) to Settings.AudioFormatPreset.OPUS,
            getString(R.string.audio_format_original) to Settings.AudioFormatPreset.ORIGINAL
        )

        fun <T> AutoCompleteTextView.bindPresetDropdown(options: List<Pair<String, T>>, current: T) {
            setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, options.map { it.first }))
            setText(options.first { it.second == current }.first, false)
        }
        fun <T> AutoCompleteTextView.selectedPreset(options: List<Pair<String, T>>): T =
            options.firstOrNull { it.first == text?.toString() }?.second ?: options.first().second

        // "Ask always" (blank stored value) first, then one entry per
        // standardQualityOptions() label, same order as the picker dialog
        // itself so the two stay visually consistent.
        val qualityLabels = listOf(getString(R.string.quality_ask_always)) +
            YtDlpManager.standardQualityOptions().map { it.label }
        defaultQualityDropdown.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, qualityLabels)
        )
        val savedLabel = Settings.ytDlpDefaultQualityLabel()
        val displayLabel = when {
            savedLabel.isBlank() -> getString(R.string.quality_ask_always)
            qualityLabels.contains(savedLabel) -> savedLabel
            // Saved before the audio format preset changed (see the
            // matching resolveYoutube() fallback) -- show the current
            // audio-only label instead of a stale "(MP3)" that's no
            // longer in the list.
            savedLabel.startsWith("Audio only") ->
                qualityLabels.firstOrNull { it.startsWith("Audio only") } ?: savedLabel
            else -> savedLabel
        }
        defaultQualityDropdown.setText(displayLabel, false)

        presetContainerDropdown.bindPresetDropdown(containerOptions, Settings.presetContainer())
        presetFpsDropdown.bindPresetDropdown(fpsOptions, Settings.presetFps())
        presetCodecDropdown.bindPresetDropdown(codecOptions, Settings.presetCodec())
        audioFormatDropdown.bindPresetDropdown(audioFormatOptions, Settings.presetAudioFormat())

        fun refreshYtDlpRow() {
            val installed = YtDlpManager.isInstalled(requireContext())
            ytdlpStatus.text = if (installed) {
                val channel = getString(
                    if (Settings.ytDlpUseNightly()) R.string.settings_ytdlp_channel_nightly
                    else R.string.settings_ytdlp_channel_stable
                )
                "${getString(R.string.settings_ytdlp_status_installed)}  •  $channel"
            } else {
                getString(R.string.settings_ytdlp_status_not_installed)
            }
            ytdlpButton.setText(if (installed) R.string.settings_ytdlp_delete else R.string.settings_ytdlp_install)
            ytdlpButton.isEnabled = true
            ytdlpUpdateButton.visibility = if (installed) View.VISIBLE else View.GONE
            ytdlpUpdateButton.isEnabled = true
            ytdlpUpdateButton.setText(R.string.settings_ytdlp_update)
            ytdlpNightlyButton.visibility = if (installed) View.VISIBLE else View.GONE
            ytdlpNightlyButton.isEnabled = true
            // Button always offers switching to the *other* channel -- once
            // on nightly, it becomes "back to stable" instead of staying
            // labeled "Use Nightly Build" forever.
            ytdlpNightlyButton.setText(
                if (Settings.ytDlpUseNightly()) R.string.settings_ytdlp_switch_stable
                else R.string.settings_ytdlp_use_nightly
            )
            ytdlpProgress.visibility = View.GONE
        }
        refreshYtDlpRow()

        ytdlpButton.setOnClickListener {
            if (YtDlpManager.isInstalled(requireContext())) {
                YtDlpManager.delete(requireContext())
                Toast.makeText(requireContext(), "yt-dlp removed", Toast.LENGTH_SHORT).show()
                refreshYtDlpRow()
            } else {
                ytdlpButton.isEnabled = false
                ytdlpProgress.visibility = View.VISIBLE
                ytdlpStatus.setText(R.string.settings_ytdlp_installing)
                lifecycleScope.launch {
                    val error = withContext(Dispatchers.IO) { YtDlpManager.install(requireContext()) }
                    // Show the exact failure reason instead of a generic
                    // message -- install() only unpacks bundled assets, no
                    // network involved, so a guessed "check your
                    // connection" message would usually be wrong.
                    Toast.makeText(
                        requireContext(),
                        error?.let { "Install failed: $it" } ?: "yt-dlp installed",
                        Toast.LENGTH_LONG
                    ).show()
                    refreshYtDlpRow()
                }
            }
        }

        ytdlpUpdateButton.setOnClickListener {
            ytdlpUpdateButton.isEnabled = false
            ytdlpNightlyButton.isEnabled = false
            ytdlpProgress.visibility = View.VISIBLE
            ytdlpStatus.setText(R.string.settings_ytdlp_updating)
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { YtDlpManager.update(requireContext()) }
                Toast.makeText(
                    requireContext(),
                    result?.let { "yt-dlp: $it" } ?: "Update failed — check your connection",
                    Toast.LENGTH_LONG
                ).show()
                refreshYtDlpRow()
            }
        }

        ytdlpNightlyButton.setOnClickListener {
            val switchingToNightly = !Settings.ytDlpUseNightly()
            ytdlpUpdateButton.isEnabled = false
            ytdlpNightlyButton.isEnabled = false
            ytdlpProgress.visibility = View.VISIBLE
            ytdlpStatus.setText(
                if (switchingToNightly) R.string.settings_ytdlp_switching_nightly
                else R.string.settings_ytdlp_updating
            )
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    YtDlpManager.switchChannel(requireContext(), switchingToNightly)
                }
                Toast.makeText(
                    requireContext(),
                    result?.let { "yt-dlp: $it" } ?: "Switch failed — check your connection",
                    Toast.LENGTH_LONG
                ).show()
                refreshYtDlpRow()
            }
        }

        view.findViewById<MaterialButton>(R.id.youtubeSaveButton).setOnClickListener {
            val chosenLabel = defaultQualityDropdown.text?.toString().orEmpty()
            val askAlways = getString(R.string.quality_ask_always)
            Settings.setYtDlpDefaultQualityLabel(if (chosenLabel == askAlways) "" else chosenLabel)
            Settings.setPresetContainer(presetContainerDropdown.selectedPreset(containerOptions))
            Settings.setPresetFps(presetFpsDropdown.selectedPreset(fpsOptions))
            Settings.setPresetCodec(presetCodecDropdown.selectedPreset(codecOptions))
            Settings.setPresetAudioFormat(audioFormatDropdown.selectedPreset(audioFormatOptions))
            Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show()
        }
    }
}
