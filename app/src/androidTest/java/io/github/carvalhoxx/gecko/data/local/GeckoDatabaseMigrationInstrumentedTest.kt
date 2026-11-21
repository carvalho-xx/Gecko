package io.github.carvalhoxx.gecko.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TI-03: Testa a migração versionada v1 -> v2 via MigrationTestHelper preservando
 * colunas Delta Baseline em Long ms e criando todas as novas tabelas do DER.
 */
@RunWith(AndroidJUnit4::class)
class GeckoDatabaseMigrationInstrumentedTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GeckoDatabase::class.java
    )

    @Test
    fun migrate1To2_preservesDeltaBaselineAndCreatesAllDerTables() {
        val dbName = "gecko-migration-test"
        val over24HoursMs = 343_800_000L // 95h 30m
        val localSessionMs = 7_200_000L // 2h

        helper.createDatabase(dbName, 1).apply {
            execSQL(
                """
                INSERT INTO GAME (
                    id, steam_app_id, psn_title_id, title, release_date_ms, status,
                    license_type, is_day_one, hltb_main_story_ms, hltb_main_sides_ms,
                    hltb_completionist_ms, selected_play_style, platform_reported_time_ms,
                    local_logged_time_ms, user_rating, cost_paid
                ) VALUES (
                    'igdb-1020', 1245620, 'PPSA04610_00', 'Elden Ring', 1645747200000, 'PLAYING',
                    'OWNED', 1, 216000000, 360000000,
                    486000000, 'Normal', $over24HoursMs,
                    $localSessionMs, 11.0, 249.90
                )
                """.trimIndent()
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(dbName, 2, true, GeckoDatabase.MIGRATION_1_2)

        migratedDb.query(
            "SELECT title, platform_reported_time_ms, local_logged_time_ms, cover_url FROM GAME WHERE id = 'igdb-1020'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Elden Ring", cursor.getString(0))
            assertEquals(over24HoursMs, cursor.getLong(1))
            assertEquals(localSessionMs, cursor.getLong(2))
            assertTrue(cursor.isNull(3))
        }

        migratedDb.execSQL(
            "INSERT INTO ANNUAL_GOAL (year, target_games) VALUES (2026, 24)"
        )
        migratedDb.close()
    }
}
