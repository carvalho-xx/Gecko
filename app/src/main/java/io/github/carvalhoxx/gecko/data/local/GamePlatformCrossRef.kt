package io.github.carvalhoxx.gecko.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "GAME_PLATFORM_REF",
    primaryKeys = ["game_id", "platform_id"],
    foreignKeys = [
        ForeignKey(
            entity = Game::class,
            parentColumns = ["id"],
            childColumns = ["game_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PlatformEntity::class,
            parentColumns = ["id"],
            childColumns = ["platform_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["game_id"]),
        Index(value = ["platform_id"])
    ]
)
data class GamePlatformCrossRef(
    @ColumnInfo(name = "game_id")
    val game_id: String,

    @ColumnInfo(name = "platform_id")
    val platform_id: String
)
