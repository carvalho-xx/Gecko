package io.github.carvalhoxx.gecko.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "GAME_CUSTOM_LIST_REF",
    primaryKeys = ["game_id", "custom_list_id"],
    foreignKeys = [
        ForeignKey(
            entity = Game::class,
            parentColumns = ["id"],
            childColumns = ["game_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CustomListEntity::class,
            parentColumns = ["id"],
            childColumns = ["custom_list_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["game_id"]),
        Index(value = ["custom_list_id"])
    ]
)
data class GameCustomListCrossRef(
    @ColumnInfo(name = "game_id")
    val game_id: String,

    @ColumnInfo(name = "custom_list_id")
    val custom_list_id: Long
)
