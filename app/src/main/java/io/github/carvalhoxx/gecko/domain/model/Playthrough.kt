package io.github.carvalhoxx.gecko.domain.model

data class Playthrough(
    val id: Long = 0L,
    val gameId: String,
    val startTimestampMs: Long,
    val endTimestampMs: Long? = null,
    val currentSessionStartMs: Long? = null,
    val rating: Float? = null,
    val notesArchived: Boolean = false
) {
    val isCompleted: Boolean
        get() = endTimestampMs != null

    val hasActiveSession: Boolean
        get() = currentSessionStartMs != null
}

data class JournalEntry(
    val id: Long = 0L,
    val playthroughId: Long,
    val timestampMs: Long,
    val textContent: String,
    val accumulatedPlaytimeMs: Long = 0L
)
