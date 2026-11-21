package io.github.carvalhoxx.gecko.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Game::class,
        PlaythroughEntity::class,
        JournalEntryEntity::class,
        PlatformEntity::class,
        GamePlatformCrossRef::class,
        CustomListEntity::class,
        GameCustomListCrossRef::class,
        HltbFallbackEntity::class,
        AnnualGoalEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class GeckoDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun playthroughDao(): PlaythroughDao
    abstract fun journalEntryDao(): JournalEntryDao
    abstract fun platformDao(): PlatformDao
    abstract fun customListDao(): CustomListDao
    abstract fun hltbFallbackDao(): HltbFallbackDao
    abstract fun annualGoalDao(): AnnualGoalDao

    companion object {
        const val DATABASE_NAME = "gecko.db"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `GAME` ADD COLUMN `cover_url` TEXT")
                db.execSQL("ALTER TABLE `GAME` ADD COLUMN `backdrop_url` TEXT")
                db.execSQL("ALTER TABLE `GAME` ADD COLUMN `synopsis` TEXT")
                db.execSQL("ALTER TABLE `GAME` ADD COLUMN `developer` TEXT")
                db.execSQL("ALTER TABLE `GAME` ADD COLUMN `publisher` TEXT")
                db.execSQL("ALTER TABLE `GAME` ADD COLUMN `genres_csv` TEXT")
                db.execSQL("ALTER TABLE `GAME` ADD COLUMN `subscription_expiry_ms` INTEGER")
                db.execSQL("ALTER TABLE `GAME` ADD COLUMN `status_updated_at_ms` INTEGER")
                db.execSQL("ALTER TABLE `GAME` ADD COLUMN `release_precision` TEXT")
                db.execSQL("ALTER TABLE `GAME` ADD COLUMN `retro_achievements_id` INTEGER")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `PLAYTHROUGH` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `game_id` TEXT NOT NULL,
                        `start_timestamp_ms` INTEGER NOT NULL,
                        `end_timestamp_ms` INTEGER,
                        `current_session_start_ms` INTEGER,
                        `rating` REAL,
                        `notes_archived` INTEGER NOT NULL,
                        FOREIGN KEY(`game_id`) REFERENCES `GAME`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_PLAYTHROUGH_game_id` ON `PLAYTHROUGH` (`game_id`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `JOURNAL_ENTRY` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `playthrough_id` INTEGER NOT NULL,
                        `timestamp_ms` INTEGER NOT NULL,
                        `text_content` TEXT NOT NULL,
                        `accumulated_playtime_ms` INTEGER NOT NULL,
                        FOREIGN KEY(`playthrough_id`) REFERENCES `PLAYTHROUGH`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_JOURNAL_ENTRY_playthrough_id`
                    ON `JOURNAL_ENTRY` (`playthrough_id`)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `PLATFORM` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `GAME_PLATFORM_REF` (
                        `game_id` TEXT NOT NULL,
                        `platform_id` TEXT NOT NULL,
                        PRIMARY KEY(`game_id`, `platform_id`),
                        FOREIGN KEY(`game_id`) REFERENCES `GAME`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`platform_id`) REFERENCES `PLATFORM`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_GAME_PLATFORM_REF_game_id` ON `GAME_PLATFORM_REF` (`game_id`)"
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_GAME_PLATFORM_REF_platform_id`
                    ON `GAME_PLATFORM_REF` (`platform_id`)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `CUSTOM_LIST` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `is_franchise` INTEGER NOT NULL,
                        `external_franchise_id` INTEGER
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `GAME_CUSTOM_LIST_REF` (
                        `game_id` TEXT NOT NULL,
                        `custom_list_id` INTEGER NOT NULL,
                        PRIMARY KEY(`game_id`, `custom_list_id`),
                        FOREIGN KEY(`game_id`) REFERENCES `GAME`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`custom_list_id`) REFERENCES `CUSTOM_LIST`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_GAME_CUSTOM_LIST_REF_game_id`
                    ON `GAME_CUSTOM_LIST_REF` (`game_id`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_GAME_CUSTOM_LIST_REF_custom_list_id`
                    ON `GAME_CUSTOM_LIST_REF` (`custom_list_id`)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `HLTB_FALLBACK` (
                        `normalized_title` TEXT NOT NULL,
                        `display_title` TEXT NOT NULL,
                        `genre_category` TEXT NOT NULL,
                        `main_story_ms` INTEGER NOT NULL,
                        `main_sides_ms` INTEGER NOT NULL,
                        `completionist_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`normalized_title`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_HLTB_FALLBACK_genre_category`
                    ON `HLTB_FALLBACK` (`genre_category`)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ANNUAL_GOAL` (
                        `year` INTEGER NOT NULL,
                        `target_games` INTEGER NOT NULL,
                        PRIMARY KEY(`year`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
