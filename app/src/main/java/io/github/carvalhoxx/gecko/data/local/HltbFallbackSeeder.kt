package io.github.carvalhoxx.gecko.data.local

import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class HltbFallbackSeedDto(
    val normalized_title: String,
    val display_title: String,
    val genre_category: String,
    val main_story_ms: Long,
    val main_sides_ms: Long,
    val completionist_ms: Long
)

object HltbFallbackSeeder {

    const val ASSET_FILE_NAME = "hltb_fallback_seed.json"

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parseSeedJson(rawJson: String): List<HltbFallbackEntity> =
        json.decodeFromString<List<HltbFallbackSeedDto>>(rawJson).map { dto ->
            HltbFallbackEntity(
                normalized_title = dto.normalized_title,
                display_title = dto.display_title,
                genre_category = dto.genre_category,
                main_story_ms = dto.main_story_ms,
                main_sides_ms = dto.main_sides_ms,
                completionist_ms = dto.completionist_ms
            )
        }

    fun loadFromAssets(context: Context): List<HltbFallbackEntity> {
        val rawJson = context.assets.open(ASSET_FILE_NAME).bufferedReader().use { it.readText() }
        return parseSeedJson(rawJson)
    }

    fun populateInto(db: SupportSQLiteDatabase, entries: List<HltbFallbackEntity>) {
        if (entries.isEmpty()) return

        db.beginTransaction()
        try {
            entries.forEach { entry ->
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `HLTB_FALLBACK` (
                        `normalized_title`,
                        `display_title`,
                        `genre_category`,
                        `main_story_ms`,
                        `main_sides_ms`,
                        `completionist_ms`
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(
                        entry.normalized_title,
                        entry.display_title,
                        entry.genre_category,
                        entry.main_story_ms,
                        entry.main_sides_ms,
                        entry.completionist_ms
                    )
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

class HltbFallbackCallback(private val context: Context) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seedIfEmpty(db)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        seedIfEmpty(db)
    }

    private fun seedIfEmpty(db: SupportSQLiteDatabase) {
        val count = db.query("SELECT COUNT(*) FROM `HLTB_FALLBACK`").use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
        if (count == 0) {
            val entries = HltbFallbackSeeder.loadFromAssets(context)
            HltbFallbackSeeder.populateInto(db, entries)
        }
    }
}
