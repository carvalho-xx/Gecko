package io.github.carvalhoxx.gecko.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "GAME",
    indices = [
        Index(value = ["steam_app_id"], unique = true),
        Index(value = ["psn_title_id"], unique = true)
    ]
)
data class Game(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "steam_app_id")
    val steam_app_id: Long? = null,

    @ColumnInfo(name = "psn_title_id")
    val psn_title_id: String? = null,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "release_date_ms")
    val release_date_ms: Long? = null,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "license_type")
    val license_type: String,

    @ColumnInfo(name = "is_day_one")
    val is_day_one: Boolean = false,

    @ColumnInfo(name = "hltb_main_story_ms")
    val hltb_main_story_ms: Long = 0L,

    @ColumnInfo(name = "hltb_main_sides_ms")
    val hltb_main_sides_ms: Long = 0L,

    @ColumnInfo(name = "hltb_completionist_ms")
    val hltb_completionist_ms: Long = 0L,

    @ColumnInfo(name = "selected_play_style")
    val selected_play_style: String = "Normal",

    @ColumnInfo(name = "platform_reported_time_ms")
    val platform_reported_time_ms: Long = 0L,

    @ColumnInfo(name = "local_logged_time_ms")
    val local_logged_time_ms: Long = 0L,

    @ColumnInfo(name = "user_rating")
    val user_rating: Float? = null,

    @ColumnInfo(name = "cost_paid")
    val cost_paid: Float? = null,

    @ColumnInfo(name = "cover_url")
    val cover_url: String? = null,

    @ColumnInfo(name = "backdrop_url")
    val backdrop_url: String? = null,

    @ColumnInfo(name = "synopsis")
    val synopsis: String? = null,

    @ColumnInfo(name = "developer")
    val developer: String? = null,

    @ColumnInfo(name = "publisher")
    val publisher: String? = null,

    @ColumnInfo(name = "genres_csv")
    val genres_csv: String? = null,

    @ColumnInfo(name = "subscription_expiry_ms")
    val subscription_expiry_ms: Long? = null,

    @ColumnInfo(name = "status_updated_at_ms")
    val status_updated_at_ms: Long? = null,

    @ColumnInfo(name = "release_precision")
    val release_precision: String? = null,

    @ColumnInfo(name = "retro_achievements_id")
    val retro_achievements_id: Long? = null
)
