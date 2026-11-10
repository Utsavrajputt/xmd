package com.invictus.xmd.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** One visited page in the Browser tab, most-recent first. */
@Entity(tableName = "history_entries")
data class HistoryEntry(
    @PrimaryKey
    val id: String,
    val url: String,
    val title: String,
    val visitedAtMs: Long = System.currentTimeMillis(),
    /** How many times this page was opened (a re-visit bumps it instead of stacking rows). Feeds address-bar ranking. */
    @ColumnInfo(defaultValue = "1")
    val visitCount: Int = 1
)
