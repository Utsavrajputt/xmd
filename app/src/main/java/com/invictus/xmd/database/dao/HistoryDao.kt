package com.invictus.xmd.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.invictus.xmd.database.entities.HistoryEntry
import com.invictus.xmd.repository.HistoryRepository

@Dao
interface HistoryDao {

    // Flow, not LiveData -- HistoryRepository turns this into a StateFlow
    // for collectAsStateWithLifecycle() in Compose (see HistoryRepository).
    @Query("SELECT * FROM history_entries ORDER BY visitedAtMs DESC LIMIT 1000")
    fun observeAll(): Flow<List<HistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntry)

    @Delete
    suspend fun delete(entry: HistoryEntry)

    @Query("SELECT * FROM history_entries")
    suspend fun getAll(): List<HistoryEntry>

    // Re-visiting the same URL replaces its old row instead of stacking up.
    @Query("DELETE FROM history_entries WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM history_entries")
    suspend fun clearAll()
}
