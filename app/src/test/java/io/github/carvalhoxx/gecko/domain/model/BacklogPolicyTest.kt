package io.github.carvalhoxx.gecko.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BacklogPolicyTest {

    @Test
    fun `VISIBLE includes BACKLOG, PAUSED, and ABANDONED exclusively`() {
        assertEquals(
            setOf(GameStatus.BACKLOG, GameStatus.PAUSED, GameStatus.ABANDONED),
            BacklogPolicy.VISIBLE
        )
        assertTrue(BacklogPolicy.isVisibleInBacklog(GameStatus.BACKLOG))
        assertTrue(BacklogPolicy.isVisibleInBacklog(GameStatus.PAUSED))
        assertTrue(BacklogPolicy.isVisibleInBacklog(GameStatus.ABANDONED))
        assertFalse(BacklogPolicy.isVisibleInBacklog(GameStatus.WISHLIST))
        assertFalse(BacklogPolicy.isVisibleInBacklog(GameStatus.PLAYING))
        assertFalse(BacklogPolicy.isVisibleInBacklog(GameStatus.COMPLETED))
    }

    @Test
    fun `ELIGIBLE includes strictly BACKLOG and never PAUSED or ABANDONED`() {
        assertEquals(setOf(GameStatus.BACKLOG), BacklogPolicy.ELIGIBLE)
        assertFalse(GameStatus.PAUSED in BacklogPolicy.ELIGIBLE)
        assertFalse(GameStatus.ABANDONED in BacklogPolicy.ELIGIBLE)
    }

    @Test
    fun `isEligibleForSuggestions allows only BACKLOG with OWNED or SUBSCRIPTION license`() {
        GameStatus.entries.forEach { status ->
            LicenseType.entries.forEach { license ->
                val expected = status == GameStatus.BACKLOG &&
                    (license == LicenseType.OWNED || license == LicenseType.SUBSCRIPTION)
                assertEquals(
                    "Unexpected eligibility for ($status, $license)",
                    expected,
                    BacklogPolicy.isEligibleForSuggestions(status, license)
                )
            }
        }
    }
}
