package io.github.carvalhoxx.gecko.data.repository.mapper

import io.github.carvalhoxx.gecko.data.local.Game
import io.github.carvalhoxx.gecko.data.local.JournalEntryEntity
import io.github.carvalhoxx.gecko.data.local.PlaythroughEntity
import io.github.carvalhoxx.gecko.data.local.UserPreferences
import io.github.carvalhoxx.gecko.domain.model.GameItem
import io.github.carvalhoxx.gecko.domain.model.GameStatus
import io.github.carvalhoxx.gecko.domain.model.LicenseType
import io.github.carvalhoxx.gecko.domain.model.PlayStyle
import io.github.carvalhoxx.gecko.domain.model.ReleasePrecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityDomainMapperTest {

    @Test
    fun `GameItem toEntity and toDomain preserve all 26 fields and Long durations above 24h`() {
        val over24HoursMs = 95L * 3_600_000L + 30L * 60_000L // 343_800_000L ms
        val localSessionMs = 7_200_000L // 2h

        val domain = GameItem(
            id = "igdb-1020",
            steamAppId = 1245620L,
            psnTitleId = "PPSA04610_00",
            title = "Elden Ring",
            releaseDateMs = 1645747200000L,
            status = GameStatus.PLAYING,
            licenseType = LicenseType.OWNED,
            isDayOne = true,
            hltbMainStoryMs = 216_000_000L,
            hltbMainSidesMs = 360_000_000L,
            hltbCompletionistMs = 486_000_000L,
            selectedPlayStyle = PlayStyle.PLATINUM,
            platformReportedTimeMs = over24HoursMs,
            localLoggedTimeMs = localSessionMs,
            userRating = 11.0f,
            costPaid = 249.90f,
            coverUrl = "https://images.igdb.com/cover.jpg",
            backdropUrl = "https://images.igdb.com/backdrop.jpg",
            synopsis = "Rise, Tarnished.",
            developer = "FromSoftware",
            publisher = "Bandai Namco",
            genres = listOf("RPG", "Action"),
            subscriptionExpiryMs = 1_900_000_000_000L,
            statusUpdatedAtMs = 1_750_000_000_000L,
            releasePrecision = ReleasePrecision.EXACT,
            retroAchievementsId = 9988L
        )

        val entity = domain.toEntity()
        val roundTrip = entity.toDomain()

        assertEquals(domain, roundTrip)
        assertEquals("PLAYING", entity.status)
        assertEquals("OWNED", entity.license_type)
        assertEquals("PLATINUM", entity.selected_play_style)
        assertEquals("RPG,Action", entity.genres_csv)
        assertEquals(over24HoursMs + localSessionMs, roundTrip.totalConsolidatedTimeMs)
        assertEquals(216_000_000L, roundTrip.targetHltbTimeMs(PlayStyle.RUSH))
        assertEquals(360_000_000L, roundTrip.targetHltbTimeMs(PlayStyle.NORMAL))
        assertEquals(486_000_000L, roundTrip.targetHltbTimeMs(PlayStyle.PLATINUM))
    }

    @Test
    fun `Game toDomain parses legacy Portuguese status, license, and play style strings`() {
        val legacyEntity = Game(
            id = "legacy-1",
            title = "Hollow Knight",
            status = "Jogando Atualmente",
            license_type = "Assinatura Temporária",
            selected_play_style = "Rushar",
            genres_csv = " Metroidvania , Indie , "
        )

        val domain = legacyEntity.toDomain()
        assertEquals(GameStatus.PLAYING, domain.status)
        assertEquals(LicenseType.SUBSCRIPTION, domain.licenseType)
        assertEquals(PlayStyle.RUSH, domain.selectedPlayStyle)
        assertEquals(listOf("Metroidvania", "Indie"), domain.genres)
    }

    @Test
    fun `Playthrough and JournalEntry mappers round-trip accurately`() {
        val playthroughEntity = PlaythroughEntity(
            id = 12L,
            game_id = "igdb-1020",
            start_timestamp_ms = 1_700_000_000_000L,
            end_timestamp_ms = null,
            current_session_start_ms = 1_700_005_000_000L,
            rating = 9.75f,
            notes_archived = false
        )
        val playthrough = playthroughEntity.toDomain()
        assertFalse(playthrough.isCompleted)
        assertTrue(playthrough.hasActiveSession)
        assertEquals(playthroughEntity, playthrough.toEntity())

        val journalEntity = JournalEntryEntity(
            id = 33L,
            playthrough_id = 12L,
            timestamp_ms = 1_700_006_000_000L,
            text_content = "Cheguei em Leyndell.",
            accumulated_playtime_ms = 144_000_000L
        )
        assertEquals(journalEntity, journalEntity.toDomain().toEntity())
    }

    @Test
    fun `UserPreferences toDomain maps profile, pace settings, and flags`() {
        val prefs = UserPreferences(
            profileName = "Carlos",
            avatarLocalUri = "content://avatar/1",
            dailyAvailabilityMs = 5_400_000L,
            defaultPlayStyle = "PLATINUM",
            isFirstLaunchCompleted = true,
            enableFinanceModule = true,
            calibrationFactor = 1.15f
        )

        val settings = prefs.toDomain()
        assertEquals("Carlos", settings.profile.name)
        assertEquals("content://avatar/1", settings.profile.avatarLocalUri)
        assertEquals(5_400_000L, settings.paceSettings.dailyAvailabilityMs)
        assertEquals(PlayStyle.PLATINUM, settings.paceSettings.defaultPlayStyle)
        assertEquals(1.15f, settings.paceSettings.calibrationFactor, 0.001f)
        assertTrue(settings.isFirstLaunchCompleted)
        assertTrue(settings.enableFinanceModule)
    }
}
