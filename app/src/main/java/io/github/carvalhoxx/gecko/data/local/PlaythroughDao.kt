package io.github.carvalhoxx.gecko.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaythroughDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaythrough(playthrough: PlaythroughEntity): Long

    @Update
    suspend fun updatePlaythrough(playthrough: PlaythroughEntity)

    @Query("SELECT * FROM PLAYTHROUGH WHERE id = :playthroughId")
    suspend fun getPlaythroughById(playthroughId: Long): PlaythroughEntity?

    @Query("SELECT * FROM PLAYTHROUGH WHERE game_id = :gameId ORDER BY start_timestamp_ms DESC")
    suspend fun getPlaythroughsForGame(gameId: String): List<PlaythroughEntity>

    @Query("SELECT * FROM PLAYTHROUGH WHERE game_id = :gameId ORDER BY start_timestamp_ms DESC")
    fun observePlaythroughsForGame(gameId: String): Flow<List<PlaythroughEntity>>

    @Query(
        """
        SELECT * FROM PLAYTHROUGH
        WHERE game_id = :gameId AND end_timestamp_ms IS NULL
        ORDER BY start_timestamp_ms DESC
        LIMIT 1
        """
    )
    suspend fun getActivePlaythroughForGame(gameId: String): PlaythroughEntity?

    @Query("SELECT * FROM PLAYTHROUGH WHERE current_session_start_ms IS NOT NULL LIMIT 1")
    suspend fun getActiveSessionPlaythrough(): PlaythroughEntity?

    @Query("UPDATE PLAYTHROUGH SET current_session_start_ms = :sessionStartMs WHERE id = :playthroughId")
    suspend fun updateCurrentSessionStart(playthroughId: Long, sessionStartMs: Long?)

    @Query(
        """
        SELECT COUNT(*) FROM PLAYTHROUGH
        WHERE end_timestamp_ms IS NOT NULL
          AND end_timestamp_ms >= :startTimestampMs
          AND end_timestamp_ms < :endTimestampMs
        """
    )
    fun observeCompletedCountInRange(startTimestampMs: Long, endTimestampMs: Long): Flow<Int>

    @Query("DELETE FROM PLAYTHROUGH WHERE id = :playthroughId")
    suspend fun deletePlaythroughById(playthroughId: Long)
}
