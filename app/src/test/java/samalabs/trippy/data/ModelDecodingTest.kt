package samalabs.trippy.data

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDecodingTest {
    @Test fun health() {
        val h = TrippyJson.decodeFromString<ServerHealth>(Fixtures.read("health.json"))
        assertTrue(h.isTrippy)
        assertTrue(h.summary.isNotBlank())
    }

    @Test fun login() {
        val a = TrippyJson.decodeFromString<AuthResponse>(Fixtures.read("login.json"))
        assertEquals("test-access", a.accessToken)
        assertEquals("alex@example.com", a.user?.email)
    }

    @Test fun trips() {
        val trips = TrippyJson.decodeFromString<List<ApiTrip>>(Fixtures.read("trips.json"))
        assertEquals(7, trips.size)
        val austin = trips.first { it.title.startsWith("Austin") }
        assertTrue(austin.pinned)
        assertEquals("United", austin.flights.first().airline)
        assertTrue(austin.lodgings.isNotEmpty())
        assertTrue(austin.participants.isNotEmpty())
        val denver = trips.first { it.title.startsWith("Denver") }
        assertTrue(denver.lodgings.any { it.name != null })
    }

    @Test fun journey() {
        val list = TrippyJson.decodeFromString<List<ApiJourney>>(Fixtures.read("journeys.json"))
        assertEquals(1, list.size)
        val j = TrippyJson.decodeFromString<ApiJourney>(Fixtures.read("journey_1.json"))
        assertEquals(2, j.legs.size)
        assertTrue(j.timeline.isNotEmpty())
        assertEquals(2, j.stats.trips)
        assertEquals("LIS", j.timeline.first().place?.code)
    }

    @Test fun deletePreviewAndStatus() {
        val d = TrippyJson.decodeFromString<DeletePreview>(Fixtures.read("delete_preview.json"))
        assertNotNull(d.action)
        val s = TrippyJson.decodeFromString<FlightStatusResponse>(Fixtures.read("flight_status_unconfigured.json"))
        assertFalse(s.configured)
        assertEquals("Live flight status isn't configured on this server.", s.message)
    }
}
