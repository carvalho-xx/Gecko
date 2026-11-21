package io.github.carvalhoxx.gecko.domain.usecase

import io.github.carvalhoxx.gecko.domain.fake.FakeGameRepository
import io.github.carvalhoxx.gecko.domain.model.GameItem
import io.github.carvalhoxx.gecko.domain.model.GameStatus
import io.github.carvalhoxx.gecko.domain.model.LicenseType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingSortingAndDashboardKpisUseCaseTest {

    @Test
    fun `ValidateAndSaveRatingUseCase accepts 0_00 to 10_00 and exact 11_00 and rejects out of range`() = runTest {
        val repo = FakeGameRepository(
            listOf(
                GameItem(
                    id = "g1",
                    title = "Elden Ring",
                    status = GameStatus.COMPLETED,
                    licenseType = LicenseType.OWNED
                )
            )
        )
        val useCase = ValidateAndSaveRatingUseCase(repo)

        // Limites válidos
        listOf(0.00f, 8.75f, 10.00f, 11.00f).forEach { validRating ->
            val result = useCase("g1", validRating)
            assertTrue("Expected $validRating to be valid", result is SaveRatingResult.Success)
            assertEquals(validRating, (result as SaveRatingResult.Success).updatedGame.userRating)
            assertEquals(validRating, repo.getGameById("g1")?.userRating)
        }

        // Fora da faixa (entre 10.00 e 11.00, negativo, acima de 11.00)
        listOf(-0.1f, -1.0f, 10.01f, 10.25f, 10.50f, 11.5f, 12.0f).forEach { invalidRating ->
            val result = useCase("g1", invalidRating)
            assertTrue("Expected $invalidRating to fail", result is SaveRatingResult.InvalidRating)
            assertEquals(
                RatingValidationError.OUT_OF_RANGE,
                (result as SaveRatingResult.InvalidRating).error
            )
        }

        // Mais de 2 casas decimais e não finitos
        val tooManyDecimals = useCase("g1", 8.755f)
        assertTrue(tooManyDecimals is SaveRatingResult.InvalidRating)
        assertEquals(
            RatingValidationError.TOO_MANY_DECIMALS,
            (tooManyDecimals as SaveRatingResult.InvalidRating).error
        )

        val nanResult = useCase("g1", Float.NaN)
        assertTrue(nanResult is SaveRatingResult.InvalidRating)
        assertEquals(RatingValidationError.NOT_FINITE, (nanResult as SaveRatingResult.InvalidRating).error)

        // Jogo inexistente e limpeza de nota
        assertTrue(useCase("missing", 9.0f) is SaveRatingResult.GameNotFound)
        val cleared = useCase.clearRating("g1") as SaveRatingResult.Success
        assertNull(cleared.updatedGame.userRating)
    }

    @Test
    fun `SortGamesWithTieBreakerUseCase ranks by rating, then Delta Baseline playtime, then latest completion`() {
        val hourMs = 3_600_000L
        val lifeGame = GameItem(
            id = "life",
            title = "Chrono Trigger",
            status = GameStatus.COMPLETED,
            licenseType = LicenseType.OWNED,
            userRating = 11.00f,
            platformReportedTimeMs = 25L * hourMs,
            statusUpdatedAtMs = 1_700_000_000_000L
        )
        val tieHighTime = GameItem(
            id = "tie-time",
            title = "Elden Ring",
            status = GameStatus.COMPLETED,
            licenseType = LicenseType.OWNED,
            userRating = 9.50f,
            platformReportedTimeMs = 95L * hourMs,
            localLoggedTimeMs = 10L * hourMs, // Total 105h (> 24h)
            statusUpdatedAtMs = 1_690_000_000_000L
        )
        val tieRecentCompletion = GameItem(
            id = "tie-recent",
            title = "Hollow Knight",
            status = GameStatus.COMPLETED,
            licenseType = LicenseType.OWNED,
            userRating = 9.50f,
            platformReportedTimeMs = 40L * hourMs,
            localLoggedTimeMs = 10L * hourMs, // Total 50h
            statusUpdatedAtMs = 1_750_000_000_000L
        )
        val tieOlderCompletion = GameItem(
            id = "tie-older",
            title = "Celeste",
            status = GameStatus.COMPLETED,
            licenseType = LicenseType.OWNED,
            userRating = 9.50f,
            platformReportedTimeMs = 50L * hourMs, // Total 50h (empate nível 2)
            localLoggedTimeMs = 0L,
            statusUpdatedAtMs = 1_710_000_000_000L
        )
        val unrated = GameItem(
            id = "unrated",
            title = "Portal 2",
            status = GameStatus.BACKLOG,
            licenseType = LicenseType.OWNED,
            userRating = null,
            platformReportedTimeMs = 200L * hourMs
        )

        val useCase = SortGamesWithTieBreakerUseCase()
        val sorted = useCase(
            games = listOf(unrated, tieOlderCompletion, tieRecentCompletion, tieHighTime, lifeGame),
            completionTimestampsByGameId = mapOf(
                "tie-recent" to 1_760_000_000_000L,
                "tie-older" to 1_720_000_000_000L
            )
        )

        assertEquals(
            listOf("life", "tie-time", "tie-recent", "tie-older", "unrated"),
            sorted.map { it.id }
        )
    }

    @Test
    fun `GetDashboardKpisUseCase handles empty and populated libraries accurately`() = runTest {
        val emptyRepo = FakeGameRepository(emptyList())
        val emptyKpis = GetDashboardKpisUseCase(emptyRepo)().first()
        assertEquals(0, emptyKpis.totalGamesCount)
        assertEquals(0, emptyKpis.completedGamesCount)
        assertEquals(0, emptyKpis.playingGamesCount)
        assertEquals(0L, emptyKpis.totalAccumulatedTimeMs)
        assertNull(emptyKpis.averageRating)
        assertEquals(0, emptyKpis.ratedGamesCount)

        val hourMs = 3_600_000L
        val populatedRepo = FakeGameRepository(
            listOf(
                GameItem(
                    id = "1",
                    title = "Elden Ring",
                    status = GameStatus.COMPLETED,
                    licenseType = LicenseType.OWNED,
                    platformReportedTimeMs = 100L * hourMs,
                    localLoggedTimeMs = 5L * hourMs,
                    userRating = 11.00f
                ),
                GameItem(
                    id = "2",
                    title = "Celeste",
                    status = GameStatus.COMPLETED,
                    licenseType = LicenseType.OWNED,
                    platformReportedTimeMs = 12L * hourMs,
                    localLoggedTimeMs = 3L * hourMs,
                    userRating = 9.00f
                ),
                GameItem(
                    id = "3",
                    title = "Hades",
                    status = GameStatus.PLAYING,
                    licenseType = LicenseType.SUBSCRIPTION,
                    platformReportedTimeMs = 10L * hourMs,
                    localLoggedTimeMs = 0L,
                    userRating = 8.50f
                ),
                GameItem(
                    id = "4",
                    title = "Sekiro",
                    status = GameStatus.BACKLOG,
                    licenseType = LicenseType.OWNED,
                    platformReportedTimeMs = 0L,
                    localLoggedTimeMs = 0L,
                    userRating = null
                )
            )
        )

        val kpis = GetDashboardKpisUseCase(populatedRepo)().first()
        assertEquals(4, kpis.totalGamesCount)
        assertEquals(2, kpis.completedGamesCount)
        assertEquals(1, kpis.playingGamesCount)
        assertEquals(130L * hourMs, kpis.totalAccumulatedTimeMs)
        assertEquals(3, kpis.ratedGamesCount)
        assertEquals(9.50f, kpis.averageRating!!, 0.001f)
    }
}
