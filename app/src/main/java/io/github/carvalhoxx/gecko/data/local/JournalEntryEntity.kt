package io.github.carvalhoxx.gecko.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "JOURNAL_ENTRY",
    foreignKeys = [
        ForeignKey(
            entity = PlaythroughEntity::class,
            parentColumns = ["id"],
            childColumns = ["playthrough_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playthrough_id"])
    ]
)
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "playthrough_id")
    val playthrough_id: Long,

    @ColumnInfo(name = "timestamp_ms")
    val timestamp_ms: Long,

    @ColumnInfo(name = "text_content")
    val text_content: String,

    @ColumnInfo(name = "accumulated_playtime_ms")
    val accumulated_playtime_ms: Long = 0L
)
