package samalabs.trippy.data

import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import samalabs.trippy.domain.Fmt

private class StaticTokens(override val current: Session) : TokenSource {
    override suspend fun updateTokens(access: String, refresh: String?) = Unit
}

/** Trips, rentals and journeys load in parallel, so answer by path rather than by order. */
private fun MockWebServer.serveTrips(body: String) {
    dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest) =
            MockResponse(code = 200, body = if (request.url.encodedPath.startsWith("/trips")) body else "[]")
    }
}

class OfflineCacheTest {
    private val tripsJson = """[{"id":7,"title":"Lisbon","start_date":"2026-11-02","flights":[{"id":3,"airline":"TAP","flight_number":"TP 236"}]}]"""

    private fun repo(server: MockWebServer, cache: ResponseCache, user: Int = 1, clock: Long = 1_000L): TripsRepository {
        val session = Session(server.url("/").toString().trimEnd('/'), "t", "r", Profile(user, "a@example.com"))
        return TripsRepository(ApiClient(StaticTokens(session)), cache, scope = { session.scope }, clock = { clock })
    }

    @Test fun savesEachGetAndServesItWhenOffline() = runBlocking {
        val cache = MemoryResponseCache()
        val server = MockWebServer().apply { start() }
        server.serveTrips(tripsJson)
        val online = repo(server, cache)
        online.refresh()
        assertEquals(listOf(7), online.state.value.trips.map { it.id })
        assertEquals(3, cache.entries.size)
        val port = server.port
        server.close()

        // A fresh launch with the server gone: the saved copy fills the list and the banner knows when it synced.
        val offline = TripsRepository(
            ApiClient(StaticTokens(Session("http://127.0.0.1:$port", "t", "r", Profile(1, "a@example.com")))), cache,
            scope = { "${server.url("/").toString().trimEnd('/')}#1" }, clock = { 99_000L },
        )
        offline.refresh()
        val s = offline.state.value
        assertTrue(s.offline)
        assertEquals("TP 236", s.trips.single().flights.single().flightNumber)
        assertEquals(1_000L, s.lastSynced)
    }

    @Test fun otherAccountsNeverSeeTheCopy() = runBlocking {
        val cache = MemoryResponseCache()
        val server = MockWebServer().apply { start() }
        cache.write(CachedResponse("${server.url("/").toString().trimEnd('/')}#1", TripsRepository.TRIPS, tripsJson, 5L))
        assertTrue(repo(server, cache, user = 1).prime())
        assertFalse(repo(server, cache, user = 2).prime())
        server.close()
    }

    @Test fun unreadableBodyIsNotSaved() = runBlocking {
        val cache = MemoryResponseCache()
        val server = MockWebServer().apply { start() }
        server.serveTrips("{\"oops\":true}")
        val r = repo(server, cache)
        r.refresh()
        assertNull(cache.entries.keys.firstOrNull { it.second == TripsRepository.TRIPS })
        assertTrue(r.state.value.error != null)
        server.close()
    }

    @Test fun wipeClearsEverything() = runBlocking {
        val cache = MemoryResponseCache()
        cache.write(CachedResponse("s#1", "/trips/", "[]", 1))
        cache.clear()
        assertTrue(cache.entries.isEmpty())
    }

    @Test fun offlineLine() {
        assertEquals("Offline · no saved copy yet", Fmt.offlineLine(null, 0))
        assertEquals("Offline · synced just now", Fmt.offlineLine(0, 30_000))
        assertEquals("Offline · synced 5m ago", Fmt.offlineLine(0, 5 * 60_000L))
        assertEquals("Offline · synced 3h ago", Fmt.offlineLine(0, 3 * 3_600_000L + 120_000))
        assertEquals("Offline · synced 2d ago", Fmt.offlineLine(0, 2 * 86_400_000L + 5))
    }
}
