package io.github.carvalhoxx.gecko.data.remote.hltb

import io.github.carvalhoxx.gecko.core.CoroutineDispatchers
import io.github.carvalhoxx.gecko.data.local.HltbFallbackDao
import io.github.carvalhoxx.gecko.data.local.HltbFallbackEntity
import io.github.carvalhoxx.gecko.data.repository.HltbRepositoryImpl
import io.github.carvalhoxx.gecko.data.sync.HltbRetryScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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

class HltbClientAndRepositoryTest {

    private lateinit var server: MockWebServer
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `handshake 200 sends identical Desktop User-Agent and injects hpKey hpVal in both header and JSON body`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"token":"sess_token_123","hpKey":"x-hp-key","hpVal":"hp_secret_789"}""")
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "data": [
                            {
                              "game_name": "Elden Ring",
                              "comp_main": 216000,
                              "comp_plus": 360000,
                              "comp_100": 486000
                            }
                          ]
                        }
                        """.trimIndent()
                    )
            )

            val fakeDao = InMemoryHltbFallbackDao()
            val retryScheduler = RecordingRetryScheduler()
            val dispatcher = StandardTestDispatcher(testScheduler)
            val apiClient = HltbApiClient(
                okHttpClient = OkHttpClient(),
                json = json,
                baseUrl = server.url("/").toString(),
                clockMs = { 1_767_225_600_000L }
            )
            val repository = HltbRepositoryImpl(
                apiClient = apiClient,
                fallbackDao = fakeDao,
                retryScheduler = retryScheduler,
                dispatchers = TestDispatchers(dispatcher)
            )

            val estimate = repository.fetchEstimate("Elden Ring", genreHint = "rpg")

            assertFalse(estimate.isFromFallback)
            assertEquals(216_000_000L, estimate.mainStoryMs)
            assertEquals(360_000_000L, estimate.mainSidesMs)
            assertEquals(486_000_000L, estimate.completionistMs)
            assertTrue(retryScheduler.scheduledTitles.isEmpty())

            // Cache perpétuo atualizado no Room em Long ms
            val cached = fakeDao.findByNormalizedTitle("elden ring")
            assertNotNull(cached)
            assertEquals(216_000_000L, cached!!.main_story_ms)

            // Valida headers idênticos entre /init e /find + hpKey/hpVal no header e no payload JSON
            val initRequest = server.takeRequest()
            val findRequest = server.takeRequest()

            assertTrue(initRequest.path!!.startsWith("/api/find/init"))
            assertEquals("/api/find", findRequest.path)
            assertEquals(HltbApiClient.DEFAULT_DESKTOP_USER_AGENT, initRequest.getHeader("User-Agent"))
            assertEquals(initRequest.getHeader("User-Agent"), findRequest.getHeader("User-Agent"))
            assertEquals(HltbApiClient.HLTB_REFERER, initRequest.getHeader("Referer"))
            assertEquals(HltbApiClient.HLTB_REFERER, findRequest.getHeader("Referer"))
            assertEquals("sess_token_123", findRequest.getHeader("x-auth-token"))
            assertEquals("hp_secret_789", findRequest.getHeader("x-hp-key"))

            val bodyJson = json.parseToJsonElement(findRequest.body.readUtf8()) as JsonObject
            assertEquals("hp_secret_789", (bodyJson["x-hp-key"] as? JsonPrimitive)?.contentOrNull)
        }

    @Test
    fun `HTTP 403 trips circuit breaker, degrades immediately to Room fallback, and schedules exponential retry`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))

            val fakeDao = InMemoryHltbFallbackDao(
                listOf(
                    HltbFallbackEntity(
                        normalized_title = "hollow knight",
                        display_title = "Hollow Knight",
                        genre_category = "metroidvania",
                        main_story_ms = 97_200_000L,
                        main_sides_ms = 147_600_000L,
                        completionist_ms = 230_400_000L
                    )
                )
            )
            val retryScheduler = RecordingRetryScheduler()
            val dispatcher = StandardTestDispatcher(testScheduler)
            var nowMs = 1_767_225_600_000L
            val apiClient = HltbApiClient(
                okHttpClient = OkHttpClient(),
                json = json,
                baseUrl = server.url("/").toString(),
                clockMs = { nowMs }
            )
            val repository = HltbRepositoryImpl(
                apiClient = apiClient,
                fallbackDao = fakeDao,
                retryScheduler = retryScheduler,
                dispatchers = TestDispatchers(dispatcher)
            ).apply {
                clockMs = { nowMs }
            }

            val firstEstimate = repository.fetchEstimate("Hollow Knight")
            assertTrue(firstEstimate.isFromFallback)
            assertEquals(97_200_000L, firstEstimate.mainStoryMs)
            assertEquals(147_600_000L, firstEstimate.mainSidesMs)
            assertEquals(230_400_000L, firstEstimate.completionistMs)
            assertEquals(listOf("Hollow Knight"), retryScheduler.scheduledTitles)
            assertEquals(1, server.requestCount)

            // Segunda chamada com Circuit Breaker aberto não dispara nova requisição HTTP
            nowMs += 60_000L
            val secondEstimate = repository.fetchEstimate("Hollow Knight")
            assertTrue(secondEstimate.isFromFallback)
            assertEquals(1, server.requestCount)
            assertEquals(2, retryScheduler.scheduledTitles.size)
        }

    @Test
    fun `HTTP 404 on find endpoint falls back to genre median and schedules retry`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"token":"t1","hpKey":"k1","hpVal":"v1"}""")
        )
        server.enqueue(MockResponse().setResponseCode(404))

        val fakeDao = InMemoryHltbFallbackDao(
            listOf(
                HltbFallbackEntity(
                    normalized_title = "genre:rpg",
                    display_title = "Mediana — RPG",
                    genre_category = "genre:rpg",
                    main_story_ms = 144_000_000L,
                    main_sides_ms = 234_000_000L,
                    completionist_ms = 360_000_000L
                )
            )
        )
        val retryScheduler = RecordingRetryScheduler()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = HltbRepositoryImpl(
            apiClient = HltbApiClient(OkHttpClient(), json, baseUrl = server.url("/").toString()),
            fallbackDao = fakeDao,
            retryScheduler = retryScheduler,
            dispatchers = TestDispatchers(dispatcher)
        )

        val estimate = repository.fetchEstimate("Unknown Brand New RPG", genreHint = "RPG")
        assertTrue(estimate.isFromFallback)
        assertEquals(144_000_000L, estimate.mainStoryMs)
        assertEquals(listOf("Unknown Brand New RPG"), retryScheduler.scheduledTitles)
    }

    @Test
    fun `decimal hours and seconds converters produce exact Long ms including above 24h`() = runTest {
        assertEquals(343_800_000L, HltbHoneypotExtractor.decimalHoursToMillis(95.5))
        assertEquals(1_800_000L, HltbHoneypotExtractor.decimalHoursToMillis(0.5))
        assertEquals(0L, HltbHoneypotExtractor.decimalHoursToMillis(-2.0))
        assertEquals(0L, HltbHoneypotExtractor.decimalHoursToMillis(Double.NaN))
        assertEquals(216_000_000L, HltbHoneypotExtractor.secondsToMillis(216_000L))

        // Também valida parsing de horas decimais retornadas no JSON do /api/find
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"nested":{"token":"tok","hpKey":"hk","hpVal":"hv"}}""")
        )
        val decimalHoursResponse =
            """{"data":[{"game_name":"Persona 5","comp_main_hours":95.5,""" +
                """"comp_plus_hours":110.25,"comp_100_hours":140.0}]}"""
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(decimalHoursResponse)
        )

        val client = HltbApiClient(OkHttpClient(), json, baseUrl = server.url("/").toString())
        val result = client.searchGame("Persona 5")
        assertNotNull(result)
        assertEquals(343_800_000L, result!!.mainStoryMs)
        assertEquals(396_900_000L, result.mainSidesMs)
        assertEquals(504_000_000L, result.completionistMs)
    }

    private class RecordingRetryScheduler : HltbRetryScheduler {
        val scheduledTitles = mutableListOf<String>()

        override fun scheduleRetry(title: String, genreHint: String?) {
            scheduledTitles += title
        }
    }

    private class InMemoryHltbFallbackDao(initial: List<HltbFallbackEntity> = emptyList()) : HltbFallbackDao {
        private val map = initial.associateBy { it.normalized_title }.toMutableMap()

        override suspend fun insertAll(entries: List<HltbFallbackEntity>) {
            entries.forEach { map.putIfAbsent(it.normalized_title, it) }
        }

        override suspend fun upsertEntry(entry: HltbFallbackEntity) {
            map[entry.normalized_title] = entry
        }

        override suspend fun findByNormalizedTitle(normalizedTitle: String): HltbFallbackEntity? = map[normalizedTitle]

        override suspend fun searchByTitlePrefix(normalizedPrefix: String, limit: Int): List<HltbFallbackEntity> =
            map.values
                .filter { it.normalized_title.startsWith(normalizedPrefix) }
                .sortedBy { it.normalized_title }
                .take(limit)

        override suspend fun findByGenreCategory(genreCategory: String): HltbFallbackEntity? =
            map.values.firstOrNull { it.genre_category == genreCategory }

        override suspend fun countEntries(): Int = map.size
    }

    private class TestDispatchers(dispatcher: CoroutineDispatcher) : CoroutineDispatchers {
        override val main: CoroutineDispatcher = dispatcher
        override val mainImmediate: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }
}
