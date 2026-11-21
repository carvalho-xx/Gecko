package io.github.carvalhoxx.gecko.domain.repository

import io.github.carvalhoxx.gecko.domain.model.JournalEntry
import io.github.carvalhoxx.gecko.domain.model.Playthrough
import kotlinx.coroutines.flow.Flow

interface PlaythroughRepository {
    suspend fun insertPlaythrough(playthrough: Playthrough): Long

    suspend fun updatePlaythrough(playthrough: Playthrough)

    suspend fun getPlaythroughById(playthroughId: Long): Playthrough?

    suspend fun getPlaythroughsForGame(gameId: String): List<Playthrough>

    fun observePlaythroughsForGame(gameId: String): Flow<List<Playthrough>>

    suspend fun getActivePlaythroughForGame(gameId: String): Playthrough?

    suspend fun getActiveSessionPlaythrough(): Playthrough?

    suspend fun updateCurrentSessionStart(playthroughId: Long, sessionStartMs: Long?)

    fun observeCompletedCountInRange(startTimestampMs: Long, endTimestampMs: Long): Flow<Int>

    suspend fun insertJournalEntry(entry: JournalEntry): Long

    fun observeJournalEntries(playthroughId: Long): Flow<List<JournalEntry>>
}
