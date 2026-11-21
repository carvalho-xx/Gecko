package io.github.carvalhoxx.gecko.data.local

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HltbFallbackSeederTest {

    private fun loadSeedAssetFile(): File {
        val relativePath = "src/main/assets/${HltbFallbackSeeder.ASSET_FILE_NAME}"
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("HLTB seed asset not found. Searched: ${candidates.map { it.absolutePath }}")
    }

    @Test
    fun `seed JSON parses into valid HLTB fallback catalog with Long durations and genre medians`() {
        val entries = HltbFallbackSeeder.parseSeedJson(loadSeedAssetFile().readText())

        assertTrue("Fallback catalog should contain at least 20 entries", entries.size >= 20)

        val uniqueTitles = entries.map { it.normalized_title }.toSet()
        assertEquals("All normalized_title keys must be unique", entries.size, uniqueTitles.size)

        entries.forEach { entry ->
            assertEquals(
                "normalized_title must be lowercase and trimmed",
                entry.normalized_title.trim().lowercase(),
                entry.normalized_title
            )
            assertTrue("main_story_ms must be > 0 for ${entry.display_title}", entry.main_story_ms > 0L)
            assertTrue(
                "main_sides_ms must be >= main_story_ms for ${entry.display_title}",
                entry.main_sides_ms >= entry.main_story_ms
            )
            assertTrue(
                "completionist_ms must be >= main_sides_ms for ${entry.display_title}",
                entry.completionist_ms >= entry.main_sides_ms
            )
        }

        val eldenRing = entries.firstOrNull { it.normalized_title == "elden ring" }
        assertNotNull(eldenRing)
        assertTrue("Elden Ring duration must exceed 24h in Long ms", eldenRing!!.main_story_ms > 86_400_000L)

        val defaultMedian = entries.firstOrNull { it.genre_category == "genre:default" }
        assertNotNull("Default genre fallback median must be present", defaultMedian)
    }
}
