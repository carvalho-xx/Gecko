package io.github.carvalhoxx.gecko.data.remote

import android.content.SharedPreferences
import io.github.carvalhoxx.gecko.core.CoroutineDispatchers
import io.github.carvalhoxx.gecko.data.local.IgdbCredentials
import io.github.carvalhoxx.gecko.data.local.PsnCredentials
import io.github.carvalhoxx.gecko.data.local.RawgCredentials
import io.github.carvalhoxx.gecko.data.local.SecureCredentialsStorage
import io.github.carvalhoxx.gecko.data.local.SteamCredentials
import io.github.carvalhoxx.gecko.data.remote.igdb.IgdbApiClient
import io.github.carvalhoxx.gecko.data.remote.psn.PsnApiClient
import io.github.carvalhoxx.gecko.data.remote.psn.PsnDurationParser
import io.github.carvalhoxx.gecko.data.remote.psn.PsnTitlesFetchResult
import io.github.carvalhoxx.gecko.data.remote.rawg.RawgApiClient
import io.github.carvalhoxx.gecko.data.remote.steam.SteamApiClient
import io.github.carvalhoxx.gecko.data.repository.CatalogSyncRepositoryImpl
import io.github.carvalhoxx.gecko.data.repository.CredentialsRepositoryImpl
import io.github.carvalhoxx.gecko.domain.fake.FakeGameRepository
import io.github.carvalhoxx.gecko.domain.model.ByokConnectionState
import io.github.carvalhoxx.gecko.domain.model.ByokProvider
import io.github.carvalhoxx.gecko.domain.model.GameItem
import io.github.carvalhoxx.gecko.domain.model.GameStatus
import io.github.carvalhoxx.gecko.domain.model.LicenseType
import io.github.carvalhoxx.gecko.domain.model.ReleasePrecision
import io.github.carvalhoxx.gecko.domain.usecase.SearchRemoteCatalogUseCase
import io.github.carvalhoxx.gecko.domain.usecase.TestByokProviderConnectionUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ByokCatalogClientsAndSyncTest {

    private lateinit var server: MockWebServer
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var json: Json
    private lateinit var storage: SecureCredentialsStorage
    private lateinit var baseUrl: String

    private val testDispatchers = object : CoroutineDispatchers {
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val mainImmediate: CoroutineDispatcher = Dispatchers.Unconfined
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("/").toString().trimEnd('/')
        okHttpClient = OkHttpClient.Builder().build()
        json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }
        storage = SecureCredentialsStorage(InMemorySharedPreferences())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `IGDB exchanges OAuth2 token in POST body, parses game metadata, and caches token`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"access_token":"twitch_tok_123","expires_in":3600,"token_type":"bearer"}""")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    [
                      {
                        "id": 119133,
                        "name": "Elden Ring",
                        "summary": "Rise, Tarnished.",
                        "first_release_date": 1645747200,
                        "cover": { "image_id": "co4jni" },
                        "artworks": [ { "image_id": "ar1234" } ],
                        "genres": [ { "name": "Role-playing (RPG)" }, { "name": "Adventure" } ],
                        "platforms": [ { "name": "PC (Microsoft Windows)" }, { "name": "PlayStation 5" } ],
                        "involved_companies": [
                          { "developer": true, "publisher": false, "company": { "name": "FromSoftware" } },
                          { "developer": false, "publisher": true, "company": { "name": "Bandai Namco" } }
                        ],
                        "release_dates": [ { "date": 1645747200, "category": 0 } ]
                      }
                    ]
                    """.trimIndent()
                )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        val client = IgdbApiClient(
            okHttpClient = okHttpClient,
            json = json,
            authBaseUrl = baseUrl,
            apiBaseUrl = baseUrl,
            clockMs = { 1_000_000L }
        )
        val creds = IgdbCredentials(clientId = "igdbClientId123", clientSecret = "superSecretIgdb999")

        val results = client.searchGames(creds, "Elden Ring")
        assertEquals(1, results.size)
        val game = results.first()
        assertEquals("igdb:119133", game.externalId)
        assertEquals("Elden Ring", game.title)
        assertEquals(1_645_747_200_000L, game.releaseDateMs)
        assertEquals(ReleasePrecision.EXACT, game.releasePrecision)
        assertEquals("https://images.igdb.com/igdb/image/upload/t_cover_big/co4jni.jpg", game.coverUrl)
        assertEquals("https://images.igdb.com/igdb/image/upload/t_1080p/ar1234.jpg", game.backdropUrl)
        assertEquals("FromSoftware", game.developer)
        assertEquals("Bandai Namco", game.publisher)
        assertEquals(listOf("Role-playing (RPG)", "Adventure"), game.genres)
        assertEquals(listOf("PC (Microsoft Windows)", "PlayStation 5"), game.platforms)

        // Second search must reuse cached OAuth2 token (total requests = 3, not 4).
        client.searchGames(creds, "Sekiro")
        assertEquals(3, server.requestCount)

        val tokenReq = server.takeRequest()
        assertEquals("/oauth2/token", tokenReq.path)
        assertFalse("Secret must not appear in URL", tokenReq.requestUrl.toString().contains("superSecretIgdb999"))
        assertTrue(tokenReq.body.readUtf8().contains("grant_type=client_credentials"))

        val searchReq = server.takeRequest()
        assertEquals("/v4/games", searchReq.path)
        assertEquals("igdbClientId123", searchReq.getHeader("Client-ID"))
        assertEquals("Bearer twitch_tok_123", searchReq.getHeader("Authorization"))
    }

    @Test
    fun `IGDB HTTP 401 returns EXPIRED status without leaking client secret`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"invalid client"}"""))
        val client = IgdbApiClient(
            okHttpClient = okHttpClient,
            json = json,
            authBaseUrl = baseUrl,
            apiBaseUrl = baseUrl
        )
        val creds = IgdbCredentials(clientId = "igdbClientId123", clientSecret = "secretMustNotLeak88")

        val status = client.testConnection(creds)

        assertEquals(ByokConnectionState.EXPIRED, status.state)
        assertFalse(status.message.orEmpty().contains("secretMustNotLeak88"))
    }

    @Test
    fun `Steam GetOwnedGames converts minutes to Long ms and handles private profile and 403`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "response": {
                        "game_count": 2,
                        "games": [
                          { "appid": 1245620, "name": "ELDEN RING", "playtime_forever": 6120 },
                          { "appid": 367520, "name": "Hollow Knight", "playtime_forever": 1800 }
                        ]
                      }
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"response":{}}"""))
        server.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))

        val client = SteamApiClient(okHttpClient = okHttpClient, json = json, baseUrl = baseUrl)
        val creds = SteamCredentials(apiKey = "A1B2C3D4E5F60718293A4B5C6D7E8F90", steamId64 = "76561198000000001")

        val owned = client.fetchOwnedGames(creds)
        assertFalse(owned.isProfileLikelyPrivate)
        assertEquals(2, owned.games.size)
        assertEquals(1245620L, owned.games[0].appId)
        assertEquals(6120L * 60_000L, owned.games[0].playtimeMs)

        val privateStatus = client.testConnection(creds)
        assertEquals(ByokConnectionState.CONNECTED, privateStatus.state)
        assertTrue(privateStatus.message.orEmpty().contains("privados"))

        val forbiddenStatus = client.testConnection(creds)
        assertEquals(ByokConnectionState.EXPIRED, forbiddenStatus.state)
        assertFalse(forbiddenStatus.message.orEmpty().contains(creds.apiKey))
    }

    @Test
    fun `PSN DurationParser handles ISO-8601 durations over 24h and malformed inputs`() {
        assertEquals(42L * 3_600_000L + 15L * 60_000L + 30_000L, PsnDurationParser.parseIso8601ToMillis("PT42H15M30S"))
        assertEquals(26L * 3_600_000L + 5L * 60_000L, PsnDurationParser.parseIso8601ToMillis("P1DT2H5M"))
        assertEquals(0L, PsnDurationParser.parseIso8601ToMillis("PT0S"))
        assertEquals(0L, PsnDurationParser.parseIso8601ToMillis(null))
        assertEquals(0L, PsnDurationParser.parseIso8601ToMillis("invalid"))
    }

    @Test
    fun `PSN exchanges npsso for OAuth2 tokens, persists them, and handles privacy error 2281077`() {
        val validNpsso = "a".repeat(64)
        storage.savePsnCredentials(PsnCredentials(npsso = validNpsso))

        // 1) Authorize redirect -> code
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "com.scee.psxandroid.scecompcall://redirect?code=v3.authCode99&cid=abc")
        )
        // 2) Token exchange -> access + refresh
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"access_token":"psn_access_ok","refresh_token":"psn_refresh_60d","expires_in":3600}""")
        )
        // 3) Titles fetch -> 200 success
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "titles": [
                        {
                          "titleId": "PPSA01342_00",
                          "name": "Demon's Souls",
                          "playDuration": "PT38H30M0S",
                          "imageUrl": "https://image.api.playstation.com/ds.png"
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )
        // 4) Privacy error 2281077
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"error":{"code":2281077,"message":"Privacy restricted"}}""")
        )

        val client = PsnApiClient(
            okHttpClient = okHttpClient,
            json = json,
            authBaseUrl = baseUrl,
            apiBaseUrl = baseUrl,
            clockMs = { 10_000L }
        )

        val token = client.getOrRefreshAccessToken(storage)
        assertEquals("psn_access_ok", token)
        val savedCreds = storage.getPsnCredentials()
        assertNotNull(savedCreds)
        assertEquals("psn_access_ok", savedCreds?.accessToken)
        assertEquals("psn_refresh_60d", savedCreds?.refreshToken)

        val titlesResult = client.fetchPlayedTitles(token!!)
        assertTrue(titlesResult is PsnTitlesFetchResult.Success)
        val titles = (titlesResult as PsnTitlesFetchResult.Success).titles
        assertEquals(1, titles.size)
        assertEquals("PPSA01342_00", titles.first().titleId)
        assertEquals(38L * 3_600_000L + 30L * 60_000L, titles.first().playtimeMs)

        // Cached token is reused for testConnection -> sees error 2281077 gracefully
        val privacyStatus = client.testConnection(storage)
        assertEquals(ByokConnectionState.CONNECTED, privacyStatus.state)
        assertTrue(privacyStatus.message.orEmpty().contains("Atividade de Jogo"))

        val authReq = server.takeRequest()
        assertEquals("npsso=$validNpsso", authReq.getHeader("Cookie"))
    }

    @Test
    fun `RAWG searches games, respects monthly quota guard, and handles 401 without secret leak`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "results": [
                        {
                          "id": 3498,
                          "name": "Hades",
                          "released": "2020-09-17",
                          "tba": false,
                          "background_image": "https://media.rawg.io/hades.jpg",
                          "genres": [ { "name": "Action" }, { "name": "Indie" } ],
                          "platforms": [ { "platform": { "name": "PC" } } ]
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Invalid API key."}"""))

        val client = RawgApiClient(
            okHttpClient = okHttpClient,
            json = json,
            baseUrl = baseUrl,
            monthlyQuotaLimit = 2,
            clockMs = { 1_700_000_000_000L }
        )
        val creds = RawgCredentials(apiKey = "rawgSecretKey12345678")

        val games = client.searchGames(creds, "Hades")
        assertEquals(1, games.size)
        assertEquals("rawg:3498", games.first().externalId)
        assertEquals("https://media.rawg.io/hades.jpg", games.first().coverUrl)
        assertEquals(ReleasePrecision.EXACT, games.first().releasePrecision)

        val unauthStatus = client.testConnection(creds)
        assertEquals(ByokConnectionState.EXPIRED, unauthStatus.state)
        assertFalse(unauthStatus.message.orEmpty().contains("rawgSecretKey12345678"))

        // 3rd call exceeds monthlyQuotaLimit = 2 -> returns emptyList without hitting server
        val quotaBlocked = client.searchGames(creds, "Celeste")
        assertTrue(quotaBlocked.isEmpty())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `No-Keys Offline Mode degrades gracefully without exceptions across search, sync, and test`() = runTest {
        val gameRepo = FakeGameRepository()
        val repository = buildCatalogSyncRepository(gameRepo)
        val searchUseCase = SearchRemoteCatalogUseCase(repository)
        val testConnectionUseCase = TestByokProviderConnectionUseCase(repository)

        assertTrue(searchUseCase("Hades").isEmpty())
        assertTrue(searchUseCase("a").isEmpty()) // < 2 chars guard

        val syncResult = repository.syncPlatformPlaytimes()
        assertTrue(syncResult.isSuccess)
        assertEquals(0, syncResult.getOrNull())

        val statuses = testConnectionUseCase.testProviders()
        assertEquals(ByokProvider.entries.size, statuses.size)
        assertTrue(statuses.all { it.state == ByokConnectionState.NOT_CONFIGURED })
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `CatalogSyncRepository enriches missing IGDB art via RAWG fallback`() = runTest {
        storage.saveIgdbCredentials(IgdbCredentials(clientId = "igdbClient12345", clientSecret = "igdbSecret12345"))
        storage.saveRawgCredentials(RawgCredentials(apiKey = "rawgApiKey12345678"))

        // IGDB token + game without cover
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"access_token":"tok1","expires_in":3600}"""))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"id":777,"name":"Hollow Knight: Silksong"}]""")
        )
        // RAWG fallback returns artwork
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "results": [
                        {
                          "id": 999,
                          "name": "Hollow Knight: Silksong",
                          "background_image": "https://media.rawg.io/silksong.jpg"
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )

        val repository = buildCatalogSyncRepository(FakeGameRepository())
        val results = SearchRemoteCatalogUseCase(repository)("Silksong")

        assertEquals(1, results.size)
        assertEquals("igdb:777", results.first().externalId)
        assertEquals("https://media.rawg.io/silksong.jpg", results.first().coverUrl)
    }

    @Test
    fun `Delta Baseline sync updates ONLY platform_reported_time_ms and preserves local_logged_time_ms`() = runTest {
        storage.saveSteamCredentials(
            SteamCredentials(apiKey = "A1B2C3D4E5F60718293A4B5C6D7E8F90", steamId64 = "76561198000000001")
        )
        storage.savePsnCredentials(
            PsnCredentials(
                npsso = "b".repeat(64),
                accessToken = "cached_psn_access",
                refreshToken = "cached_psn_refresh",
                expiresAtMs = 999_999_999_999L
            )
        )

        val existingSteamGame = GameItem(
            id = "local_elden",
            steamAppId = 1245620L,
            title = "Elden Ring",
            status = GameStatus.PLAYING,
            licenseType = LicenseType.OWNED,
            platformReportedTimeMs = 10L * 3_600_000L, // 10h
            localLoggedTimeMs = 3L * 3_600_000L // 3h logged locally
        )
        val existingPsnGame = GameItem(
            id = "local_bloodborne",
            psnTitleId = "CUSA00900_00",
            title = "Bloodborne",
            status = GameStatus.PLAYING,
            licenseType = LicenseType.OWNED,
            platformReportedTimeMs = 20L * 3_600_000L, // 20h
            localLoggedTimeMs = 4L * 3_600_000L // 4h logged locally
        )

        val gameRepo = FakeGameRepository(listOf(existingSteamGame, existingPsnGame))

        // Steam reports 15h for Elden Ring (increase) and a brand new Steam game
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "response": {
                        "game_count": 2,
                        "games": [
                          { "appid": 1245620, "name": "Elden Ring", "playtime_forever": 900 },
                          { "appid": 504230, "name": "Celeste", "playtime_forever": 120 }
                        ]
                      }
                    }
                    """.trimIndent()
                )
        )
        // PSN reports 18h for Bloodborne (LOWER than 20h baseline -> must NOT regress!)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "titles": [
                        { "titleId": "CUSA00900_00", "name": "Bloodborne", "playDuration": "PT18H0M0S" }
                      ]
                    }
                    """.trimIndent()
                )
        )

        val repository = buildCatalogSyncRepository(gameRepo)
        val syncResult = repository.syncPlatformPlaytimes()

        assertTrue(syncResult.isSuccess)
        assertEquals(2, syncResult.getOrNull()) // 1 updated (Elden Ring) + 1 imported (Celeste)

        val updatedElden = gameRepo.getGameById("local_elden")!!
        assertEquals(15L * 3_600_000L, updatedElden.platformReportedTimeMs)
        assertEquals(3L * 3_600_000L, updatedElden.localLoggedTimeMs) // Intact!
        assertEquals(18L * 3_600_000L, updatedElden.totalConsolidatedTimeMs)
        assertEquals(GameStatus.PLAYING, updatedElden.status) // Status untouched!

        val unchangedBloodborne = gameRepo.getGameById("local_bloodborne")!!
        assertEquals(20L * 3_600_000L, unchangedBloodborne.platformReportedTimeMs) // Never regresses!
        assertEquals(4L * 3_600_000L, unchangedBloodborne.localLoggedTimeMs) // Intact!

        val importedCeleste = gameRepo.findBySteamAppId(504230L)
        assertNotNull(importedCeleste)
        assertEquals(2L * 3_600_000L, importedCeleste?.platformReportedTimeMs)
        assertEquals(0L, importedCeleste?.localLoggedTimeMs)
        assertEquals(GameStatus.BACKLOG, importedCeleste?.status)
    }

    @Test
    fun `CredentialsRepositoryImpl validates formats and never exposes secrets in error messages`() {
        val repo = CredentialsRepositoryImpl(storage)
        assertFalse(repo.hasAnyConfiguredProvider())

        val invalidSecret = "bad secret with spaces"
        val error = runCatching { repo.saveIgdbCredentials("validClient123", invalidSecret) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertFalse(error?.message.orEmpty().contains(invalidSecret))

        repo.saveRawgApiKey("validRawgApiKey123456")
        assertTrue(repo.hasAnyConfiguredProvider())
        assertEquals(
            ByokConnectionState.CONFIGURED,
            repo.getProviderStatuses().first { it.provider == ByokProvider.RAWG }.state
        )

        repo.clearProvider(ByokProvider.RAWG)
        assertFalse(repo.hasAnyConfiguredProvider())
    }

    private fun buildCatalogSyncRepository(gameRepo: FakeGameRepository): CatalogSyncRepositoryImpl =
        CatalogSyncRepositoryImpl(
            credentialsStorage = storage,
            gameRepository = gameRepo,
            igdbApiClient = IgdbApiClient(
                okHttpClient = okHttpClient,
                json = json,
                authBaseUrl = baseUrl,
                apiBaseUrl = baseUrl,
                clockMs = { 10_000L }
            ),
            steamApiClient = SteamApiClient(okHttpClient = okHttpClient, json = json, baseUrl = baseUrl),
            psnApiClient = PsnApiClient(
                okHttpClient = okHttpClient,
                json = json,
                authBaseUrl = baseUrl,
                apiBaseUrl = baseUrl,
                clockMs = { 10_000L }
            ),
            rawgApiClient = RawgApiClient(
                okHttpClient = okHttpClient,
                json = json,
                baseUrl = baseUrl,
                clockMs = { 10_000L }
            ),
            dispatchers = testDispatchers
        )
}

private class InMemorySharedPreferences : SharedPreferences {
    private val map = linkedMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = map.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues

    override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = map.containsKey(key)

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = linkedMapOf<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = null
        }

        override fun clear(): SharedPreferences.Editor = apply { clearRequested = true }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearRequested) map.clear()
            pending.forEach { (k, v) ->
                if (v == null) map.remove(k) else map[k] = v
            }
        }
    }

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit
}
