package io.github.carvalhoxx.gecko.data.repository

import io.github.carvalhoxx.gecko.data.local.GameDao
import io.github.carvalhoxx.gecko.data.repository.mapper.toDomain
import io.github.carvalhoxx.gecko.data.repository.mapper.toEntity
import io.github.carvalhoxx.gecko.domain.model.GameItem
import io.github.carvalhoxx.gecko.domain.model.GameStatus
import io.github.carvalhoxx.gecko.domain.repository.GameRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GameRepositoryImpl @Inject constructor(private val gameDao: GameDao) : GameRepository {

    override fun observeAllGames(): Flow<List<GameItem>> = gameDao.observeAllGames().map { list ->
        list.map { it.toDomain() }
    }

    override fun observeGameById(gameId: String): Flow<GameItem?> = gameDao.observeGameById(gameId).map {
        it?.toDomain()
    }

    override suspend fun getGameById(gameId: String): GameItem? = gameDao.getGameById(gameId)?.toDomain()

    override fun observeGamesByStatus(status: GameStatus): Flow<List<GameItem>> =
        gameDao.observeGamesByStatus(status.name).map { list -> list.map { it.toDomain() } }

    override fun observeEligibleBacklogGames(): Flow<List<GameItem>> =
        gameDao.observeEligibleBacklogGames().map { list -> list.map { it.toDomain() } }

    override suspend fun getEligibleBacklogGames(): List<GameItem> =
        gameDao.getEligibleBacklogGames().map { it.toDomain() }

    override fun observeGameCountsByStatus(): Flow<Map<GameStatus, Int>> =
        gameDao.observeGameCountsByStatus().map { counts ->
            val result = mutableMapOf<GameStatus, Int>()
            counts.forEach { entry ->
                val status = GameStatus.fromStorageValue(entry.status)
                result[status] = (result[status] ?: 0) + entry.count
            }
            result
        }

    override fun observeTotalGamesCount(): Flow<Int> = gameDao.observeTotalGamesCount()

    override suspend fun upsertGame(game: GameItem) {
        gameDao.insertGame(game.toEntity())
    }

    override suspend fun upsertGames(games: List<GameItem>) {
        gameDao.insertGames(games.map { it.toEntity() })
    }

    override suspend fun updatePlatformReportedTime(gameId: String, reportedTimeMs: Long) {
        gameDao.updatePlatformReportedTime(gameId = gameId, reportedTimeMs = reportedTimeMs)
    }

    override suspend fun addLocalLoggedTime(gameId: String, sessionDurationMs: Long) {
        gameDao.addLocalLoggedTime(gameId = gameId, sessionDurationMs = sessionDurationMs)
    }

    override suspend fun deleteGameById(gameId: String) {
        gameDao.deleteGameById(gameId)
    }
}
