package com.local.stzb.data.research

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SharedLineupRankingsRepositoryTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    @Test fun postsAuthenticatedContractAndParsesAllThreeLists() = runTest {
        server.enqueue(okResponse())
        val repository = HttpSharedLineupRankingsRepository(
            server.url("/"), OkHttpClient(), token = { "secret-token" }, clientVersion = "1.2.0",
        )

        val snapshot = repository.load(50)

        val request = server.takeRequest()
        assertEquals("/v1/lineup-rankings/query", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("secret-token", body.getString("token"))
        assertEquals("1.2.0", body.getString("clientVersion"))
        assertEquals(50, body.getInt("limit"))
        assertEquals(80, snapshot.summary.eligibleBattles)
        assertEquals(listOf(1L, 2L, 3L), snapshot.popular.single().heroIds)
        assertEquals(62.3, snapshot.winRate.single().conservativeWinRate, 0.01)
        assertEquals(listOf(4L, 5L, 6L), snapshot.counters.single().opponentHeroIds)
    }

    @Test fun rejectsMissingNoStoreIdentityFieldsAndInvalidShapes() = runTest {
        val bodies = listOf(
            MockResponse().setResponseCode(200).setBody(validBody()),
            MockResponse().setResponseCode(200).addHeader("Cache-Control", "no-store").setBody(validBody().replace("\"ok\":true", "\"ok\":true,\"uid\":123")),
            MockResponse().setResponseCode(200).addHeader("Cache-Control", "no-store").setBody("{}"),
        )
        bodies.forEach(server::enqueue)
        val repository = HttpSharedLineupRankingsRepository(server.url("/"), OkHttpClient(), { "token" }, "1.2.0")
        repeat(3) { assertTrue(runCatching { repository.load() }.isFailure) }
    }

    @Test fun resultStringDoesNotExposeToken() = runTest {
        server.enqueue(okResponse())
        val repository = HttpSharedLineupRankingsRepository(server.url("/"), OkHttpClient(), { "secret-token" }, "1.2.0")
        val snapshot = repository.load()
        assertFalse(snapshot.toString().contains("secret-token"))
    }

    private fun okResponse() = MockResponse().setResponseCode(200).addHeader("Cache-Control", "no-store").setBody(validBody())
    private fun validBody() = """{
      "ok":true,
      "summary":{"eligibleBattles":80,"uniqueLineups":63,"uniqueMatchups":76,"latestBattleAt":1787315909,"generatedAt":"2026-09-14T00:00:00Z"},
      "popular":[{"lineupKey":"1+2+3","heroIds":[1,2,3],"samples":12,"wins":8,"draws":2,"losses":2,"lastSeenAt":9,"rawWinRate":75.0,"conservativeWinRate":46.8,"usageRate":7.5,"confidence":"中"}],
      "winRate":[{"lineupKey":"1+2+3","heroIds":[1,2,3],"samples":12,"wins":9,"draws":1,"losses":2,"lastSeenAt":9,"rawWinRate":79.2,"conservativeWinRate":62.3,"usageRate":7.5,"confidence":"中"}],
      "counters":[{"lineupKey":"1+2+3","heroIds":[1,2,3],"opponentKey":"4+5+6","opponentHeroIds":[4,5,6],"samples":5,"wins":4,"draws":0,"losses":1,"rawWinRate":80.0,"conservativeWinRate":37.6,"confidence":"低"}],
      "requestId":"r1"
    }"""
}
