package io.github.carvalhoxx.gecko.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ANNUAL_GOAL")
data class AnnualGoalEntity(
    @PrimaryKey
    @ColumnInfo(name = "year")
    val year: Int,

    @ColumnInfo(name = "target_games")
    val target_games: Int
)
