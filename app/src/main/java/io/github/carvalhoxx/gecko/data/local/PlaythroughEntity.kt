package io.github.carvalhoxx.gecko.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "PLAYTHROUGH",
    foreignKeys = [
        ForeignKey(
            entity = Game::class,
            parentColumns = ["id"],
            childColumns = ["game_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["game_id"])
    ]
)
data class PlaythroughEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "game_id")
    val game_id: String,

    @ColumnInfo(name = "start_timestamp_ms")
    val start_timestamp_ms: Long,

    @ColumnInfo(name = "end_timestamp_ms")
    val end_timestamp_ms: Long? = null,

    @ColumnInfo(name = "current_session_start_ms")
    val current_session_start_ms: Long? = null,

    @ColumnInfo(name = "rating")
    val rating: Float? = null,

    @ColumnInfo(name = "notes_archived")
    val notes_archived: Boolean = false
)
