package io.github.carvalhoxx.gecko.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class GameStatusCount(
    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "count")
    val count: Int
)

@Dao
interface GameDao {

    /**
     * Usa @Upsert (INSERT + fallback UPDATE) em vez de REPLACE para nunca disparar
     * ON DELETE CASCADE em PLAYTHROUGH, JOURNAL_ENTRY e tabelas N:N ao atualizar um jogo.
     */
    @Upsert
    suspend fun insertGame(game: Game)

    @Upsert
    suspend fun insertGames(games: List<Game>)

    @Query("SELECT * FROM GAME WHERE id = :gameId")
    suspend fun getGameById(gameId: String): Game?

    @Query("SELECT * FROM GAME WHERE id = :gameId")
    fun observeGameById(gameId: String): Flow<Game?>

    @Query("SELECT * FROM GAME")
    fun observeAllGames(): Flow<List<Game>>

    @Query("SELECT * FROM GAME WHERE status = :status")
    fun observeGamesByStatus(status: String): Flow<List<Game>>

    /**
     * RN-BKL-01: Backlog Elegível estrito para sugestões e planejamento.
     * Filtra explicitamente por status = 'BACKLOG' e licenças ativas ('OWNED', 'SUBSCRIPTION').
     * Proibido usar status != ou NOT IN.
     */
    @Query(
        """
        SELECT * FROM GAME
        WHERE status = 'BACKLOG' AND license_type IN ('OWNED', 'SUBSCRIPTION')
        ORDER BY title ASC
        """
    )
    fun observeEligibleBacklogGames(): Flow<List<Game>>

    @Query(
        """
        SELECT * FROM GAME
        WHERE status = 'BACKLOG' AND license_type IN ('OWNED', 'SUBSCRIPTION')
        ORDER BY title ASC
        """
    )
    suspend fun getEligibleBacklogGames(): List<Game>

    @Query("SELECT status, COUNT(*) AS count FROM GAME GROUP BY status")
    fun observeGameCountsByStatus(): Flow<List<GameStatusCount>>

    @Query("SELECT COUNT(*) FROM GAME")
    fun observeTotalGamesCount(): Flow<Int>

    /**
     * Atualiza exclusivamente o eixo da API externa (Delta Baseline),
     * apenas quando o valor remoto supera o baseline registrado,
     * preservando intacta a coluna local_logged_time_ms.
     */
    @Query(
        """
        UPDATE GAME
        SET platform_reported_time_ms = :reportedTimeMs
        WHERE id = :gameId AND :reportedTimeMs > platform_reported_time_ms
        """
    )
    suspend fun updatePlatformReportedTime(gameId: String, reportedTimeMs: Long)

    /**
     * Incrementa exclusivamente o eixo de sessões cronometradas localmente (Delta Baseline),
     * preservando intacta a coluna platform_reported_time_ms.
     */
    @Query(
        """
        UPDATE GAME
        SET local_logged_time_ms = local_logged_time_ms + :sessionDurationMs
        WHERE id = :gameId
        """
    )
    suspend fun addLocalLoggedTime(gameId: String, sessionDurationMs: Long)

    @Delete
    suspend fun deleteGame(game: Game)

    @Query("DELETE FROM GAME WHERE id = :gameId")
    suspend fun deleteGameById(gameId: String)
}
