package io.github.carvalhoxx.gecko.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HltbFallbackDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<HltbFallbackEntity>)

    @Query("SELECT * FROM HLTB_FALLBACK WHERE normalized_title = :normalizedTitle LIMIT 1")
    suspend fun findByNormalizedTitle(normalizedTitle: String): HltbFallbackEntity?

    @Query(
        """
        SELECT * FROM HLTB_FALLBACK
        WHERE normalized_title LIKE :normalizedPrefix || '%'
        ORDER BY normalized_title ASC
        LIMIT :limit
        """
    )
    suspend fun searchByTitlePrefix(normalizedPrefix: String, limit: Int = 5): List<HltbFallbackEntity>

    @Query("SELECT * FROM HLTB_FALLBACK WHERE genre_category = :genreCategory LIMIT 1")
    suspend fun findByGenreCategory(genreCategory: String): HltbFallbackEntity?

    @Query("SELECT COUNT(*) FROM HLTB_FALLBACK")
    suspend fun countEntries(): Int
}
