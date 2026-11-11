package com.invictus.xmd.domain.account

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.invictus.xmd.R
import com.invictus.xmd.ui.ShareReceiverActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

/**
 * "Send to phone": links travel between a user's devices through the same private Gist the backup
 * uses. Every device owns exactly one file there (`xmd-device-<id>.json`: its name plus an outbox of
 * links it sent), so two phones never write the same file and nothing can conflict. A device reads
 * the other devices' files and picks out the items addressed to it (or to "all").
 */
object GistSync {
    const val DEVICE_FILE_PREFIX = "xmd-device-"
    const val TARGET_ALL = "all"
    const val CHANNEL_ID = "xmd_inbox"

    private const val PREFS = "xmd_sync" // survives sign-out, so a device keeps its id
    private const val ITEM_TTL_MS = 7L * 24 * 60 * 60 * 1000
    private const val DEVICE_TTL_MS = 90L * 24 * 60 * 60 * 1000
    private const val MAX_URL = 4000

    data class Device(val id: String, val name: String, val updatedAtMs: Long)
    data class Incoming(val id: String, val url: String, val fromName: String, val atMs: Long)
    private data class OutItem(val id: String, val url: String, val to: String, val atMs: Long)

    private lateinit var appContext: Context
    private val prefs get() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    /** Other devices that have registered in the Gist (this one excluded). */
    val devices: StateFlow<List<Device>> = _devices

    private val _received = MutableStateFlow<List<Incoming>>(emptyList())
    /** Most recent links received from other devices, newest first. */
    val received: StateFlow<List<Incoming>> = _received

    fun init(context: Context) {
        appContext = context.applicationContext
        _received.value = loadReceived()
    }

    // ── This device ─────────────────────────────────────────────────────

    fun deviceId(): String = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().replace("-", "").take(12)
        .also { prefs.edit().putString("device_id", it).apply() }

    fun deviceName(context: Context = appContext): String {
        prefsOf(context).getString("device_name", null)?.let { return it }
        val model = Build.MODEL.orEmpty().trim()
        val maker = Build.MANUFACTURER.orEmpty().trim().replaceFirstChar { it.uppercase() }
        return when {
            model.isEmpty() -> "Android"
            model.startsWith(maker, ignoreCase = true) -> model
            else -> "$maker $model"
        }
    }

    private fun prefsOf(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun ownFileName() = "$DEVICE_FILE_PREFIX${deviceId()}.json"

    // ── Sending ─────────────────────────────────────────────────────────

    /** Accepts web and magnet links only. */
    fun isSendable(text: String): Boolean {
        val t = text.trim()
        return t.length <= MAX_URL && (
            t.startsWith("http://", true) || t.startsWith("https://", true) || t.startsWith("magnet:", true)
            )
    }

    /** Queues [url] for [toDeviceId] ([TARGET_ALL] for every other device) and publishes this device's file. */
    suspend fun send(url: String, toDeviceId: String) = withContext(Dispatchers.IO) {
        val link = url.trim()
        if (!isSendable(link)) throw AccountException("Only web and magnet links can be sent.")
        val token = AccountManager.requireToken()
        val now = System.currentTimeMillis()
        val items = loadOutbox().filter { now - it.atMs < ITEM_TTL_MS } +
            OutItem(UUID.randomUUID().toString().take(12), link, toDeviceId, now)
        saveOutbox(items)
        publishOwnFile(token)
    }

    /** Makes sure this device shows up in other devices' lists (refreshes name/last seen at most daily). */
    suspend fun registerDevice(force: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong("registered_ms", 0L) < 24L * 60 * 60 * 1000) return@withContext
        publishOwnFile(AccountManager.requireToken())
    }

    private fun publishOwnFile(token: String, retried: Boolean = false) {
        val now = System.currentTimeMillis()
        val content = JSONObject()
            .put("id", deviceId())
            .put("name", deviceName())
            .put("updatedAtMs", now)
            .put(
                "outbox",
                JSONArray().also { arr ->
                    loadOutbox().filter { now - it.atMs < ITEM_TTL_MS }.forEach {
                        arr.put(JSONObject().put("id", it.id).put("url", it.url).put("to", it.to).put("atMs", it.atMs))
                    }
                },
            ).toString()
        val files = JSONObject().put(ownFileName(), JSONObject().put("content", content))
        val json = "application/json".toMediaType()
        val gistId = AccountManager.findGistId(token)
        val request = if (gistId == null) {
            GitHubAuth.apiRequest("https://api.github.com/gists", token).post(
                JSONObject().put("description", AccountManager.GIST_DESCRIPTION).put("public", false).put("files", files)
                    .toString().toRequestBody(json),
            )
        } else {
            GitHubAuth.apiRequest("https://api.github.com/gists/$gistId", token)
                .patch(JSONObject().put("files", files).toString().toRequestBody(json))
        }
        val (code, text) = GitHubAuth.call(request.build())
        if (code == 404 && gistId != null && !retried) {
            // The remembered Gist was deleted on GitHub: forget it and create a fresh one.
            AccountManager.forgetGistId()
            publishOwnFile(token, retried = true)
            return
        }
        AccountManager.checkApi(code)
        if (gistId == null) AccountManager.rememberGistId(JSONObject(text).getString("id"))
        prefs.edit().putLong("registered_ms", now).apply()
    }

    // ── Receiving ───────────────────────────────────────────────────────

