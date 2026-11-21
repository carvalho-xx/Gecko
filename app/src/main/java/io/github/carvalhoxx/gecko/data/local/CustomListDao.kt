package io.github.carvalhoxx.gecko.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomListDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCustomList(customList: CustomListEntity): Long

    @Update
    suspend fun updateCustomList(customList: CustomListEntity)

    @Query("SELECT * FROM CUSTOM_LIST ORDER BY name ASC")
    fun observeAllCustomLists(): Flow<List<CustomListEntity>>

    @Query("SELECT * FROM CUSTOM_LIST WHERE is_franchise = 1 ORDER BY name ASC")
    fun observeFranchiseLists(): Flow<List<CustomListEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGameCustomListRef(ref: GameCustomListCrossRef)

    @Query("SELECT custom_list_id FROM GAME_CUSTOM_LIST_REF WHERE game_id = :gameId")
    suspend fun getCustomListIdsForGame(gameId: String): List<Long>

    @Query(
        """
        SELECT g.* FROM GAME g
        INNER JOIN GAME_CUSTOM_LIST_REF ref ON g.id = ref.game_id
        WHERE ref.custom_list_id = :customListId
        ORDER BY g.title ASC
        """
    )
    fun observeGamesInCustomList(customListId: Long): Flow<List<Game>>

    @Query("DELETE FROM CUSTOM_LIST WHERE id = :customListId")
    suspend fun deleteCustomListById(customListId: Long)
}
