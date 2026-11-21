package io.github.carvalhoxx.gecko.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.carvalhoxx.gecko.core.DefaultCoroutineDispatchers
import io.github.carvalhoxx.gecko.data.local.GeckoDatabase
import io.github.carvalhoxx.gecko.data.repository.GameRepositoryImpl
import io.github.carvalhoxx.gecko.data.repository.PlaythroughRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CsvImportInstrumentedTest {

    private lateinit var database: GeckoDatabase
    private lateinit var gameRepository: GameRepositoryImpl
    private lateinit var playthroughRepository: PlaythroughRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GeckoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gameRepository = GameRepositoryImpl(database.gameDao())
        playthroughRepository = PlaythroughRepositoryImpl(
            database.playthroughDao(),
            database.journalEntryDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun importCsvBatchPersistsInRealSqliteAndReimportRemainsIdempotentWithoutCascadeLoss() = runBlocking {
        val rawCsv = """
            Games Zerados,,Lançamento,,Genero,Tempo,Conclusão,,Nota,Consoles,,,
            ,,,,,,,,,,,,
            1,Elden Ring,24/2,2022,Souls Like,95:30:00,08/11,2022,10,PC,,ATUALIZAÇÃO,23/8/2026
            2,"No, i'm not a human",15/09,2025,Terror/Texto,4:30:00,26/09,2025,"8,5",PC,,TOP 10,Elden Ring
            3,Demon's Souls,12/11,2020,Souls Like,21:05:27.000,25/05,2025,"8,75",PS5,,,
        """.trimIndent()

        val useCase = ImportCsvGamesUseCase(
            gameRepository = gameRepository,
            playthroughRepository = playthroughRepository,
            dispatchers = DefaultCoroutineDispatchers(),
            clockMs = { 1_767_225_600_000L }
        )

        val firstImport = useCase(rawCsv)
        assertEquals(3, firstImport.importedCount)
        assertEquals(3, gameRepository.observeTotalGamesCount().first())

        val eldenId = ImportCsvGamesUseCase.generateDeterministicCsvId("Elden Ring", "PC")
        val elden = gameRepository.getGameById(eldenId)
        assertNotNull(elden)
        assertEquals(343_800_000L, elden!!.localLoggedTimeMs)
        assertEquals(0L, elden.platformReportedTimeMs)
        assertEquals(1, playthroughRepository.getPlaythroughsForGame(eldenId).size)

        // Reimportar não duplica jogos nem dispara CASCADE nos playthroughs existentes
        val secondImport = useCase(rawCsv)
        assertEquals(3, secondImport.importedCount)
        assertEquals(3, gameRepository.observeTotalGamesCount().first())
        assertEquals(1, playthroughRepository.getPlaythroughsForGame(eldenId).size)
    }
}
