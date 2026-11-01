package samalabs.trippy.ui.flight

import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import samalabs.trippy.data.ApiClient
import samalabs.trippy.data.ApiFlight
import samalabs.trippy.data.ApiTrip
import samalabs.trippy.data.CachedResponse
import samalabs.trippy.data.MemoryResponseCache
import samalabs.trippy.data.Profile
import samalabs.trippy.data.Session
import samalabs.trippy.data.TokenSource
import samalabs.trippy.data.TripsRepository
import java.time.LocalDate

class AddFlightTest {

    @Test
    fun `defaultDate uses trip start when no flights exist, or day after last flight`() {
        val today = LocalDate.of(2026, 10, 31)
        val emptyTrip = ApiTrip(id = 1, title = "Tokyo", startDate = "2026-11-10T00:00:00")
        assertEquals(LocalDate.of(2026, 11, 10), FlightFormLogic.defaultDate(emptyTrip, today))

        val tripWithFlights = emptyTrip.copy(
            flights = listOf(
                ApiFlight(
                    id = 10,
                    airline = "United Airlines",
                    flightNumber = "UA 837",
                    departureAirport = "SFO",
                    arrivalAirport = "NRT",
                    departureTime = "2026-11-10T11:15:00",
                    arrivalTime = "2026-11-11T15:25:00",
                ),
            ),
        )
        assertEquals(LocalDate.of(2026, 11, 12), FlightFormLogic.defaultDate(tripWithFlights, today))
        assertEquals("NRT", FlightFormLogic.defaultFromAirport(tripWithFlights))
    }

    @Test
    fun `normalizeFlightNumber and inferAirline handle carrier prefixes`() {
        assertEquals("UA 1580", FlightFormLogic.normalizeFlightNumber("ua01580"))
        assertEquals("DL 42", FlightFormLogic.normalizeFlightNumber(" dl  42 "))
        assertEquals("United Airlines", FlightFormLogic.inferAirline("UA 1580"))
        assertEquals("Delta Air Lines", FlightFormLogic.inferAirline("DL42"))
        assertEquals("Custom Air", FlightFormLogic.inferAirline("UA 1580", "Custom Air"))
    }

    @Test
    fun `validate rejects missing fields, unknown IATA codes, and arrival before departure`() {
        val validBase = FlightFormDraft(
            flightNumber = "UA 1580",
            airline = "",
            date = "2026-11-15",
            fromAirport = "SFO",
            toAirport = "JFK",
            departureTime = "09:00",
            arrivalTime = "17:30",
            nextDayArrival = false,
            confirmationNumber = "abc123",
            seat = "12a",
            cabinClass = "business",
        )

        val ok = FlightFormLogic.validate(validBase)
        assertTrue(ok.valid)
        assertNull(ok.error)
        assertEquals("UA 1580", ok.normalizedNumber)
        assertEquals("United Airlines", ok.resolvedAirline)
        assertEquals("2026-11-15T09:00:00", ok.departureIso)
        assertEquals("2026-11-15T17:30:00", ok.arrivalIso)
        assertNotNull(ok.distanceMiles)
        assertTrue((ok.distanceMiles ?: 0.0) > 2500.0)

        // Unknown IATA code
        val unknownIata = FlightFormLogic.validate(validBase.copy(toAirport = "ZZZ"))
        assertFalse(unknownIata.valid)
        assertTrue(unknownIata.error!!.contains("Unknown IATA"))

        // Same airport
        val sameAirport = FlightFormLogic.validate(validBase.copy(toAirport = "SFO"))
        assertFalse(sameAirport.valid)

        // Arrival before departure without +1 day toggle
        val badOrder = FlightFormLogic.validate(validBase.copy(departureTime = "22:00", arrivalTime = "06:15", nextDayArrival = false))
        assertFalse(badOrder.valid)
        assertTrue(badOrder.error!!.contains("+1 day"))

        // Overnight flight with +1 day toggle succeeds
        val overnightOk = FlightFormLogic.validate(validBase.copy(departureTime = "22:00", arrivalTime = "06:15", nextDayArrival = true))
        assertTrue(overnightOk.valid)
        assertEquals("2026-11-15T22:00:00", overnightOk.departureIso)
        assertEquals("2026-11-16T06:15:00", overnightOk.arrivalIso)
    }

    @Test
    fun `parseLookupResponse handles unconfigured and valid schedule payloads`() {
        val unconf = FlightFormLogic.parseLookupResponse("""{"configured":false,"status":null}""", "UA 1580")
        assertEquals("Couldn't look this up, fill it in manually", unconf.message)

        val okJson = """
            {
              "configured": true,
              "status": {
                "provider": "aerodatabox",
                "status": "scheduled",
                "departure_airport": "sfo",
                "arrival_airport": "jfk",
                "departure_scheduled": "2026-11-15 08:30-08:00",
                "arrival_scheduled": "2026-11-15 17:05-05:00",
                "departure_terminal": "3",
                "departure_gate": "F12"
              }
            }
        """.trimIndent()
        val prefill = FlightFormLogic.parseLookupResponse(okJson, "UA 1580")
        assertNull(prefill.message)
        assertEquals("SFO", prefill.fromAirport)
        assertEquals("JFK", prefill.toAirport)
        assertEquals("08:30", prefill.departureTime)
        assertEquals("17:05", prefill.arrivalTime)
        assertEquals(false, prefill.nextDayArrival)
        assertEquals("T3 · F12", prefill.terminalGate)
    }

