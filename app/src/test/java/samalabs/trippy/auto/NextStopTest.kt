package samalabs.trippy.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import samalabs.trippy.data.ApiFlight
import samalabs.trippy.data.ApiLodging
import samalabs.trippy.data.ApiRentalCar
import samalabs.trippy.data.ApiTrip
import java.time.LocalDateTime

class NextStopTest {
    private val now = LocalDateTime.of(2026, 12, 1, 8, 0)

    @Test
    fun picksUpcomingFlightAsNextStop() {
        val flight = ApiFlight(
            id = 1,
            airline = "United",
            flightNumber = "UA 837",
            departureAirport = "SFO",
            arrivalAirport = "ZRH",
            departureTime = "2026-12-01T10:00:00",
            arrivalTime = "2026-12-02T06:00:00",
            confirmationNumber = "CONF123",
        )
        val stay = ApiLodging(
            id = 2,
            name = "Hotel Schweizerhof",
            city = "Zürich",
            checkIn = "2026-12-02T15:00:00",
            checkOut = "2026-12-05T11:00:00",
        )
        val trip = ApiTrip(
            id = 10,
            title = "Switzerland",
            flights = listOf(flight),
            lodgings = listOf(stay),
        )

        val stop = NextStop.find(listOf(trip), emptyList(), now)
        assertNotNull(stop)
        assertTrue(stop is NextStop.Flight)
        assertEquals("United UA 837", stop?.title)
        assertEquals("SFO → ZRH · Conf: CONF123", stop?.subtitle)
        assertEquals("SFO Airport", stop?.navigationQuery)
        assertEquals("Navigate to SFO", stop?.actionLabel)
        assertEquals("Departs Dec 1, 10:00 AM", stop?.timeDescription)
    }

    @Test
    fun picksUpcomingStayWhenEarlierThanFlight() {
        val flight = ApiFlight(
            id = 1,
            airline = "Swiss",
            flightNumber = "LX 38",
            departureAirport = "ZRH",
            arrivalAirport = "SFO",
            departureTime = "2026-12-05T13:00:00",
        )
        val stay = ApiLodging(
            id = 2,
            name = "Hotel Schweizerhof",
            city = "Zürich",
            country = "Switzerland",
            checkIn = "2026-12-01T14:00:00",
            checkOut = "2026-12-05T11:00:00",
            confirmationNumber = "SWISS456",
        )
        val trip = ApiTrip(
            id = 10,
            title = "Switzerland",
            flights = listOf(flight),
            lodgings = listOf(stay),
        )

        val stop = NextStop.find(listOf(trip), emptyList(), now)
        assertNotNull(stop)
        assertTrue(stop is NextStop.Stay)
        assertEquals("Hotel Schweizerhof", stop?.title)
        assertEquals("Zürich, Switzerland · Conf: SWISS456", stop?.subtitle)
        assertEquals("Hotel Schweizerhof, Zürich, Switzerland", stop?.navigationQuery)
        assertEquals("Navigate to hotel", stop?.actionLabel)
        assertEquals("Check-in Dec 1, 2:00 PM", stop?.timeDescription)
    }

    @Test
    fun picksRentalPickup() {
        val rental = ApiRentalCar(
            id = 5,
            company = "Sixt",
            pickupCity = "Zürich",
            pickupCode = "ZRH",
            pickupTime = "2026-12-01T09:30:00",
            dropoffTime = "2026-12-05T10:00:00",
            confirmationNumber = "SX-1234",
        )
        val stop = NextStop.find(emptyList(), listOf(rental), now)
        assertNotNull(stop)
        assertTrue(stop is NextStop.RentalPickup)
        assertEquals("Sixt", stop?.title)
        assertEquals("ZRH · Pick up · Conf: SX-1234", stop?.subtitle)
        assertEquals("Navigate to rental pickup", stop?.actionLabel)
        assertEquals("Pick up Dec 1, 9:30 AM", stop?.timeDescription)
    }

    @Test
    fun returnsNullWhenAllEventsArePast() {
        val pastFlight = ApiFlight(
            id = 1,
            airline = "United",
            flightNumber = "UA 100",
            departureAirport = "SFO",
            arrivalAirport = "LAX",
            departureTime = "2026-11-20T10:00:00",
            arrivalTime = "2026-11-20T11:30:00",
        )
        val trip = ApiTrip(id = 1, title = "Past", flights = listOf(pastFlight))
        val stop = NextStop.find(listOf(trip), emptyList(), now)
        assertNull(stop)
    }

    @Test
    fun returnsNullForEmptyTrips() {
        assertNull(NextStop.find(emptyList(), emptyList(), now))
    }
}
