package io.github.carvalhoxx.gecko.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlatformDao {

    @Upsert
    suspend fun insertPlatform(platform: PlatformEntity)

    @Upsert
    suspend fun insertPlatforms(platforms: List<PlatformEntity>)

    @Query("SELECT * FROM PLATFORM ORDER BY name ASC")
    fun observeAllPlatforms(): Flow<List<PlatformEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGamePlatformRef(ref: GamePlatformCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGamePlatformRefs(refs: List<GamePlatformCrossRef>)

    @Query("SELECT platform_id FROM GAME_PLATFORM_REF WHERE game_id = :gameId")
    suspend fun getPlatformIdsForGame(gameId: String): List<String>

    @Query(
        """
        SELECT p.* FROM PLATFORM p
        INNER JOIN GAME_PLATFORM_REF ref ON p.id = ref.platform_id
        WHERE ref.game_id = :gameId
        ORDER BY p.name ASC
        """
    )
    fun observePlatformsForGame(gameId: String): Flow<List<PlatformEntity>>

    @Query("DELETE FROM GAME_PLATFORM_REF WHERE game_id = :gameId")
    suspend fun deleteGamePlatformRefs(gameId: String)
}
