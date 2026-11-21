package io.github.carvalhoxx.gecko.data.repository.mapper

import io.github.carvalhoxx.gecko.data.local.JournalEntryEntity
import io.github.carvalhoxx.gecko.data.local.PlaythroughEntity
import io.github.carvalhoxx.gecko.domain.model.JournalEntry
import io.github.carvalhoxx.gecko.domain.model.Playthrough

fun PlaythroughEntity.toDomain(): Playthrough = Playthrough(
    id = id,
    gameId = game_id,
    startTimestampMs = start_timestamp_ms,
    endTimestampMs = end_timestamp_ms,
    currentSessionStartMs = current_session_start_ms,
    rating = rating,
    notesArchived = notes_archived
)

fun Playthrough.toEntity(): PlaythroughEntity = PlaythroughEntity(
    id = id,
    game_id = gameId,
    start_timestamp_ms = startTimestampMs,
    end_timestamp_ms = endTimestampMs,
    current_session_start_ms = currentSessionStartMs,
    rating = rating,
    notes_archived = notesArchived
)

fun JournalEntryEntity.toDomain(): JournalEntry = JournalEntry(
    id = id,
    playthroughId = playthrough_id,
    timestampMs = timestamp_ms,
    textContent = text_content,
    accumulatedPlaytimeMs = accumulated_playtime_ms
)

fun JournalEntry.toEntity(): JournalEntryEntity = JournalEntryEntity(
    id = id,
    playthrough_id = playthroughId,
    timestamp_ms = timestampMs,
    text_content = textContent,
    accumulated_playtime_ms = accumulatedPlaytimeMs
)
