package io.github.carvalhoxx.gecko.domain.usecase

import io.github.carvalhoxx.gecko.domain.fake.FakeGameRepository
import io.github.carvalhoxx.gecko.domain.model.GameItem
import io.github.carvalhoxx.gecko.domain.model.GameStatus
import io.github.carvalhoxx.gecko.domain.model.LicenseType
import io.github.carvalhoxx.gecko.domain.repository.GameRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Teste obrigatório por RN-BKL-01: garante separação clínica entre Backlog Elegível
 * (somente status BACKLOG + licença OWNED/SUBSCRIPTION) e Backlog Visível (BACKLOG + PAUSED + ABANDONED).
 */
class GetEligibleBacklogGamesUseCaseTest {

    private val mixedLibrary = listOf(
        GameItem(id = "backlog-owned", title = "Celeste", status = GameStatus.BACKLOG, licenseType = LicenseType.OWNED),
        GameItem(
            id = "backlog-sub",
            title = "Hades",
            status = GameStatus.BACKLOG,
            licenseType = LicenseType.SUBSCRIPTION
        ),
        GameItem(
            id = "backlog-wishlist-license",
            title = "Invalid License Game",
            status = GameStatus.BACKLOG,
            licenseType = LicenseType.WISHLIST
        ),
        GameItem(id = "paused-game", title = "Sekiro", status = GameStatus.PAUSED, licenseType = LicenseType.OWNED),
        GameItem(
            id = "abandoned-game",
            title = "Returnal",
            status = GameStatus.ABANDONED,
            licenseType = LicenseType.OWNED
        ),
        GameItem(
            id = "wishlist-game",
            title = "GTA VI",
            status = GameStatus.WISHLIST,
            licenseType = LicenseType.WISHLIST
        ),
        GameItem(
            id = "playing-game",
            title = "Elden Ring",
            status = GameStatus.PLAYING,
            licenseType = LicenseType.OWNED
        ),
        GameItem(
            id = "completed-game",
            title = "Hollow Knight",
            status = GameStatus.COMPLETED,
            licenseType = LicenseType.OWNED
        )
    )

    @Test
    fun `RN-BKL-01 returns exclusively BACKLOG games with OWNED or SUBSCRIPTION license`() = runTest {
        val repository = FakeGameRepository(mixedLibrary)
        val useCase = GetEligibleBacklogGamesUseCase(repository)

        val observedIds = useCase().first().map { it.id }
        val snapshotIds = useCase.getSnapshot().map { it.id }

        assertEquals(listOf("backlog-owned", "backlog-sub"), observedIds)
        assertEquals(listOf("backlog-owned", "backlog-sub"), snapshotIds)
    }

    @Test
    fun `RN-BKL-01 defensively filters PAUSED or ABANDONED even if repository leaks them`() = runTest {
        val leakyRepo = object : GameRepository by FakeGameRepository(mixedLibrary) {
            override fun observeEligibleBacklogGames(): Flow<List<GameItem>> = flowOf(mixedLibrary)
            override suspend fun getEligibleBacklogGames(): List<GameItem> = mixedLibrary
        }
        val useCase = GetEligibleBacklogGamesUseCase(leakyRepo)

        assertEquals(listOf("backlog-owned", "backlog-sub"), useCase().first().map { it.id })
        assertEquals(listOf("backlog-owned", "backlog-sub"), useCase.getSnapshot().map { it.id })
    }

    @Test
    fun `GetVisibleBacklogGamesUseCase includes BACKLOG, PAUSED, and ABANDONED and supports sub-filtering`() = runTest {
        val repository = FakeGameRepository(mixedLibrary)
        val useCase = GetVisibleBacklogGamesUseCase(repository)

        val allVisible = useCase().first().map { it.id }.toSet()
        assertEquals(
            setOf("backlog-owned", "backlog-sub", "backlog-wishlist-license", "paused-game", "abandoned-game"),
            allVisible
        )

        assertEquals(listOf("paused-game"), useCase(GameStatus.PAUSED).first().map { it.id })
        assertEquals(listOf("abandoned-game"), useCase(GameStatus.ABANDONED).first().map { it.id })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `GetVisibleBacklogGamesUseCase rejects non-backlog subStatusFilter`() {
        val useCase = GetVisibleBacklogGamesUseCase(FakeGameRepository(mixedLibrary))
        useCase(GameStatus.COMPLETED)
    }
}
