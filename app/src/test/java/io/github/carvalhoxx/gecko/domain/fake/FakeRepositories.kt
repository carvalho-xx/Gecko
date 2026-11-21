package io.github.carvalhoxx.gecko.domain.fake

import io.github.carvalhoxx.gecko.domain.model.GameItem
import io.github.carvalhoxx.gecko.domain.model.GameStatus
import io.github.carvalhoxx.gecko.domain.model.JournalEntry
import io.github.carvalhoxx.gecko.domain.model.LicenseType
import io.github.carvalhoxx.gecko.domain.model.Playthrough
import io.github.carvalhoxx.gecko.domain.repository.GameRepository
import io.github.carvalhoxx.gecko.domain.repository.PlaythroughRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeGameRepository(initialGames: List<GameItem> = emptyList()) : GameRepository {

    private val gamesFlow = MutableStateFlow(initialGames.associateBy { it.id })

    override fun observeAllGames(): Flow<List<GameItem>> = gamesFlow.map { it.values.toList() }

    override fun observeGameById(gameId: String): Flow<GameItem?> = gamesFlow.map { it[gameId] }

    override suspend fun getGameById(gameId: String): GameItem? = gamesFlow.value[gameId]

    override suspend fun findBySteamAppId(steamAppId: Long): GameItem? =
        gamesFlow.value.values.firstOrNull { it.steamAppId == steamAppId }

    override suspend fun findByPsnTitleId(psnTitleId: String): GameItem? =
        gamesFlow.value.values.firstOrNull { it.psnTitleId == psnTitleId }

    override fun observeGamesByStatus(status: GameStatus): Flow<List<GameItem>> =
        gamesFlow.map { map -> map.values.filter { it.status == status } }

    override fun observeEligibleBacklogGames(): Flow<List<GameItem>> = gamesFlow.map { map ->
        map.values
            .filter {
                it.status == GameStatus.BACKLOG &&
                    (it.licenseType == LicenseType.OWNED || it.licenseType == LicenseType.SUBSCRIPTION)
            }
            .sortedBy { it.title }
    }

    override suspend fun getEligibleBacklogGames(): List<GameItem> = gamesFlow.value.values
        .filter {
            it.status == GameStatus.BACKLOG &&
                (it.licenseType == LicenseType.OWNED || it.licenseType == LicenseType.SUBSCRIPTION)
        }
        .sortedBy { it.title }

    override fun observeGameCountsByStatus(): Flow<Map<GameStatus, Int>> =
        gamesFlow.map { map -> map.values.groupingBy { it.status }.eachCount() }

    override fun observeTotalGamesCount(): Flow<Int> = gamesFlow.map { it.size }

    override suspend fun upsertGame(game: GameItem) {
        gamesFlow.update { it + (game.id to game) }
    }

    override suspend fun upsertGames(games: List<GameItem>) {
        gamesFlow.update { it + games.associateBy { game -> game.id } }
    }

    override suspend fun updatePlatformReportedTime(gameId: String, reportedTimeMs: Long) {
        gamesFlow.update { current ->
            val game = current[gameId] ?: return@update current
            if (reportedTimeMs > game.platformReportedTimeMs) {
                current + (gameId to game.copy(platformReportedTimeMs = reportedTimeMs))
            } else {
                current
            }
        }
    }

    override suspend fun addLocalLoggedTime(gameId: String, sessionDurationMs: Long) {
        gamesFlow.update { current ->
            val game = current[gameId] ?: return@update current
            current + (gameId to game.copy(localLoggedTimeMs = game.localLoggedTimeMs + sessionDurationMs))
        }
    }

    override suspend fun deleteGameById(gameId: String) {
        gamesFlow.update { it - gameId }
    }
}

class FakePlaythroughRepository : PlaythroughRepository {

    private var nextPlaythroughId = 1L
    private var nextJournalId = 1L
    private val playthroughsFlow = MutableStateFlow<Map<Long, Playthrough>>(emptyMap())
    private val journalsFlow = MutableStateFlow<Map<Long, JournalEntry>>(emptyMap())

    override suspend fun insertPlaythrough(playthrough: Playthrough): Long {
        val assignedId = if (playthrough.id > 0L) playthrough.id else nextPlaythroughId++
        playthroughsFlow.update { it + (assignedId to playthrough.copy(id = assignedId)) }
        return assignedId
    }

    override suspend fun updatePlaythrough(playthrough: Playthrough) {
        playthroughsFlow.update { it + (playthrough.id to playthrough) }
    }

    override suspend fun getPlaythroughById(playthroughId: Long): Playthrough? = playthroughsFlow.value[playthroughId]

    override suspend fun getPlaythroughsForGame(gameId: String): List<Playthrough> = playthroughsFlow.value.values
        .filter { it.gameId == gameId }
        .sortedByDescending { it.startTimestampMs }

    override fun observePlaythroughsForGame(gameId: String): Flow<List<Playthrough>> = playthroughsFlow.map { map ->
        map.values
            .filter { it.gameId == gameId }
            .sortedByDescending { it.startTimestampMs }
    }

    override suspend fun getActivePlaythroughForGame(gameId: String): Playthrough? = playthroughsFlow.value.values
        .filter { it.gameId == gameId && it.endTimestampMs == null }
        .maxByOrNull { it.startTimestampMs }

    override suspend fun getActiveSessionPlaythrough(): Playthrough? =
        playthroughsFlow.value.values.firstOrNull { it.currentSessionStartMs != null }

    override suspend fun updateCurrentSessionStart(playthroughId: Long, sessionStartMs: Long?) {
        playthroughsFlow.update { current ->
            val existing = current[playthroughId] ?: return@update current
            current + (playthroughId to existing.copy(currentSessionStartMs = sessionStartMs))
        }
    }

    override fun observeCompletedCountInRange(startTimestampMs: Long, endTimestampMs: Long): Flow<Int> =
        playthroughsFlow.map { map ->
            map.values.count {
                val end = it.endTimestampMs
                end != null && end >= startTimestampMs && end < endTimestampMs
            }
        }

    override suspend fun insertJournalEntry(entry: JournalEntry): Long {
        val assignedId = if (entry.id > 0L) entry.id else nextJournalId++
        journalsFlow.update { it + (assignedId to entry.copy(id = assignedId)) }
        return assignedId
    }

    override fun observeJournalEntries(playthroughId: Long): Flow<List<JournalEntry>> = journalsFlow.map { map ->
        map.values
            .filter { it.playthroughId == playthroughId }
            .sortedBy { it.timestampMs }
    }
}
