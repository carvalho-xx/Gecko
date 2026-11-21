package io.github.carvalhoxx.gecko.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "HLTB_FALLBACK",
    indices = [
        Index(value = ["genre_category"])
    ]
)
data class HltbFallbackEntity(
    @PrimaryKey
    @ColumnInfo(name = "normalized_title")
    val normalized_title: String,

    @ColumnInfo(name = "display_title")
    val display_title: String,

    @ColumnInfo(name = "genre_category")
    val genre_category: String,

    @ColumnInfo(name = "main_story_ms")
    val main_story_ms: Long,

    @ColumnInfo(name = "main_sides_ms")
    val main_sides_ms: Long,

    @ColumnInfo(name = "completionist_ms")
    val completionist_ms: Long
)
