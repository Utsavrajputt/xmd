package com.invictus.xmd.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.invictus.xmd.R
import com.invictus.xmd.core.ItemStatus
import com.invictus.xmd.core.MediaPlatform
import com.invictus.xmd.core.QueueItem
import com.invictus.xmd.core.Settings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QueueAdapter(
    private val onPauseResume: (QueueItem) -> Unit,
    private val onCancel: (QueueItem) -> Unit,
    private val onRetry: (QueueItem) -> Unit,
    private val onClear: (QueueItem) -> Unit,
    private val onOpen: (QueueItem) -> Unit,
    /** Long-press on a DONE/FAILED row -- Open with / Rename / Re-download /
     *  Copy link / Share / Delete, same idea as the Files app's per-download
     *  context menu. Not offered for still-in-flight items (nothing to
     *  rename/share/delete yet, and Re-download would race the live job). */
    private val onLongPress: (QueueItem) -> Unit
) : ListAdapter<QueueItem, QueueAdapter.VH>(DIFF) {

    private val dateFormat by lazy {
        SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
    }

    private fun formatElapsedDuration(durationMs: Long): String {
        val totalSecs = (durationMs / 1000).coerceAtLeast(1)
        val hours = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return when {
            hours > 0 -> "${hours}h ${mins}m"
            mins > 0 -> "${mins}m ${secs}s"
            else -> "${secs}s"
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val indicator: View      = view.findViewById(R.id.statusIndicator)
        val title: TextView      = view.findViewById(R.id.itemTitle)
        val category: TextView   = view.findViewById(R.id.itemCategory)
        val status: TextView     = view.findViewById(R.id.itemStatus)
        val sizeRow: View        = view.findViewById(R.id.itemSizeRow)
        val sizeText: TextView   = view.findViewById(R.id.itemSizeText)   // MB done / total MB
        val completedMeta: TextView = view.findViewById(R.id.itemCompletedMeta)
        val progress: ProgressBar= view.findViewById(R.id.itemProgress)
        val speedEta: TextView   = view.findViewById(R.id.itemSpeedEta)   // speed + ETA
        val actions: View        = view.findViewById(R.id.itemActions)
        val pauseResume: Button  = view.findViewById(R.id.itemPauseResume)
        val cancel: Button       = view.findViewById(R.id.itemCancel)
        val secondaryActions: View = view.findViewById(R.id.itemSecondaryActions)
        val retry: Button        = view.findViewById(R.id.itemRetry)
        val open: Button         = view.findViewById(R.id.itemOpen)
        val clear: Button        = view.findViewById(R.id.itemClear)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_queue, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        // Progress-only tick (bytes/speed changed but status/name/etc didn't) --
        // update just the live text/progress views and skip the full rebind so
        // RecyclerView's default change-animation (fade out/in) doesn't fire and
        // flicker the whole row on every update.
        if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_PROGRESS }) {
            bindProgressOnly(holder, getItem(position))
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context

        holder.title.text = item.fileName ?: item.sourceUrl
        holder.category.text = item.category.label
        holder.indicator.setBackgroundColor(colorForStatus(context, item.status))

        // ── Status text ───────────────────────────────────────────────────
        holder.status.text = when (item.status) {
            ItemStatus.PENDING          -> "⏳ Queued"
            ItemStatus.RESOLVING        -> "🔄 Resolving…"
            ItemStatus.NEEDS_CHALLENGE  -> "🛡 Verifying — complete check in browser"
            ItemStatus.READY            -> "✅ Ready to download"
            ItemStatus.DOWNLOADING      -> {
                if (item.platform == MediaPlatform.YOUTUBE) {
                    val label = item.mediaFormatLabel?.let { " • $it" }.orEmpty()
                    "⬇  ${if (item.progressPercent >= 0) "${item.progressPercent}%" else "Downloading…"}$label"
                } else {
                    val pct = if (item.bytesTotal > 0) (item.bytesDone * 100 / item.bytesTotal) else 0
                    "⬇  ${if (item.bytesTotal > 0) "$pct%" else "Downloading…"}"
                }
            }            ItemStatus.PAUSED           ->
                if (item.error == Settings.WIFI_WAIT_MARKER) "📶 Waiting for Wi-Fi" else "⏸  Paused"
            ItemStatus.RETRYING         -> "🔁 ${item.error ?: "Retrying…"}"
            ItemStatus.SAVING           -> "💾 Saving to storage…"
            ItemStatus.DONE             -> "✔  Done"
            ItemStatus.FAILED           -> "✖  ${item.error ?: "Failed"}"
        }

        // ── Size line + completed meta (time taken & date on right) ───────
        when (item.status) {
            ItemStatus.DOWNLOADING, ItemStatus.PAUSED, ItemStatus.SAVING, ItemStatus.RETRYING -> {
                holder.completedMeta.visibility = View.GONE
                when {
                    item.platform == MediaPlatform.YOUTUBE -> {
                        if (!item.mediaStatusText.isNullOrBlank()) {
                            holder.sizeText.text = item.mediaStatusText
                            holder.sizeText.visibility = View.VISIBLE
                        } else if (item.status == ItemStatus.DOWNLOADING) {
                            holder.sizeText.text = if (item.progressPercent >= 0) "${item.progressPercent}%" else "Connecting…"
                            holder.sizeText.visibility = View.VISIBLE
                        } else {
                            holder.sizeText.visibility = View.GONE
                        }
                    }
                    item.bytesTotal > 0 -> {
                        holder.sizeText.text =
                            "${formatBytes(item.bytesDone)} / ${formatBytes(item.bytesTotal)}"
                        holder.sizeText.visibility = View.VISIBLE
                    }
                    item.bytesDone > 0 -> {
                        holder.sizeText.text = formatBytes(item.bytesDone)
                        holder.sizeText.visibility = View.VISIBLE
                    }
                    else -> holder.sizeText.visibility = View.GONE
                }
                holder.sizeRow.visibility = holder.sizeText.visibility
            }
            ItemStatus.DONE -> {
                val bytes = when {
                    item.bytesTotal > 0 -> item.bytesTotal
                    item.bytesDone > 0 -> item.bytesDone
                    else -> item.filePath?.let { File(it).takeIf { f -> f.exists() }?.length() } ?: 0L
                }
                if (bytes > 0) {
                    holder.sizeText.text = formatBytes(bytes)
                    holder.sizeText.visibility = View.VISIBLE
                } else {
                    holder.sizeText.visibility = View.GONE
                }

                val finishedAt = when {
                    item.downloadFinishedAtMs > 0 -> item.downloadFinishedAtMs
                    else -> item.filePath?.let { File(it).takeIf { f -> f.exists() }?.lastModified() } ?: 0L
                }
                val durationMs = if (item.downloadStartedAtMs > 0 && finishedAt > item.downloadStartedAtMs) {
                    finishedAt - item.downloadStartedAtMs
                } else 0L

                val parts = mutableListOf<String>()
                if (durationMs > 500) {
                    parts.add("Took ${formatElapsedDuration(durationMs)}")
                }
                if (finishedAt > 0) {
                    parts.add(dateFormat.format(Date(finishedAt)))
                }
                if (parts.isNotEmpty()) {
                    holder.completedMeta.text = parts.joinToString("  •  ")
                    holder.completedMeta.visibility = View.VISIBLE
                } else {
                    holder.completedMeta.visibility = View.GONE
                }

                holder.sizeRow.visibility = if (bytes > 0 || parts.isNotEmpty()) View.VISIBLE else View.GONE
            }
            else -> {
                holder.sizeText.visibility = View.GONE
                holder.completedMeta.visibility = View.GONE
                holder.sizeRow.visibility = View.GONE
            }
        }

        // ── Progress bar ─────────────────────────────────────────────────
        when (item.status) {
            ItemStatus.DOWNLOADING -> {
                if (item.platform == MediaPlatform.YOUTUBE) {
                    if (item.progressPercent >= 0) {
                        holder.progress.isIndeterminate = false
                        holder.progress.progress = item.progressPercent
                    } else {
                        holder.progress.isIndeterminate = true
                    }
                    holder.progress.visibility = View.VISIBLE
                } else if (item.bytesTotal > 0) {
                    holder.progress.isIndeterminate = false
                    holder.progress.progress = ((item.bytesDone * 100) / item.bytesTotal).toInt()
                    holder.progress.visibility = View.VISIBLE
                } else {
                    holder.progress.isIndeterminate = true
                    holder.progress.visibility = View.VISIBLE
                }
            }
            ItemStatus.DONE -> {
                holder.progress.isIndeterminate = false
                holder.progress.progress = 100
                holder.progress.visibility = View.VISIBLE
            }
            ItemStatus.SAVING -> {
                holder.progress.isIndeterminate = false
                holder.progress.progress = 100
                holder.progress.visibility = View.VISIBLE
            }
            else -> {
                holder.progress.isIndeterminate = false
                holder.progress.visibility = View.GONE
            }
        }

        // ── Speed + ETA line ─────────────────────────────────────────────
        if (item.status == ItemStatus.DOWNLOADING && item.speedBps > 0) {
            holder.speedEta.text = buildSpeedEtaText(item)
            holder.speedEta.visibility = View.VISIBLE
        } else {
            holder.speedEta.visibility = View.GONE
        }

        // ── Action buttons ────────────────────────────────────────────────
        // PENDING/RESOLVING/NEEDS_CHALLENGE included so a queued-but-not-yet-
        // downloading item still gets a Cancel (previously only Retry/Open/
        // Clear states got any buttons at all, leaving Queued items with no
        // way to back out short of clearing the whole queue).
        val showActions = item.status == ItemStatus.DOWNLOADING || item.status == ItemStatus.PAUSED ||
            item.status == ItemStatus.RETRYING || item.status == ItemStatus.READY ||
            item.status == ItemStatus.PENDING || item.status == ItemStatus.RESOLVING ||
            item.status == ItemStatus.NEEDS_CHALLENGE
        holder.actions.visibility = if (showActions) View.VISIBLE else View.GONE
        // No live connection to pause during an auto-retry backoff wait -- only Cancel
        // applies. yt-dlp also has no native pause, so a downloading YouTube item only
        // gets Cancel too (READY still shows "Start", which works the same for both).
        // Queued/resolving/challenge items have nothing running yet either -- Pause
        // has no meaning there, only Cancel does.
        val hidePauseResume = item.status == ItemStatus.RETRYING ||
            item.status == ItemStatus.PENDING || item.status == ItemStatus.RESOLVING ||
            item.status == ItemStatus.NEEDS_CHALLENGE ||
            (item.platform == MediaPlatform.YOUTUBE && item.status == ItemStatus.DOWNLOADING)
        holder.pauseResume.visibility = if (hidePauseResume) View.GONE else View.VISIBLE
        holder.pauseResume.text = when (item.status) {
            ItemStatus.READY  -> context.getString(R.string.action_start)
            ItemStatus.PAUSED -> context.getString(R.string.action_resume)
            else              -> context.getString(R.string.action_pause)
        }
        holder.pauseResume.setOnClickListener { onPauseResume(item) }
        // Cancel only makes sense once a download is actually in flight -- a READY
        // item has no live engine for DownloadService.cancelItem to act on, so its
        // Cancel button used to just silently do nothing. Clear (below) covers
        // removing it instead.
        holder.cancel.visibility = if (item.status == ItemStatus.READY) View.GONE else View.VISIBLE
        holder.cancel.setOnClickListener { onCancel(item) }

        // ── Retry / Open / Clear (FAILED gets Retry, DONE gets Open, all three get Clear) ──
        // PENDING/RESOLVING/NEEDS_CHALLENGE get this row too (Clear only, via the
        // same visibility rules below) so a queued item can be removed outright
        // instead of only cancellable-into-FAILED via the row above.
        val showSecondary = item.status == ItemStatus.FAILED || item.status == ItemStatus.DONE ||
            item.status == ItemStatus.READY || item.status == ItemStatus.PENDING ||
            item.status == ItemStatus.RESOLVING || item.status == ItemStatus.NEEDS_CHALLENGE
        holder.secondaryActions.visibility = if (showSecondary) View.VISIBLE else View.GONE
        holder.retry.visibility = if (item.status == ItemStatus.FAILED) View.VISIBLE else View.GONE
        holder.retry.setOnClickListener { onRetry(item) }
        holder.open.visibility = if (item.status == ItemStatus.DONE && item.filePath != null) View.VISIBLE else View.GONE
        holder.open.setOnClickListener { onOpen(item) }
        holder.clear.setOnClickListener { onClear(item) }

        // ── Long-press context menu (DONE/FAILED only) ─────────────────────
        holder.itemView.setOnLongClickListener {
            if (item.status == ItemStatus.DONE || item.status == ItemStatus.FAILED) {
                onLongPress(item)
                true
            } else {
                false
            }
        }
    }

    /** Updates only what a progress tick can change -- status text, size line,
     *  progress bar value, speed/ETA line -- with no visibility/animator churn
     *  on the rest of the row. */
    private fun bindProgressOnly(holder: VH, item: QueueItem) {
        if (item.status == ItemStatus.DOWNLOADING) {
            if (item.platform == MediaPlatform.YOUTUBE) {
                val label = item.mediaFormatLabel?.let { " • $it" }.orEmpty()
                holder.status.text = "⬇  ${if (item.progressPercent >= 0) "${item.progressPercent}%" else "Downloading…"}$label"
            } else {
                val pct = if (item.bytesTotal > 0) (item.bytesDone * 100 / item.bytesTotal) else 0
                holder.status.text = "⬇  ${if (item.bytesTotal > 0) "$pct%" else "Downloading…"}"
            }
        }

        // bytesTotal/bytesDone can go from 0 (unknown, hidden on the initial bind)
        // to a real value on the very first progress tick -- so visibility must be
        // (re)applied here too, not just the text, or the size line stays stuck GONE.
        // YouTube items never populate bytesTotal/bytesDone at all (percent-based,
        // not byte-based) -- branch on platform the same way the full bind does,
        // or this would always fall through to the `else -> GONE` case for them.
        when (item.status) {
            ItemStatus.DOWNLOADING, ItemStatus.PAUSED, ItemStatus.SAVING, ItemStatus.RETRYING -> {
                holder.completedMeta.visibility = View.GONE
                when {
                    item.platform == MediaPlatform.YOUTUBE -> {
                        if (!item.mediaStatusText.isNullOrBlank()) {
                            holder.sizeText.text = item.mediaStatusText
                            holder.sizeText.visibility = View.VISIBLE
                        } else if (item.status == ItemStatus.DOWNLOADING) {
                            holder.sizeText.text = if (item.progressPercent >= 0) "${item.progressPercent}%" else "Connecting…"
                            holder.sizeText.visibility = View.VISIBLE
                        } else {
                            holder.sizeText.visibility = View.GONE
                        }
                    }
                    item.bytesTotal > 0 -> {
                        holder.sizeText.text = "${formatBytes(item.bytesDone)} / ${formatBytes(item.bytesTotal)}"
                        holder.sizeText.visibility = View.VISIBLE
                    }
                    item.bytesDone > 0 -> {
                        holder.sizeText.text = formatBytes(item.bytesDone)
                        holder.sizeText.visibility = View.VISIBLE
                    }
                    else -> holder.sizeText.visibility = View.GONE
                }
                holder.sizeRow.visibility = holder.sizeText.visibility
            }
            ItemStatus.DONE -> {
                val bytes = when {
                    item.bytesTotal > 0 -> item.bytesTotal
                    item.bytesDone > 0 -> item.bytesDone
                    else -> item.filePath?.let { File(it).takeIf { f -> f.exists() }?.length() } ?: 0L
                }
                if (bytes > 0) {
                    holder.sizeText.text = formatBytes(bytes)
                    holder.sizeText.visibility = View.VISIBLE
                } else {
                    holder.sizeText.visibility = View.GONE
                }

                val finishedAt = when {
                    item.downloadFinishedAtMs > 0 -> item.downloadFinishedAtMs
                    else -> item.filePath?.let { File(it).takeIf { f -> f.exists() }?.lastModified() } ?: 0L
                }
                val durationMs = if (item.downloadStartedAtMs > 0 && finishedAt > item.downloadStartedAtMs) {
                    finishedAt - item.downloadStartedAtMs
                } else 0L

                val parts = mutableListOf<String>()
                if (durationMs > 500) {
                    parts.add("Took ${formatElapsedDuration(durationMs)}")
                }
                if (finishedAt > 0) {
                    parts.add(dateFormat.format(Date(finishedAt)))
                }
                if (parts.isNotEmpty()) {
                    holder.completedMeta.text = parts.joinToString("  •  ")
                    holder.completedMeta.visibility = View.VISIBLE
                } else {
                    holder.completedMeta.visibility = View.GONE
                }

                holder.sizeRow.visibility = if (bytes > 0 || parts.isNotEmpty()) View.VISIBLE else View.GONE
            }
            else -> {
                holder.sizeText.visibility = View.GONE
                holder.completedMeta.visibility = View.GONE
                holder.sizeRow.visibility = View.GONE
            }
        }

        if (item.status == ItemStatus.DOWNLOADING) {
            if (item.platform == MediaPlatform.YOUTUBE) {
                if (item.progressPercent >= 0) {
                    holder.progress.isIndeterminate = false
                    holder.progress.progress = item.progressPercent
                } else {
                    holder.progress.isIndeterminate = true
                }
                holder.progress.visibility = View.VISIBLE
            } else if (item.bytesTotal > 0) {
                holder.progress.isIndeterminate = false
                holder.progress.progress = ((item.bytesDone * 100) / item.bytesTotal).toInt()
                holder.progress.visibility = View.VISIBLE
            } else {
                holder.progress.isIndeterminate = true
                holder.progress.visibility = View.VISIBLE
            }
        }

        if (item.status == ItemStatus.DOWNLOADING && item.speedBps > 0) {
            holder.speedEta.text = buildSpeedEtaText(item)
            holder.speedEta.visibility = View.VISIBLE
        } else {
            holder.speedEta.visibility = View.GONE
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Resolves theme-reactive status colors from the current active theme
     *  attribute (so they follow Theme.Xmd.*); the two roles with no
     *  matching M3 attr (warning/success) stay fixed static colors, same
     *  across every theme by design. */
    private fun colorForStatus(context: android.content.Context, status: ItemStatus): Int = when (status) {
        ItemStatus.PENDING,
        ItemStatus.RESOLVING,
        ItemStatus.NEEDS_CHALLENGE -> resolveThemeColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant)
        ItemStatus.READY,
        ItemStatus.DOWNLOADING,
        ItemStatus.SAVING          -> resolveThemeColor(context, com.google.android.material.R.attr.colorPrimary)
        ItemStatus.PAUSED,
        ItemStatus.RETRYING        -> context.getColor(R.color.ff_warning)
        ItemStatus.DONE            -> context.getColor(R.color.ff_success)
        ItemStatus.FAILED          -> resolveThemeColor(context, com.google.android.material.R.attr.colorError)
    }

    private fun resolveThemeColor(context: android.content.Context, attrResId: Int): Int {
        val tv = android.util.TypedValue()
        context.theme.resolveAttribute(attrResId, tv, true)
        return tv.data
    }

    /** "2.1 MB/s  •  ETA 3:42" */
    private fun buildSpeedEtaText(item: QueueItem): String {
        val bps = item.speedBps
        val speedStr = when {
            bps >= 1_048_576.0 -> "%.1f MB/s".format(bps / 1_048_576.0)
            bps >= 1_024.0     -> "%.0f KB/s".format(bps / 1_024.0)
            else               -> "%.0f B/s".format(bps)
        }
        val remaining = (item.bytesTotal - item.bytesDone).coerceAtLeast(0)
        val etaSec = if (bps > 1.0 && item.bytesTotal > 0) (remaining / bps).toLong() else -1L
        return if (etaSec >= 0) "$speedStr  •  ETA ${formatDuration(etaSec)}" else speedStr
    }

    /** Bytes → human-readable string using binary prefixes (KiB, MiB, GiB). */
    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> "%.2f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024L         -> "%.0f KB".format(bytes / 1_024.0)
        else                    -> "$bytes B"
    }

    private fun formatDuration(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    companion object {
        /** Payload marker: only progress-related fields changed between two binds. */
        private const val PAYLOAD_PROGRESS = "payload_progress"

        private val DIFF = object : DiffUtil.ItemCallback<QueueItem>() {
            override fun areItemsTheSame(a: QueueItem, b: QueueItem) = a.id == b.id
            override fun areContentsTheSame(a: QueueItem, b: QueueItem) = a == b

            override fun getChangePayload(a: QueueItem, b: QueueItem): Any? {
                // If everything except bytesDone/bytesTotal/speedBps/progressPercent/
                // mediaStatusText is identical, this is a plain progress tick -- tell
                // the adapter to do a partial (non-animated) rebind instead of a full one.
                val progressOnly = a.copy(bytesDone = 0L, bytesTotal = 0L, speedBps = 0.0, progressPercent = -1, mediaStatusText = null) ==
                    b.copy(bytesDone = 0L, bytesTotal = 0L, speedBps = 0.0, progressPercent = -1, mediaStatusText = null)
                return if (progressOnly) PAYLOAD_PROGRESS else null
            }
        }
    }
}
