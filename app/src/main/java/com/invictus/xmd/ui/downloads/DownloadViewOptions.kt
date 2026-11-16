package com.invictus.xmd.ui.downloads

import com.invictus.xmd.database.entities.QueueItem
import com.invictus.xmd.preferences.Settings
import java.util.Calendar

/** What the Downloads list is ordered by. Direction is a separate flag ([DownloadViewOptions.ascending]). */
enum class DownloadSortBy(
    /** Direction that feels natural when the user first picks this sort. */
    val defaultAscending: Boolean,
) {
    DATE(defaultAscending = false),
    NAME(defaultAscending = true),
    SIZE(defaultAscending = false),
    TYPE(defaultAscending = true),
}

enum class DownloadGroupBy { NONE, DATE, TYPE }

/** Optional parts of a download row; hiding one just drops it from the card. */
enum class DownloadField {
    ICON,
    BADGE,
    SIZE,
    DURATION,
    DATE,
    ACTIONS;

    companion object {
        val ALL: Set<DownloadField> = entries.toSet()
    }
}

/** Everything the "Sort & view" sheet controls. Persisted via [Settings]. */
data class DownloadViewOptions(
    val sortBy: DownloadSortBy = DownloadSortBy.DATE,
    val ascending: Boolean = DownloadSortBy.DATE.defaultAscending,
    val groupBy: DownloadGroupBy = DownloadGroupBy.NONE,
    val compact: Boolean = false,
    val fields: Set<DownloadField> = DownloadField.ALL,
) {
    val isDefault: Boolean get() = this == DownloadViewOptions()

    fun save() {
        Settings.setDownloadsView(
            sortBy = sortBy.name,
            ascending = ascending,
            groupBy = groupBy.name,
            compact = compact,
            // Names joined by ",": an empty string means "every field hidden", null means "never customised".
            fields = fields.joinToString(",") { it.name },
        )
    }

    companion object {
        fun load(): DownloadViewOptions {
            val sortBy = DownloadSortBy.entries.firstOrNull { it.name == Settings.downloadsSortBy() }
                ?: DownloadSortBy.DATE
            val groupBy = DownloadGroupBy.entries.firstOrNull { it.name == Settings.downloadsGroupBy() }
                ?: DownloadGroupBy.NONE
            val fields = Settings.downloadsFields()
                ?.split(',')
                ?.mapNotNull { name -> DownloadField.entries.firstOrNull { it.name == name } }
                ?.toSet()
                ?: DownloadField.ALL
            return DownloadViewOptions(
                sortBy = sortBy,
                ascending = Settings.downloadsSortAscending(),
                groupBy = groupBy,
                compact = Settings.downloadsCompact(),
                fields = fields,
            )
        }
    }
}

// ── Sorting ────────────────────────────────────────────────────────────────

// Finished time, else start time; items that haven't started yet were queued most recently.
internal fun timeKey(item: QueueItem): Long {
    val t = maxOf(item.downloadFinishedAtMs, item.downloadStartedAtMs)
    return if (t == 0L) Long.MAX_VALUE else t
}

internal fun sizeKey(item: QueueItem): Long = maxOf(item.bytesTotal, item.bytesDone)

private fun nameKey(item: QueueItem): String = item.fileName ?: item.sourceUrl

internal fun sortDownloads(list: List<QueueItem>, sortBy: DownloadSortBy, ascending: Boolean): List<QueueItem> {
    val comparator: Comparator<QueueItem> = when (sortBy) {
        DownloadSortBy.DATE -> compareBy<QueueItem> { timeKey(it) }
        DownloadSortBy.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { nameKey(it) }
        DownloadSortBy.SIZE -> compareBy<QueueItem> { sizeKey(it) }
        DownloadSortBy.TYPE -> compareBy<QueueItem> { downloadType(it).ordinal }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { nameKey(it) }
    }
    return list.sortedWith(if (ascending) comparator else comparator.reversed())
}

// ── Grouping ───────────────────────────────────────────────────────────────

enum class DateBucket { TODAY, YESTERDAY, LAST_7_DAYS, LAST_30_DAYS, MONTH }

/**
 * One block of the list. [type] / [bucket] are both null for the single header-less
 * section used when grouping is off (or on the Active tab); [monthStartMs] is only set
 * for [DateBucket.MONTH] and is used to print "March 2026".
 */
internal class DownloadSection(
    val key: String,
    val type: DownloadTypeFilter?,
    val bucket: DateBucket?,
    val monthStartMs: Long,
    val items: List<QueueItem>,
) {
    val hasHeader: Boolean get() = type != null || bucket != null
}

internal fun singleSection(items: List<QueueItem>) =
    DownloadSection("all", type = null, bucket = null, monthStartMs = 0L, items = items)

private fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun startOfMonth(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDay(ms)
    set(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Returns (bucket, monthStart) for a timestamp. Calendar-day based, so DST shifts don't skew "Yesterday". */
private fun bucketFor(ms: Long, todayStart: Long): Pair<DateBucket, Long> {
    val dayStart = startOfDay(ms)
    val days = Math.round((todayStart - dayStart).toDouble() / DAY_MS)
    return when {
        days <= 0 -> DateBucket.TODAY to 0L
        days == 1L -> DateBucket.YESTERDAY to 0L
        days < 7 -> DateBucket.LAST_7_DAYS to 0L
        days < 30 -> DateBucket.LAST_30_DAYS to 0L
        else -> DateBucket.MONTH to startOfMonth(ms)
    }
}

/**
 * Splits an already-sorted list into sections. Items keep their sorted order inside a
 * section; the sections themselves go newest-first (oldest-first for an ascending Date
 * sort) for date groups and in filter-chip order for type groups.
 */
internal fun groupDownloads(
    sorted: List<QueueItem>,
    options: DownloadViewOptions,
    nowMs: Long = System.currentTimeMillis(),
): List<DownloadSection> {
    if (sorted.isEmpty()) return emptyList()
    return when (options.groupBy) {
        DownloadGroupBy.NONE -> listOf(singleSection(sorted))
        DownloadGroupBy.TYPE -> sorted.groupBy { downloadType(it) }
            .toSortedMap(compareBy<DownloadTypeFilter> { it.ordinal })
            .map { (type, items) -> DownloadSection("type_${type.name}", type, null, 0L, items) }
        DownloadGroupBy.DATE -> {
            val todayStart = startOfDay(nowMs)
            // Queued-but-not-started items carry Long.MAX_VALUE: they belong to "Today".
            val grouped = sorted.groupBy { bucketFor(minOf(timeKey(it), nowMs), todayStart) }
            val newestFirst = !(options.sortBy == DownloadSortBy.DATE && options.ascending)
            val order = compareBy<Pair<DateBucket, Long>>({ it.first.ordinal }, { -it.second })
            grouped.toSortedMap(if (newestFirst) order else order.reversed())
                .map { (key, items) ->
                    val (bucket, month) = key
                    DownloadSection("date_${bucket.name}_$month", null, bucket, month, items)
                }
        }
    }
}