    @Test
    fun `addFlight posts to trip flights endpoint and updates offline cache`() = runBlocking {
        val cache = MemoryResponseCache()
        val server = MockWebServer().apply { start() }
        val baseUrl = server.url("/").toString().trimEnd('/')
        val session = Session(baseUrl, "tok", "ref", Profile(1, "demo@example.com"))
        cache.write(
            CachedResponse(
                session.scope,
                TripsRepository.TRIPS,
                """[{"id":42,"title":"Japan","start_date":"2026-11-10T00:00:00","flights":[]}]""",
                100L,
            ),
        )
        val createdFlightJson = """{"id":99,"trip_id":42,"airline":"United Airlines","flight_number":"UA 837","departure_airport":"SFO","arrival_airport":"NRT","departure_time":"2026-11-10T11:15:00","arrival_time":"2026-11-11T15:25:00","status":"Scheduled"}"""
        val updatedTripsJson = """[{"id":42,"title":"Japan","start_date":"2026-11-10T00:00:00","flights":[$createdFlightJson]}]"""
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "POST" && request.url.encodedPath == "/trips/42/flights/") {
                    return MockResponse(code = 200, body = createdFlightJson)
                }
                if (request.method == "GET" && request.url.encodedPath.startsWith("/trips")) {
                    return MockResponse(code = 200, body = updatedTripsJson)
                }
                return MockResponse(code = 200, body = "[]")
            }
        }
        val tokens = object : TokenSource {
            override val current: Session = session
            override suspend fun updateTokens(access: String, refresh: String?) = Unit
        }
        val repo = TripsRepository(ApiClient(tokens), cache, scope = { session.scope }, clock = { 200L })
        repo.prime()
        val created = repo.addFlight(42, """{"airline":"United Airlines","flight_number":"UA 837","departure_airport":"SFO","arrival_airport":"NRT","departure_time":"2026-11-10T11:15:00","arrival_time":"2026-11-11T15:25:00"}""")
        assertEquals(99, created.id)
        assertEquals("UA 837", repo.state.value.trips.single().flights.single().flightNumber)
        val cached = cache.read(session.scope, TripsRepository.TRIPS)
        assertNotNull(cached)
        assertTrue(cached!!.body.contains("UA 837"))
        server.close()
    }

    @Test
    fun `addTraveler and mergeTrip update trip state and offline cache`() = runBlocking {
        val cache = MemoryResponseCache()
        val server = MockWebServer().apply { start() }
        val baseUrl = server.url("/").toString().trimEnd('/')
        val session = Session(baseUrl, "tok", "ref", Profile(1, "demo@example.com"))
        cache.write(
            CachedResponse(
                session.scope,
                TripsRepository.TRIPS,
                """[{"id":42,"title":"Japan","start_date":"2026-11-10T00:00:00","participants":[]},{"id":43,"title":"Japan Copy","start_date":"2026-11-10T00:00:00","participants":[]}]""",
                100L,
            ),
        )
        val updatedWithTraveler = """{"id":42,"title":"Japan","start_date":"2026-11-10T00:00:00","participants":[{"id":77,"display_name":"Maya Chen","is_placeholder":true}]}"""
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "POST" && request.url.encodedPath == "/trips/42/travelers") {
                    return MockResponse(code = 200, body = updatedWithTraveler)
                }
                if (request.method == "POST" && request.url.encodedPath == "/trips/42/merge") {
                    return MockResponse(code = 200, body = updatedWithTraveler)
                }
                if (request.method == "GET" && request.url.encodedPath.startsWith("/trips")) {
                    return MockResponse(code = 200, body = "[$updatedWithTraveler]")
                }
                return MockResponse(code = 200, body = "[]")
            }
        }
        val tokens = object : TokenSource {
            override val current: Session = session
            override suspend fun updateTokens(access: String, refresh: String?) = Unit
        }
        val repo = TripsRepository(ApiClient(tokens), cache, scope = { session.scope }, clock = { 200L })
        repo.prime()

        val updated = repo.addTraveler(42, "Maya Chen", null)
        assertEquals("Maya Chen", updated.participants.single().name)
        assertTrue(updated.participants.single().isPlaceholder)

        val merged = repo.mergeTrip(sourceTripId = 43, targetTripId = 42)
        assertEquals(42, merged.id)
        assertEquals(listOf(42), repo.state.value.trips.map { it.id })
        server.close()
    }
}