    /**
     * Reads the other devices' files, refreshes [devices] and returns links newly addressed to this
     * device (each also raised as a notification when [notify] is set). [force] skips the ETag
     * shortcut the background poll uses, so the UI always gets a fresh device list.
     */
    suspend fun poll(force: Boolean = false, notify: Boolean = true): List<Incoming> = withContext(Dispatchers.IO) {
        val token = AccountManager.requireToken()
        val gistId = AccountManager.findGistId(token) ?: return@withContext emptyList()

        val builder = GitHubAuth.apiRequest("https://api.github.com/gists/$gistId", token)
        if (!force) prefs.getString("inbox_etag", null)?.let { builder.header("If-None-Match", it) }
        val (code, etag, text) = try {
            GitHubAuth.http.newCall(builder.get().build()).execute().use {
                Triple(it.code, it.header("ETag"), it.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            throw AccountException("No connection to GitHub.")
        }
        if (code == 304) return@withContext emptyList()
        AccountManager.checkApi(code)

        val files = JSONObject(text).optJSONObject("files") ?: return@withContext emptyList()
        val me = deviceId()
        val now = System.currentTimeMillis()
        val handled = loadHandled()
        val seeded = prefs.getBoolean("inbox_seeded", false)
        val devices = mutableListOf<Device>()
        val fresh = mutableListOf<Incoming>()

        for (fileName in files.keys()) {
            if (!fileName.startsWith(DEVICE_FILE_PREFIX)) continue
            val file = files.optJSONObject(fileName) ?: continue
            if (file.optBoolean("truncated", false)) continue
            val dev = runCatching { JSONObject(file.getString("content")) }.getOrNull() ?: continue
            val id = dev.optString("id")
            if (id.isEmpty() || id == me) continue
            val name = dev.optString("name").ifBlank { "Android" }
            val updated = dev.optLong("updatedAtMs", 0L)
            if (now - updated < DEVICE_TTL_MS) devices += Device(id, name, updated)

            val outbox = dev.optJSONArray("outbox") ?: continue
            for (i in 0 until outbox.length()) {
                val item = outbox.optJSONObject(i) ?: continue
                val to = item.optString("to")
                val itemId = item.optString("id")
                val url = item.optString("url")
                val at = item.optLong("atMs", 0L)
                if (itemId.isEmpty() || (to != me && to != TARGET_ALL)) continue
                if (now - at > ITEM_TTL_MS || !isSendable(url)) continue
                val key = "$id:$itemId"
                if (key in handled) continue
                handled += key
                fresh += Incoming(key, url.trim(), name, at)
            }
        }
        _devices.value = devices.sortedBy { it.name.lowercase() }
        saveHandled(handled)
        if (etag != null) prefs.edit().putString("inbox_etag", etag).apply()

        if (!seeded) {
            // First run on this device: whatever is already in the Gist is old news, not a new link.
            prefs.edit().putBoolean("inbox_seeded", true).apply()
            return@withContext emptyList()
        }
        val sorted = fresh.sortedBy { it.atMs }
        if (sorted.isNotEmpty()) {
            _received.value = (sorted.reversed() + _received.value).take(20).also(::saveReceived)
            if (notify) sorted.forEach { notifyIncoming(it) }
        }
        sorted
    }

    private fun notifyIncoming(item: Incoming) {
        val ctx = appContext
        val manager = NotificationManagerCompat.from(ctx)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, ctx.getString(R.string.inbox_channel_name), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val pending = PendingIntent.getActivity(
            ctx,
            item.id.hashCode(),
            openIntent(ctx, item.url),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(ctx.getString(R.string.inbox_notification_title, item.fromName))
            .setContentText(item.url)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.url))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(item.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Notification permission revoked meanwhile; the link is still in the received list.
        }
    }

    /** The intent that opens the normal "add download" dialog for [url]. */
    fun openIntent(context: Context, url: String): Intent =
        Intent(context, ShareReceiverActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, url)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Called on sign-out so the next account starts with a clean local picture. */
    fun clearLocalState() {
        prefs.edit()
            .remove("inbox_etag").remove("inbox_seeded").remove("handled").remove("received").remove("outbox").remove("registered_ms")
            .apply()
        _devices.value = emptyList()
        _received.value = emptyList()
    }

    // ── Local persistence ───────────────────────────────────────────────

    private fun loadOutbox(): List<OutItem> = runCatching {
        val arr = JSONArray(prefs.getString("outbox", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            OutItem(o.getString("id"), o.getString("url"), o.getString("to"), o.getLong("atMs"))
        }
    }.getOrDefault(emptyList())

    private fun saveOutbox(items: List<OutItem>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("id", it.id).put("url", it.url).put("to", it.to).put("atMs", it.atMs)) }
        prefs.edit().putString("outbox", arr.toString()).apply()
    }

    private fun loadHandled(): MutableSet<String> = runCatching {
        val arr = JSONArray(prefs.getString("handled", "[]"))
        (0 until arr.length()).mapTo(LinkedHashSet()) { arr.getString(it) }
    }.getOrDefault(LinkedHashSet())

    private fun saveHandled(set: Set<String>) {
        // Newest ids sit last in the insertion-ordered set; keep the tail.
        prefs.edit().putString("handled", JSONArray(set.toList().takeLast(300)).toString()).apply()
    }

    private fun loadReceived(): List<Incoming> = runCatching {
        val arr = JSONArray(prefs.getString("received", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Incoming(o.getString("id"), o.getString("url"), o.getString("from"), o.getLong("atMs"))
        }
    }.getOrDefault(emptyList())

    private fun saveReceived(items: List<Incoming>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("id", it.id).put("url", it.url).put("from", it.fromName).put("atMs", it.atMs)) }
        prefs.edit().putString("received", arr.toString()).apply()
    }
}
