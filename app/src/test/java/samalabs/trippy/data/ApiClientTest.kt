package samalabs.trippy.data

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

private class FakeTokens(var session: Session) : TokenSource {
    override val current: Session get() = session
    override suspend fun updateTokens(access: String, refresh: String?) {
        session = session.copy(accessToken = access, refreshToken = refresh ?: session.refreshToken)
    }
}

class ApiClientTest {
    private val server = MockWebServer()
    private lateinit var tokens: FakeTokens
    private lateinit var api: ApiClient
    private var expired = false

    @Before fun setUp() {
        server.start()
        tokens = FakeTokens(Session(server.url("/").toString().trimEnd('/'), "old", "r1", Profile(1, "alex@example.com")))
        api = ApiClient(tokens).also { it.onSessionExpired = { expired = true } }
    }

    @After fun tearDown() = server.close()

    @Test fun sendsBearer() = runBlocking {
        server.enqueue(MockResponse(code = 200, body = "[]"))
        assertEquals("[]", api.get("/trips/"))
        assertEquals("Bearer old", server.takeRequest().headers["Authorization"])
    }

    @Test fun refreshesOnceOn401ThenRetries() = runBlocking {
        server.enqueue(MockResponse(code = 401, body = """{"detail":"Invalid token"}"""))
        server.enqueue(MockResponse(code = 200, body = """{"access_token":"new","refresh_token":"r2"}"""))
        server.enqueue(MockResponse(code = 200, body = "ok"))
        assertEquals("ok", api.get("/me"))
        server.takeRequest()
        assertEquals("/auth/refresh", server.takeRequest().url.encodedPath)
        assertEquals("Bearer new", server.takeRequest().headers["Authorization"])
        assertEquals("r2", tokens.current.refreshToken)
    }

    @Test fun expiredSessionSignsOut() = runBlocking {
        server.enqueue(MockResponse(code = 401, body = """{"detail":"Invalid token"}"""))
        server.enqueue(MockResponse(code = 401, body = """{"detail":"Invalid token"}"""))
        try { api.get("/me"); fail() } catch (_: SessionExpiredException) {}
        assertTrue(expired)
    }

    @Test fun healthRejectsNonTrippy() = runBlocking {
        server.enqueue(MockResponse(code = 200, body = """{"hello":"world"}"""))
        try { api.health(tokens.current.server); fail() } catch (e: ApiException) {
            assertEquals("Something answered, but it isn't a Trippy server.", e.message)
        }
    }

    @Test fun offlineIsOfflineException() = runBlocking {
        val url = tokens.current.server
        server.close()
        tokens.session = tokens.session.copy(server = url)
        try { api.get("/trips/"); fail() } catch (_: OfflineException) {}
    }
}
