package io.github.carvalhoxx.gecko.domain.repository

import io.github.carvalhoxx.gecko.domain.model.GameItem
import io.github.carvalhoxx.gecko.domain.model.GameStatus
import kotlinx.coroutines.flow.Flow

interface GameRepository {
    fun observeAllGames(): Flow<List<GameItem>>

    fun observeGameById(gameId: String): Flow<GameItem?>

    suspend fun getGameById(gameId: String): GameItem?

    suspend fun findBySteamAppId(steamAppId: Long): GameItem?

    suspend fun findByPsnTitleId(psnTitleId: String): GameItem?

    fun observeGamesByStatus(status: GameStatus): Flow<List<GameItem>>

    /**
     * RN-BKL-01: Fonte exclusiva para sugestões e planejamento (status = BACKLOG + licença ativa).
     */
    fun observeEligibleBacklogGames(): Flow<List<GameItem>>

    /**
     * RN-BKL-01: Consulta pontual de Backlog Elegível.
     */
    suspend fun getEligibleBacklogGames(): List<GameItem>

    fun observeGameCountsByStatus(): Flow<Map<GameStatus, Int>>

    fun observeTotalGamesCount(): Flow<Int>

    suspend fun upsertGame(game: GameItem)

    suspend fun upsertGames(games: List<GameItem>)

    suspend fun updatePlatformReportedTime(gameId: String, reportedTimeMs: Long)

    suspend fun addLocalLoggedTime(gameId: String, sessionDurationMs: Long)

    suspend fun deleteGameById(gameId: String)
}
