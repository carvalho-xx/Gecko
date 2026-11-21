package io.github.carvalhoxx.gecko.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnualGoalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: AnnualGoalEntity)

    @Query("SELECT * FROM ANNUAL_GOAL WHERE year = :year")
    suspend fun getGoalForYear(year: Int): AnnualGoalEntity?

    @Query("SELECT * FROM ANNUAL_GOAL WHERE year = :year")
    fun observeGoalForYear(year: Int): Flow<AnnualGoalEntity?>

    @Query("DELETE FROM ANNUAL_GOAL WHERE year = :year")
    suspend fun deleteGoalForYear(year: Int)
}
