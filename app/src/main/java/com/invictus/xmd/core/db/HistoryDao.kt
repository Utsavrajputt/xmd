package com.invictus.xmd.core.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.invictus.xmd.core.HistoryEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    // Flow, not LiveData -- HistoryRepository turns this into a StateFlow
    // for collectAsStateWithLifecycle() in Compose (see HistoryRepository).
    @Query("SELECT * FROM history_entries ORDER BY visitedAtMs DESC LIMIT 500")
    fun observeAll(): Flow<List<HistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntry)

    @Delete
    suspend fun delete(entry: HistoryEntry)

    @Query("DELETE FROM history_entries")
    suspend fun clearAll()
}
