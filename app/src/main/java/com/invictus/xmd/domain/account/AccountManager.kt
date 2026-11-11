package com.invictus.xmd.domain.account

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.invictus.xmd.database.entities.Bookmark
import com.invictus.xmd.database.entities.Shortcut
import com.invictus.xmd.preferences.Profile
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.repository.BookmarkRepository
import com.invictus.xmd.repository.ShortcutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/** What a backup covers; each part can be switched off in the Profile screen. */
data class BackupScope(
    val settings: Boolean = true,
    val profile: Boolean = true,
    val bookmarks: Boolean = true,
) {
    val any: Boolean get() = settings || profile || bookmarks
}

data class Account(val login: String, val name: String?, val avatarUrl: String?)

data class RestoreSummary(val settings: Int, val profile: Boolean, val bookmarks: Int, val shortcuts: Int)

/** One entry of the backup history: a past state of the backup file in the Gist. */
data class BackupRevision(
    val sha: String,
    /** When the backup was taken (from the file itself), not when GitHub stored the commit. */
    val backupAtMs: Long,
    val deviceName: String?,
)

/**
 * The signed-in GitHub account plus "Back up now" / "Restore": the user's settings, profile and
 * bookmarks/shortcuts are serialised to one JSON file stored in a private Gist of their own
 * GitHub account. The token lives encrypted (Keystore) in a prefs file that is itself never
 * part of a backup.
 */
object AccountManager {
    private const val PREFS = "xmd_account"
    internal const val GIST_FILE = "xmd-backup.json"
    internal const val GIST_DESCRIPTION = "XMD settings backup (managed by the XMD app)"
    private const val FORMAT_VERSION = 1

    private lateinit var appContext: Context
    private val prefs get() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account

    private val _lastBackupMs = MutableStateFlow(0L)
    val lastBackupMs: StateFlow<Long> = _lastBackupMs

    fun init(context: Context) {
        appContext = context.applicationContext
        val login = prefs.getString("login", null)
        if (login != null && token() != null) {
            _account.value = Account(login, prefs.getString("name", null), prefs.getString("avatar_url", null))
        }
        _lastBackupMs.value = prefs.getLong("last_backup_ms", 0L)
    }

    private fun token(): String? = prefs.getString("token_enc", null)?.let { TokenCipher.decrypt(it) }

    /** The decrypted token, or a friendly error when nobody is signed in. For [GistSync]. */
    internal fun requireToken(): String = token() ?: throw AccountException("Please sign in first.")

    // ── Auto-backup / inbox switches (remembered; cleared on sign-out) ──

