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

/**
 * The signed-in GitHub account plus "Back up now" / "Restore": the user's settings, profile and
 * bookmarks/shortcuts are serialised to one JSON file stored in a private Gist of their own
 * GitHub account. The token lives encrypted (Keystore) in a prefs file that is itself never
 * part of a backup.
 */
object AccountManager {
    private const val PREFS = "xmd_account"
    private const val GIST_FILE = "xmd-backup.json"
    private const val GIST_DESCRIPTION = "XMD settings backup (managed by the XMD app)"
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
        return Account(user.login, user.name, user.avatarUrl).also { _account.value = it }
    }

    /** Signs out on this device only; the Gist (the backup) stays on GitHub. */
    fun signOut() {
        prefs.edit().clear().apply()
        _account.value = null
        _lastBackupMs.value = 0L
    }

    // ── Backup / restore ────────────────────────────────────────────────

    /** Uploads the selected parts; returns the backup time. */
    suspend fun backupNow(scope: BackupScope): Long = withContext(Dispatchers.IO) {
        val token = token() ?: throw AccountException("Please sign in first.")
        if (!scope.any) throw AccountException("Choose something to back up.")
        val now = System.currentTimeMillis()
        val content = buildSnapshot(scope, now).toString()

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
        prefs.edit().putLong("last_backup_ms", now).apply()
        _lastBackupMs.value = now
        now
    }

    /** Downloads the backup and applies the selected parts over what is on this device. */
    suspend fun restore(scope: BackupScope): RestoreSummary = withContext(Dispatchers.IO) {
        val token = token() ?: throw AccountException("Please sign in first.")
        if (!scope.any) throw AccountException("Choose something to restore.")
        val gistId = findGistId(token) ?: throw AccountException("No backup found in your GitHub account yet.")

        val (code, text) = GitHubAuth.call(GitHubAuth.apiRequest("https://api.github.com/gists/$gistId", token).get().build())
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
        applySnapshot(snapshot, scope)
    }

    private fun checkApi(code: Int) {
        when {
            code in 200..299 -> Unit
            code == 401 -> throw AccountException("GitHub rejected the sign-in. Sign out and sign in again.")
            code == 403 || code == 429 -> throw AccountException("GitHub is limiting requests. Try again in a minute.")
            else -> throw AccountException("GitHub error ($code)")
        }
    }

    /** The backup Gist's id: remembered, else found by its file name in the user's gists. */
    private fun findGistId(token: String): String? {
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
                if (gist.optJSONObject("files")?.has(GIST_FILE) == true) {
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
