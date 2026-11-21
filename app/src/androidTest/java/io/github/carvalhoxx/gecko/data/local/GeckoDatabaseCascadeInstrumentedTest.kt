package io.github.carvalhoxx.gecko.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TI-01: Valida exclusão em cascata (ForeignKey.CASCADE) em todo o DER
 * e valida a query explícita de Backlog Elegível (RN-BKL-01) no SQLite real.
 */
@RunWith(AndroidJUnit4::class)
class GeckoDatabaseCascadeInstrumentedTest {

    private lateinit var database: GeckoDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GeckoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ti01_deletingGameCascadesToPlaythroughJournalPlatformRefAndCustomListRef() = runBlocking {
        val game = Game(
            id = "igdb-1020",
            title = "Elden Ring",
            status = "PLAYING",
            license_type = "OWNED"
        )
        database.gameDao().insertGame(game)

        val playthroughId = database.playthroughDao().insertPlaythrough(
            PlaythroughEntity(
                game_id = game.id,
                start_timestamp_ms = 1_700_000_000_000L
            )
        )
        database.journalEntryDao().insertEntry(
            JournalEntryEntity(
                playthrough_id = playthroughId,
                timestamp_ms = 1_700_010_000_000L,
                text_content = "Derrotei Margit após 3 tentativas.",
                accumulated_playtime_ms = 18_000_000L
            )
        )

        database.platformDao().insertPlatform(PlatformEntity(id = "ps5", name = "PlayStation 5"))
        database.platformDao().insertGamePlatformRef(
            GamePlatformCrossRef(game_id = game.id, platform_id = "ps5")
        )

        val listId = database.customListDao().insertCustomList(
            CustomListEntity(name = "FromSoftware", is_franchise = true, external_franchise_id = 42L)
        )
        database.customListDao().insertGameCustomListRef(
            GameCustomListCrossRef(game_id = game.id, custom_list_id = listId)
        )

        assertEquals(1, database.playthroughDao().getPlaythroughsForGame(game.id).size)
        assertEquals(1, database.journalEntryDao().getEntryCountForPlaythrough(playthroughId))
        assertEquals(listOf("ps5"), database.platformDao().getPlatformIdsForGame(game.id))
        assertEquals(listOf(listId), database.customListDao().getCustomListIdsForGame(game.id))

        database.gameDao().deleteGameById(game.id)

        assertTrue(database.playthroughDao().getPlaythroughsForGame(game.id).isEmpty())
        assertEquals(0, database.journalEntryDao().getEntryCountForPlaythrough(playthroughId))
        assertTrue(database.platformDao().getPlatformIdsForGame(game.id).isEmpty())
        assertTrue(database.customListDao().getCustomListIdsForGame(game.id).isEmpty())

        assertEquals(1, database.platformDao().observeAllPlatforms().first().size)
        assertEquals(1, database.customListDao().observeAllCustomLists().first().size)
    }

    @Test
    fun rnBkl01_eligibleBacklogQueryExcludesPausedAbandonedAndWishlistInSqlite() = runBlocking {
        database.gameDao().insertGames(
            listOf(
                Game(id = "g1", title = "Celeste", status = "BACKLOG", license_type = "OWNED"),
                Game(id = "g2", title = "Hades", status = "BACKLOG", license_type = "SUBSCRIPTION"),
                Game(id = "g3", title = "Sekiro", status = "PAUSED", license_type = "OWNED"),
                Game(id = "g4", title = "Returnal", status = "ABANDONED", license_type = "OWNED"),
                Game(id = "g5", title = "GTA VI", status = "WISHLIST", license_type = "WISHLIST"),
                Game(id = "g6", title = "Silksong", status = "BACKLOG", license_type = "WISHLIST")
            )
        )

        val eligible = database.gameDao().getEligibleBacklogGames()
        assertEquals(listOf("g1", "g2"), eligible.map { it.id })
    }
}
