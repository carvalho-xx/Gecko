package io.github.carvalhoxx.gecko.data.repository

import io.github.carvalhoxx.gecko.data.local.JournalEntryDao
import io.github.carvalhoxx.gecko.data.local.PlaythroughDao
import io.github.carvalhoxx.gecko.data.repository.mapper.toDomain
import io.github.carvalhoxx.gecko.data.repository.mapper.toEntity
import io.github.carvalhoxx.gecko.domain.model.JournalEntry
import io.github.carvalhoxx.gecko.domain.model.Playthrough
import io.github.carvalhoxx.gecko.domain.repository.PlaythroughRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlaythroughRepositoryImpl @Inject constructor(
    private val playthroughDao: PlaythroughDao,
    private val journalEntryDao: JournalEntryDao
) : PlaythroughRepository {

    override suspend fun insertPlaythrough(playthrough: Playthrough): Long =
        playthroughDao.insertPlaythrough(playthrough.toEntity())

    override suspend fun updatePlaythrough(playthrough: Playthrough) {
        playthroughDao.updatePlaythrough(playthrough.toEntity())
    }

    override suspend fun getPlaythroughById(playthroughId: Long): Playthrough? =
        playthroughDao.getPlaythroughById(playthroughId)?.toDomain()

    override suspend fun getPlaythroughsForGame(gameId: String): List<Playthrough> =
        playthroughDao.getPlaythroughsForGame(gameId).map { it.toDomain() }

    override fun observePlaythroughsForGame(gameId: String): Flow<List<Playthrough>> =
        playthroughDao.observePlaythroughsForGame(gameId).map { list -> list.map { it.toDomain() } }

    override suspend fun getActivePlaythroughForGame(gameId: String): Playthrough? =
        playthroughDao.getActivePlaythroughForGame(gameId)?.toDomain()

    override suspend fun getActiveSessionPlaythrough(): Playthrough? =
        playthroughDao.getActiveSessionPlaythrough()?.toDomain()

    override suspend fun updateCurrentSessionStart(playthroughId: Long, sessionStartMs: Long?) {
        playthroughDao.updateCurrentSessionStart(playthroughId = playthroughId, sessionStartMs = sessionStartMs)
    }

    override fun observeCompletedCountInRange(startTimestampMs: Long, endTimestampMs: Long): Flow<Int> =
        playthroughDao.observeCompletedCountInRange(
            startTimestampMs = startTimestampMs,
            endTimestampMs = endTimestampMs
        )

    override suspend fun insertJournalEntry(entry: JournalEntry): Long = journalEntryDao.insertEntry(entry.toEntity())

    override fun observeJournalEntries(playthroughId: Long): Flow<List<JournalEntry>> =
        journalEntryDao.observeEntriesForPlaythrough(playthroughId).map { list -> list.map { it.toDomain() } }
}
