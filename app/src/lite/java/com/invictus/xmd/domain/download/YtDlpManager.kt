package com.invictus.xmd.domain.download

import android.content.Context
import java.io.File
import com.invictus.xmd.service.DownloadService
import com.invictus.xmd.ui.MainActivity

/**
 * "lite" flavor stub -- this build has no youtubedl-android dependency at
 * all (see app/build.gradle.kts), so there's nothing here to wrap. Kept
 * with the exact same public API as the "full" flavor's real
 * YtDlpManager.kt so MainActivity/DownloadService (which live in the
 * shared main/ source set, built into both flavors) compile against
 * either one without any flavor-specific branching of their own beyond
 * the BuildConfig.HAS_YOUTUBE_SUPPORT check that gates ever calling these.
 */
object YtDlpManager {

    const val AUDIO_ONLY_SELECTOR = "bestaudio/best"

    data class QualityOption(
        val label: String,
        val formatSelector: String,
        val isAudioOnly: Boolean,
        val height: Int? = null
    )

    enum class SponsorBlockMode { OFF, MARK, REMOVE }
    val SPONSORBLOCK_CATEGORIES = listOf("sponsor", "selfpromo", "interaction", "intro", "outro", "preview", "filler", "music_offtopic")
    val SUBTITLE_LANGUAGES = listOf(
        "en" to "English",
        "hi" to "Hindi",
        "es" to "Spanish",
        "fr" to "French",
        "ar" to "Arabic",
        "pt" to "Portuguese",
        "ja" to "Japanese",
        "ko" to "Korean",
        "de" to "German",
        "ru" to "Russian",
        "all" to "All",
    )
    data class PlaylistEntry(val id: String, val title: String, val url: String, val durationSeconds: Int?)
    data class PlaylistProbeResult(val playlistTitle: String?, val entries: List<PlaylistEntry>)

    fun standardQualityOptions(isGenericOrHls: Boolean = false): List<QualityOption> = emptyList()

    fun qualityOptionsFromProbedFormats(
        probedFormats: List<ProbedFormat>,
        isGenericOrHls: Boolean = false
    ): List<QualityOption> = emptyList()

    /** One raw stream as reported by the real format probe -- see the full flavor's YtDlpManager.kt for field meanings. */
    data class ProbedFormat(
        val formatId: String,
        val ext: String,
        val height: Int?,
        val fps: Int?,
        val vcodec: String?,
        val acodec: String?,
        val sizeBytes: Long?,
        val tbr: Double?
    ) {
        val isVideoOnly: Boolean get() = acodec == null || acodec == "none"
        val isAudioOnly: Boolean get() = vcodec == null || vcodec == "none"
    }

    data class ProbeResult(
        val title: String?,
        val formats: List<ProbedFormat>,
        val durationSeconds: Int?
    )

    /** Always empty in this flavor -- gated behind BuildConfig.HAS_YOUTUBE_SUPPORT at the call site, same as everything else here. */
    fun probeFormats(url: String, context: Context): ProbeResult = ProbeResult(null, emptyList(), null)

    fun videoSelectorFor(
        maxHeight: Int,
        isGenericOrHls: Boolean,
        codecPrefix: String?,
        maxFps: Int?,
    ): String = ""

    fun advancedSelector(format: ProbedFormat): String = format.formatId

    fun formatSize(format: ProbedFormat, durationSeconds: Int?): String? = null

    fun isInstalled(context: Context): Boolean = false

    fun install(context: Context): String? = "This build doesn't include YouTube support"

    fun delete(context: Context) {}

    fun ensureReady(context: Context): Boolean = false

    fun update(context: Context): String? = null

    fun switchChannel(context: Context, toNightly: Boolean): String? = null

    fun isReady(): Boolean = false

    data class DownloadProgress(
        val percent: Int,
        val statusText: String?
    )

    fun download(
        url: String,
        option: QualityOption,
        outputDir: File,
        processId: String,
        context: Context,
        customFileName: String? = null,
        sponsorBlockMode: SponsorBlockMode = SponsorBlockMode.OFF,
        sponsorBlockCategories: Set<String> = emptySet(),
        embedSubtitles: Boolean = false,
        subtitleLanguages: Set<String> = setOf("en"),
        onProgress: (DownloadProgress) -> Unit
    ): File = throw IllegalStateException("This build doesn't include YouTube support")

    fun probePlaylist(url: String, context: Context): PlaylistProbeResult = PlaylistProbeResult(null, emptyList())

    fun cancel(processId: String) {}
}