    fun autoBackupEnabled(): Boolean = prefs.getBoolean("auto_backup", false)

    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_backup", enabled).apply()
        SyncScheduler.refresh(appContext)
    }

    fun inboxEnabled(): Boolean = prefs.getBoolean("inbox_enabled", true)

    fun setInboxEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("inbox_enabled", enabled).apply()
        SyncScheduler.refresh(appContext)
    }

    // ── What to include (remembered) ────────────────────────────────────

    fun backupScope(): BackupScope = BackupScope(
        settings = prefs.getBoolean("include_settings", true),
        profile = prefs.getBoolean("include_profile", true),
        bookmarks = prefs.getBoolean("include_bookmarks", true),
    )

    fun setBackupScope(scope: BackupScope) {
        prefs.edit()
            .putBoolean("include_settings", scope.settings)
            .putBoolean("include_profile", scope.profile)
            .putBoolean("include_bookmarks", scope.bookmarks)
            .apply()
    }

    // ── Sign in / out ───────────────────────────────────────────────────

    /** Stores [token] after the device flow succeeds and returns the account it belongs to. */
    suspend fun completeSignIn(token: String): Account {
        val user = GitHubAuth.fetchUser(token)
        prefs.edit()
            .putString("token_enc", TokenCipher.encrypt(token))
            .putString("login", user.login)
            .putString("name", user.name)
            .putString("avatar_url", user.avatarUrl)
            .remove("gist_id")
            .apply()
        return Account(user.login, user.name, user.avatarUrl).also {
            _account.value = it
            SyncScheduler.refresh(appContext)
        }
    }

    /** Signs out on this device only; the Gist (the backup) stays on GitHub. */
    fun signOut() {
        prefs.edit().clear().apply()
        GistSync.clearLocalState()
        _account.value = null
        _lastBackupMs.value = 0L
        SyncScheduler.refresh(appContext)
    }

    // ── Backup / restore ────────────────────────────────────────────────

    /**
     * Uploads the selected parts; returns the backup time. With [auto] set, nothing is uploaded
     * when the content is identical to the last backup, so background runs do not fill the history.
     */
    suspend fun backupNow(scope: BackupScope, auto: Boolean = false): Long = withContext(Dispatchers.IO) {
        val token = token() ?: throw AccountException("Please sign in first.")
        if (!scope.any) throw AccountException("Choose something to back up.")
        val now = System.currentTimeMillis()
        val snapshot = buildSnapshot(scope, now)
        val hash = contentHash(snapshot)
        if (auto && hash == prefs.getString("last_backup_hash", null)) {
            return@withContext prefs.getLong("last_backup_ms", now)
        }
        val content = snapshot.toString()

        val files = JSONObject().put(GIST_FILE, JSONObject().put("content", content))
        val existing = findGistId(token)
        val request = if (existing == null) {
            GitHubAuth.apiRequest("https://api.github.com/gists", token)
                .post(
                    JSONObject().put("description", GIST_DESCRIPTION).put("public", false).put("files", files)
                        .toString().toRequestBody("application/json".toMediaType()),
                )
        } else {
            GitHubAuth.apiRequest("https://api.github.com/gists/$existing", token)
                .patch(JSONObject().put("files", files).toString().toRequestBody("application/json".toMediaType()))
        }
        val (code, text) = GitHubAuth.call(request.build())
        checkApi(code)
        if (existing == null) {
            prefs.edit().putString("gist_id", JSONObject(text).getString("id")).apply()
        }
        prefs.edit().putLong("last_backup_ms", now).putString("last_backup_hash", hash).apply()
        _lastBackupMs.value = now
        now
    }

    /** Hash of a snapshot ignoring its timestamp, to tell "nothing changed" from "new backup". */
    private fun contentHash(snapshot: JSONObject): String {
        val copy = JSONObject(snapshot.toString()).apply { remove("createdAtMs") }
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(copy.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /** Downloads the newest backup and applies the selected parts over what is on this device. */
    suspend fun restore(scope: BackupScope): RestoreSummary = withContext(Dispatchers.IO) {
        val token = token() ?: throw AccountException("Please sign in first.")
        if (!scope.any) throw AccountException("Choose something to restore.")
        val gistId = findGistId(token) ?: throw AccountException("No backup found in your GitHub account yet.")
        applySnapshot(readBackup(token, "https://api.github.com/gists/$gistId"), scope)
    }

    // ── Backup history (Gist revisions) ─────────────────────────────────

    /** Up to ~20 distinct past backups, newest first. Commits that only touched other files are skipped. */
    suspend fun backupHistory(): List<BackupRevision> = withContext(Dispatchers.IO) {
        val token = token() ?: throw AccountException("Please sign in first.")
        val gistId = findGistId(token) ?: return@withContext emptyList()
        val (code, text) = GitHubAuth.call(
            GitHubAuth.apiRequest("https://api.github.com/gists/$gistId/commits?per_page=30", token).get().build(),
        )
        checkApi(code)
        val commits = JSONArray(text)
        val shas = (0 until commits.length()).map { commits.getJSONObject(it).getString("version") }
        val gate = kotlinx.coroutines.sync.Semaphore(4)
        val loaded = kotlinx.coroutines.coroutineScope {
            shas.map { sha ->
                async {
                    gate.withPermit {
                        runCatching {
                            val snap = readBackup(token, "https://api.github.com/gists/$gistId/$sha")
                            BackupRevision(sha, snap.optLong("createdAtMs", 0L), snap.optString("deviceName").takeIf { it.isNotBlank() })
                        }.getOrNull()
                    }
                }
            }.awaitAll()
        }
        // Inbox/device-file commits leave the backup file unchanged: keep one row per distinct backup.
        loaded.filterNotNull().distinctBy { it.backupAtMs }.take(20)
    }

    /**
     * Restores [revision]. The current state of this device is uploaded first (a normal backup, so
     * it shows up in the history) and the restore can be undone from there.
     */
    suspend fun restoreRevision(revision: BackupRevision, scope: BackupScope): RestoreSummary {
        if (!scope.any) throw AccountException("Choose something to restore.")
        val token = requireToken()
        val gistId = withContext(Dispatchers.IO) { findGistId(token) } ?: throw AccountException("No backup found in your GitHub account yet.")
        val snapshot = withContext(Dispatchers.IO) { readBackup(token, "https://api.github.com/gists/$gistId/${revision.sha}") }
        backupNow(BackupScope())
        return withContext(Dispatchers.IO) { applySnapshot(snapshot, scope) }
    }

    /** Reads and validates the backup file of the gist at [gistUrl] (newest, or a revision url). */
    private fun readBackup(token: String, gistUrl: String): JSONObject {
        val (code, text) = GitHubAuth.call(GitHubAuth.apiRequest(gistUrl, token).get().build())
        checkApi(code)
        val file = JSONObject(text).optJSONObject("files")?.optJSONObject(GIST_FILE)
            ?: throw AccountException("No backup found in your GitHub account yet.")
        val content = if (file.optBoolean("truncated", false)) {
            val (c, body) = GitHubAuth.call(GitHubAuth.apiRequest(file.getString("raw_url"), token).get().build())
            checkApi(c)
            body
        } else {
            file.getString("content")
        }
        val snapshot = runCatching { JSONObject(content) }.getOrElse { throw AccountException("The backup file is damaged.") }
        if (snapshot.optInt("version", 0) > FORMAT_VERSION) {
            throw AccountException("This backup is from a newer version of XMD. Update the app first.")
        }
        return snapshot
    }

    internal fun checkApi(code: Int) {
        when {
            code in 200..299 -> Unit
            code == 401 -> throw AccountException("GitHub rejected the sign-in. Sign out and sign in again.")
            code == 403 || code == 429 -> throw AccountException("GitHub is limiting requests. Try again in a minute.")
            else -> throw AccountException("GitHub error ($code)")
        }
    }

    internal fun forgetGistId() {
        prefs.edit().remove("gist_id").apply()
    }

    internal fun rememberGistId(id: String) {
        prefs.edit().putString("gist_id", id).apply()
    }

    /** The XMD Gist's id: remembered, else found by its backup / device file name in the user's gists. */
    internal fun findGistId(token: String): String? {
        prefs.getString("gist_id", null)?.let { return it }
        for (page in 1..5) {
            val (code, text) = GitHubAuth.call(
                GitHubAuth.apiRequest("https://api.github.com/gists?per_page=100&page=$page", token).get().build(),
            )
            checkApi(code)
            val arr = JSONArray(text)
            if (arr.length() == 0) return null
            for (i in 0 until arr.length()) {
                val gist = arr.getJSONObject(i)
                val files = gist.optJSONObject("files")
                if (files != null && (files.has(GIST_FILE) || files.keys().asSequence().any { it.startsWith(GistSync.DEVICE_FILE_PREFIX) })) {
                    return gist.getString("id").also { prefs.edit().putString("gist_id", it).apply() }
                }
            }
            if (arr.length() < 100) return null
        }
        return null
    }

    // ── Snapshot (the JSON stored in the Gist) ──────────────────────────

    private suspend fun buildSnapshot(scope: BackupScope, now: Long): JSONObject {
        val root = JSONObject().put("app", "xmd").put("version", FORMAT_VERSION).put("createdAtMs", now)
            .put("deviceName", GistSync.deviceName(appContext))
        if (scope.settings) root.put("settings", Settings.exportPrefsForBackup())
        if (scope.profile) {
            val profile = JSONObject().put("name", Profile.name())
            avatarBase64()?.let { profile.put("avatarPng", it) }
            root.put("profile", profile)
        }
        if (scope.bookmarks) {
            root.put(
                "bookmarks",
                JSONArray().also { arr ->
                    BookmarkRepository.allBookmarks().forEach {
                        arr.put(
                            JSONObject().put("id", it.id).put("title", it.title).put("url", it.url)
                                .put("faviconUrl", it.faviconUrl ?: JSONObject.NULL).put("createdAtMs", it.createdAtMs),
                        )
                    }
                },
            )
            root.put(
                "shortcuts",
                JSONArray().also { arr ->
                    ShortcutRepository.allShortcuts().forEach {
                        // The custom icon is a file on this device, so it is not carried over.
                        arr.put(
                            JSONObject().put("id", it.id).put("title", it.title).put("url", it.url)
                                .put("faviconUrl", it.faviconUrl ?: JSONObject.NULL)
                                .put("sortOrder", it.sortOrder).put("createdAtMs", it.createdAtMs),
                        )
                    }
                },
            )
        }
        return root
    }

    private fun avatarBase64(): String? {
        val bitmap = Profile.loadAvatar(appContext) ?: return null
        val scaled = if (bitmap.width > 384) Bitmap.createScaledBitmap(bitmap, 384, 384, true) else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private suspend fun applySnapshot(snapshot: JSONObject, scope: BackupScope): RestoreSummary {
        var settingsCount = 0
        var profileDone = false
        var bookmarkCount = 0
        var shortcutCount = 0

        if (scope.settings) {
            snapshot.optJSONObject("settings")?.let { settingsCount = Settings.importPrefsFromBackup(it) }
        }
        if (scope.profile) {
            snapshot.optJSONObject("profile")?.let { p ->
                Profile.setName(p.optString("name"))
                val png = p.optString("avatarPng")
                if (png.isNotEmpty()) {
                    val bytes = Base64.decode(png, Base64.NO_WRAP)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { Profile.saveAvatarBitmap(appContext, it) }
                } else {
                    Profile.clearAvatar(appContext)
                }
                profileDone = true
            }
        }
        if (scope.bookmarks) {
            snapshot.optJSONArray("bookmarks")?.let { arr ->
                val list = (0 until arr.length()).map { i ->
                    arr.getJSONObject(i).let {
                        Bookmark(
                            id = it.getString("id"),
                            title = it.getString("title"),
                            url = it.getString("url"),
                            faviconUrl = it.optString("faviconUrl").takeIf { s -> s.isNotEmpty() && s != "null" },
                            createdAtMs = it.optLong("createdAtMs", System.currentTimeMillis()),
                        )
                    }
                }
                BookmarkRepository.replaceAll(list)
                bookmarkCount = list.size
            }
            snapshot.optJSONArray("shortcuts")?.let { arr ->
                val list = (0 until arr.length()).map { i ->
                    arr.getJSONObject(i).let {
                        Shortcut(
                            id = it.getString("id"),
                            title = it.getString("title"),
                            url = it.getString("url"),
                            faviconUrl = it.optString("faviconUrl").takeIf { s -> s.isNotEmpty() && s != "null" },
                            sortOrder = it.optInt("sortOrder", i),
                            createdAtMs = it.optLong("createdAtMs", System.currentTimeMillis()),
                            customIconPath = null,
                        )
                    }
                }
                ShortcutRepository.replaceAll(list)
                shortcutCount = list.size
            }
        }
        return RestoreSummary(settingsCount, profileDone, bookmarkCount, shortcutCount)
    }
}
