package io.github.carvalhoxx.gecko.core.csv

import io.github.carvalhoxx.gecko.core.CoroutineDispatchers
import io.github.carvalhoxx.gecko.domain.fake.FakeGameRepository
import io.github.carvalhoxx.gecko.domain.fake.FakePlaythroughRepository
import io.github.carvalhoxx.gecko.domain.model.GameStatus
import io.github.carvalhoxx.gecko.domain.usecase.ImportCsvGamesUseCase
import io.github.carvalhoxx.gecko.domain.usecase.PreviewCsvImportUseCase
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvSanitizerAndParsersTest {

    private fun loadRealCsvFixture(): File {
        val candidates = listOf(
            File("docs/assets/jogos zerados.csv"),
            File("../docs/assets/jogos zerados.csv")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Real CSV fixture not found. Searched: ${candidates.map { it.absolutePath }}")
    }

    @Test
    fun `TU-03 DurationRegexParser converts arbitrary durations above 24h and milliseconds to Long ms`() {
        assertEquals(343_800_000L, DurationRegexParser.parseToMillisOrNull("95:30:00"))
        assertEquals(75_927_000L, DurationRegexParser.parseToMillisOrNull("21:05:27.000"))
        assertEquals(2_100_000L, DurationRegexParser.parseToMillisOrNull("0:35:00"))
        assertEquals(27_900_000L, DurationRegexParser.parseToMillisOrNull("07:45:00.000"))
        assertEquals(3_723_500L, DurationRegexParser.parseToMillisOrNull("1:02:03.5"))
        assertEquals(343_800_000L, DurationRegexParser.parseToMillisOrNull("95:30"))
        assertEquals(5_143_320_000L, DurationRegexParser.parseToMillisOrNull("1428:42"))
        assertEquals(45_000_000L, DurationRegexParser.parseToMillisOrNull("12,5h"))

        assertNull(DurationRegexParser.parseToMillisOrNull("95:75:00"))
        assertNull(DurationRegexParser.parseToMillisOrNull("N/A"))
        assertNull(DurationRegexParser.parseToMillisOrNull(""))
        assertNull(DurationRegexParser.parseToMillisOrNull(null))
    }

    @Test
    fun `TU-04 RFC 4180 state machine handles quoted commas, doubled quotes, mixed ratings, and floating blocks`() {
        val rawCsv = buildString {
            append('\uFEFF')
            appendLine("Titulo,Tempo,Nota,Genero,Plataforma")
            appendLine("\"No, i'm not a human\",4:30:00,\"8,5\",Terror/Texto,PC")
            appendLine("\"Game \"\"Quoted\"\" Edition\",95:30:00,8.5,RPG,PS5")
            appendLine("ShortRowOnlyTitle")
            appendLine(",,,,")
            appendLine("ATUALIZAÇÃO,23/8/2026,,,")
            appendLine("TOP 10 PREFERIDOS,1,Elden Ring,,")
            appendLine("DESAFIOS,0%,Pokemon,,")
            appendLine("OutOfRangeRatingGame,1:00:00,\"10,5\",Indie,PC")
        }

        val report = CsvSanitizerParser.sanitize(rawCsv)

        assertEquals(4, report.validRows.size)
        assertEquals("No, i'm not a human", report.validRows[0].title)
        assertEquals(16_200_000L, report.validRows[0].playtimeMs)
        assertEquals(8.5f, report.validRows[0].rating)
        assertEquals(listOf("Terror", "Texto"), report.validRows[0].genres)

        assertEquals("Game \"Quoted\" Edition", report.validRows[1].title)
        assertEquals(343_800_000L, report.validRows[1].playtimeMs)
        assertEquals(8.5f, report.validRows[1].rating)

        assertEquals("ShortRowOnlyTitle", report.validRows[2].title)
        assertEquals(0L, report.validRows[2].playtimeMs)
        assertNull(report.validRows[2].rating)

        // Nota fora da escala válida (10.5) é descartada graciosamente sem derrubar o jogo
        assertEquals("OutOfRangeRatingGame", report.validRows[3].title)
        assertNull(report.validRows[3].rating)

        val floatingIgnored = report.ignoredRows.filter { it.reason == IgnoredRowReason.FLOATING_BLOCK_OUTSIDE_1FN }
        assertEquals(3, floatingIgnored.size)
    }

    @Test
    fun `RatingNormalizer accepts comma and dot decimals and honorary 11`() {
        assertEquals(9.25f, RatingNormalizer.normalizeOrNull("9,25"))
        assertEquals(9.25f, RatingNormalizer.normalizeOrNull("9.25"))
        assertEquals(10.0f, RatingNormalizer.normalizeOrNull("10"))
        assertEquals(11.0f, RatingNormalizer.normalizeOrNull("11,00"))
        assertEquals(0.0f, RatingNormalizer.normalizeOrNull("0"))
        assertNull(RatingNormalizer.normalizeOrNull("10,5"))
        assertNull(RatingNormalizer.normalizeOrNull("-1"))
        assertNull(RatingNormalizer.normalizeOrNull("abc"))
    }

    @Test
    fun `real fixture jogos zerados csv parses all 112 games accurately and imports idempotently`() = runTest {
        val rawCsv = loadRealCsvFixture().readText(Charsets.UTF_8)
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = TestCoroutineDispatchers(testDispatcher)

        val previewUseCase = PreviewCsvImportUseCase(dispatchers)
        val report = previewUseCase(rawCsv)

        assertEquals(112, report.validRows.size)

        val eldenRing = report.validRows.firstOrNull { it.title == "Elden Ring" }
        assertNotNull(eldenRing)
        assertEquals(343_800_000L, eldenRing!!.playtimeMs)
        assertEquals(10.0f, eldenRing.rating)
        assertEquals("PC", eldenRing.platform)

        val demonsSouls = report.validRows.firstOrNull { it.title == "Demon's Souls" }
        assertNotNull(demonsSouls)
        assertEquals(75_927_000L, demonsSouls!!.playtimeMs)
        assertEquals(8.75f, demonsSouls.rating)
        assertEquals("PS5", demonsSouls.platform)

        val quotedCommaGame = report.validRows.firstOrNull { it.title == "No, i'm not a human" }
        assertNotNull(quotedCommaGame)
        assertEquals(16_200_000L, quotedCommaGame!!.playtimeMs)
        assertEquals(8.0f, quotedCommaGame.rating)

        val buckshot = report.validRows.firstOrNull { it.title == "Buckshot Roulette" }
        assertNotNull(buckshot)
        assertEquals(2_100_000L, buckshot!!.playtimeMs)

        // Importação assíncrona + idempotência na reimportação
        val gameRepo = FakeGameRepository()
        val playthroughRepo = FakePlaythroughRepository()
        val importUseCase = ImportCsvGamesUseCase(gameRepo, playthroughRepo, dispatchers) { 1_767_225_600_000L }

        val firstSummary = importUseCase(rawCsv)
        assertEquals(112, firstSummary.importedCount)
        val persistedGames = gameRepo.observeAllGames().first()
        assertEquals(112, persistedGames.size)
        assertTrue(persistedGames.all { it.status == GameStatus.COMPLETED })
        assertTrue(persistedGames.all { it.platformReportedTimeMs == 0L })

        val secondSummary = importUseCase(rawCsv)
        assertEquals(112, secondSummary.importedCount)
        assertEquals(112, gameRepo.observeAllGames().first().size)
    }

    private class TestCoroutineDispatchers(dispatcher: CoroutineDispatcher) : CoroutineDispatchers {
        override val main: CoroutineDispatcher = dispatcher
        override val mainImmediate: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }
}
