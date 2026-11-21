package io.github.carvalhoxx.gecko.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: JournalEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: JournalEntryEntity)

    @Query("SELECT * FROM JOURNAL_ENTRY WHERE playthrough_id = :playthroughId ORDER BY timestamp_ms ASC")
    suspend fun getEntriesForPlaythrough(playthroughId: Long): List<JournalEntryEntity>

    @Query("SELECT * FROM JOURNAL_ENTRY WHERE playthrough_id = :playthroughId ORDER BY timestamp_ms ASC")
    fun observeEntriesForPlaythrough(playthroughId: Long): Flow<List<JournalEntryEntity>>

    @Query("SELECT COUNT(*) FROM JOURNAL_ENTRY WHERE playthrough_id = :playthroughId")
    suspend fun getEntryCountForPlaythrough(playthroughId: Long): Int

    @Query("DELETE FROM JOURNAL_ENTRY WHERE id = :entryId")
    suspend fun deleteEntryById(entryId: Long)
}
