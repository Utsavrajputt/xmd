package com.invictus.xmd.domain.browser

import android.content.Context
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.repository.ShortcutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Keeps the saved website shortcuts in step with the FMHY link wiki
 * (github.com/fmhy/edit, markdown pages under docs/).
 *
 * What a sync does:
 *  1. Asks GitHub for the newest commit SHA of fmhy/edit. Unless forced,
 *     nothing else happens when it matches the SHA from the last sync.
 *  2. Downloads the wiki pages and parses the `* [Name](url), [2](mirror) - note` lines.
 *  3. UPDATE: a saved shortcut whose domain FMHY no longer lists, but whose
 *     name matches exactly one FMHY entry, is pointed at that entry's current URL.
 *  4. Nothing is ever added: FMHY entries that aren't already saved are ignored.
 *
 * Android has no push channel from GitHub, so "when FMHY updates" is
 * implemented as a throttled check on app start (see [autoCheckIfDue]) plus
 * the manual Refresh button.
 */
object FmhySync {

    data class UiState(val running: Boolean = false, val message: String? = null)

    sealed interface Result {
        data class Done(val updated: Int) : Result
        object UpToDate : Result
        object Failed : Result
    }

    private const val REPO_SHA_URL = "https://api.github.com/repos/fmhy/edit/commits/main"
    private const val RAW_BASE = "https://raw.githubusercontent.com/fmhy/edit/main/docs/"
    private const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000
    
    /** FMHY docs pages: video, torrents, downloads/software + the rest. A missing page is skipped. */
    private val PAGES = listOf(
        "videopiracyguide", "torrentpiracyguide", "downloadpiracyguide",
        "audiopiracyguide", "gamingpiracyguide", "readingpiracyguide",
        "non-english", "video-tools", "img-tools", "devtools",
    )

    private data class Entry(
        val name: String,
        val primary: String,
        val urls: List<String>,
    ) {
        val hosts: Set<String> by lazy { urls.map { hostKey(it) }.filter { it.isNotEmpty() }.toSet() }
    }

    private val linkRegex = Regex("""\[([^\]]+)]\((https?://[^)\s]+)\)""")

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var initialized = false

    fun init(@Suppress("UNUSED_PARAMETER") context: Context) {
        initialized = true
    }

    /** App-start hook: at most one lightweight check every 6h, only if auto-sync is on. */
    fun autoCheckIfDue() {
        if (!initialized || !Settings.fmhyAutoSync()) return
        if (System.currentTimeMillis() - Settings.fmhyLastCheckMs() < CHECK_INTERVAL_MS) return
        scope.launch { sync(force = false) }
    }

    fun refreshNow(onResult: (Result) -> Unit) {
        scope.launch {
            val result = sync(force = true)
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    private suspend fun sync(force: Boolean): Result = mutex.withLock {
        _state.value = UiState(running = true)
        val result = try {
            runSync(force)
        } catch (_: Exception) {
            Result.Failed
        }
        _state.value = UiState(running = false)
        result
    }

    private suspend fun runSync(force: Boolean): Result {
        val sha = fetchLatestSha()
        if (!force && (sha == null || sha == Settings.fmhyLastSha())) {
            Settings.setFmhyLastCheckMs(System.currentTimeMillis())
            return if (sha == null) Result.Failed else Result.UpToDate
        }

        val entries = PAGES.flatMap { page -> fetchPage(page)?.let(::parse) ?: emptyList() }
            .distinctBy { it.primary }
        if (entries.isEmpty()) return Result.Failed

        val saved = ShortcutRepository.allShortcuts()

        // ── UPDATE moved domains ──────────────────────────────────────
        val byHost = HashMap<String, Entry>()
        entries.forEach { e -> e.hosts.forEach { h -> byHost.putIfAbsent(h, e) } }
        val byName = entries.groupBy { normName(it.name) }
        val updates = ArrayList<com.invictus.xmd.database.entities.Shortcut>()
        saved.forEach { s ->
            val hit = byHost[hostKey(s.url)]
            if (hit != null) return@forEach
            val key = normName(s.title)
            if (key.length < 3) return@forEach
            val match = byName[key]?.singleOrNull() ?: return@forEach
            // Only the URL changes; icon (faviconUrl / customIconPath) stays untouched.
            updates += s.copy(url = match.primary)
        }
        if (updates.isNotEmpty()) ShortcutRepository.updateAll(updates)

        val summary = "${updates.size} updated"
        Settings.setFmhySynced(sha, System.currentTimeMillis(), summary)
        return Result.Done(updated = updates.size)
    }

    // ── network ─────────────────────────────────────────────────────

    private fun fetchLatestSha(): String? = runCatching {
        val req = Request.Builder().url(REPO_SHA_URL)
            .header("Accept", "application/vnd.github.sha")
            .header("User-Agent", "xmd")
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) null else r.body?.string()?.trim()?.takeIf { it.length in 7..64 }
        }
    }.getOrNull()

    private fun fetchPage(name: String): String? = runCatching {
        val req = Request.Builder().url("$RAW_BASE$name.md").header("User-Agent", "xmd").build()
        client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    }.getOrNull()

    // ── parsing ─────────────────────────────────────────────────────

    private fun parse(markdown: String): List<Entry> = markdown.lineSequence().mapNotNull { line ->
        if (!line.startsWith("* ") && !line.startsWith("- ")) return@mapNotNull null
        val head = line.substring(2).substringBefore(" - ")
        val links = linkRegex.findAll(head).toList()
        val first = links.firstOrNull() ?: return@mapNotNull null
        val primary = first.groupValues[2]
        val mirrors = links.drop(1).filter { it.groupValues[1].trim().all(Char::isDigit) }
            .map { it.groupValues[2] }
        Entry(
            name = first.groupValues[1].trim(),
            primary = primary,
            urls = listOf(primary) + mirrors,
        )
    }.toList()

    private fun normName(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun hostKey(url: String): String =
        (runCatching { java.net.URI(url).host }.getOrNull() ?: "").lowercase().removePrefix("www.")
}
