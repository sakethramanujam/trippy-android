package samalabs.trippy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import samalabs.trippy.data.ApiFlight
import samalabs.trippy.data.ApiLodging
import samalabs.trippy.data.ApiRentalCar
import samalabs.trippy.data.ApiTrip
import java.time.LocalDateTime

class TripSnapshotTest {
    private val now = LocalDateTime.of(2026, 4, 10, 12, 0)

    private fun trip(id: Int, start: String, end: String, vararg flights: ApiFlight, pinned: Boolean = false) =
        ApiTrip(id = id, title = "T$id", startDate = start, endDate = end, flights = flights.toList(), pinned = pinned)

    private fun flight(id: Int, from: String, to: String, dep: String, arr: String) =
        ApiFlight(id = id, departureAirport = from, arrivalAirport = to, departureTime = dep, arrivalTime = arr, flightNumber = "UA$id", airline = "United")

    @Test fun phaseAndCountdown() {
        val up = TripSnapshot(trip(1, "2026-04-20", "2026-04-25"), emptyList(), now)
        assertEquals(TripPhase.UPCOMING, up.phase)
        assertEquals(10, up.countdownDays(now))
        assertEquals(6, up.days)
        val cur = TripSnapshot(trip(2, "2026-04-08", "2026-04-10"), emptyList(), now)
        assertEquals(TripPhase.NOW, cur.phase)
        assertEquals(3, cur.dayOfTrip(now))
        val past = TripSnapshot(trip(3, "2026-03-01", "2026-03-04"), emptyList(), now)
        assertEquals(TripPhase.PAST, past.phase)
    }

    @Test fun routeCodesCollapseRepeatsAndTruncate() {
        val t = trip(1, "2026-04-20", "2026-04-30",
            flight(1, "SFO", "LIS", "2026-04-20T18:00:00", "2026-04-21T14:00:00"),
            flight(2, "LIS", "MAD", "2026-04-25T09:00:00", "2026-04-25T11:30:00"),
            flight(3, "MAD", "JFK", "2026-04-28T10:00:00", "2026-04-28T13:00:00"),
            flight(4, "JFK", "SFO", "2026-04-30T08:00:00", "2026-04-30T11:30:00"))
        val s = TripSnapshot(t, emptyList(), now)
        assertEquals(listOf("SFO", "LIS", "MAD", "JFK", "SFO"), s.stops.map { it.code })
        assertEquals("SFO → LIS → MAD → +2", s.routeCodes)
        assertEquals("Apr 20–30", s.dateRange())
    }

    @Test fun overnightArrivalSortsAfterDepartureAcrossZones() {
        // Lands "earlier" in local time than it left (date line), must still follow the takeoff.
        val t = trip(1, "2026-04-20", "2026-04-22", flight(1, "NRT", "SFO", "2026-04-20T17:00:00", "2026-04-20T10:00:00"))
        val ev = TripSnapshot(t, emptyList(), now).events
        assertEquals(listOf(EventKind.DEPARTURE, EventKind.ARRIVAL), ev.map { it.kind })
    }

    @Test fun unassignedRentalInsideWindowIsLinked() {
        val t = trip(7, "2026-04-20", "2026-04-22")
        val inside = ApiRentalCar(id = 1, company = "Hertz", pickupTime = "2026-04-22T20:00:00", pickupCode = "DEN")
        val outside = ApiRentalCar(id = 2, pickupTime = "2026-04-25T09:00:00")
        val other = ApiRentalCar(id = 3, tripId = 99, pickupTime = "2026-04-21T09:00:00")
        val s = TripSnapshot(t, listOf(inside, outside, other), now)
        assertEquals(listOf(1), s.rentals.map { it.id })
        assertTrue(s.events.any { it.kind == EventKind.PICKUP && it.code == "DEN" })
    }

    @Test fun spendPrefersExpensesElseBookings() {
        val t = ApiTrip(id = 1, startDate = "2026-04-01", endDate = "2026-04-02",
            lodgings = listOf(ApiLodging(id = 1, cost = 300.0), ApiLodging(id = 2, cost = 999.0, status = "cancelled")))
        assertEquals(300.0, TripSnapshot(t, emptyList(), now).spend, 0.001)
    }

    @Test fun sectionsPullHeroPinnedAndYears() {
        val snaps = listOf(
            trip(1, "2026-05-01", "2026-05-03"),
            trip(2, "2026-06-01", "2026-06-03"),
            trip(3, "2025-06-01", "2025-06-03"),
            trip(4, "2024-06-01", "2024-06-03", pinned = true),
            trip(5, "2026-02-01", "2026-02-03"),
        ).map { TripSnapshot(it, emptyList(), now) }
        val sec = TripSections.of(snaps, journeyTripIds = setOf(5))
        assertEquals(1, sec.hero?.id)
        assertEquals(listOf(2), sec.upcoming.map { it.id })
        assertEquals(listOf(4), sec.pinned.map { it.id })
        assertEquals(listOf(2025), sec.pastByYear.map { it.first })
    }

    @Test fun rentalNamesAndStationCodes() {
        val r = ApiRentalCar(id = 1, carModel = "Corolla or similar", vehicleMake = "Toyota", pickupLocation = "Denver DEN Airport")
        assertEquals("Toyota Corolla", r.vehicleName)
        assertEquals("DEN", r.pickupDisplayCode)
        assertEquals("SAF", stationCode(null, "San Francisco", ""))
        assertNull(ApiRentalCar(id = 2).rentalDays)
    }

    @Test fun airbnbAndNights() {
        val h = ApiLodging(id = 1, bookingSource = "airbnb", city = "Breckenridge", checkIn = "2026-01-16T16:00:00", checkOut = "2026-01-19T10:00:00")
        assertTrue(h.isAirbnb)
        assertEquals(3, h.nightTotal)
        assertEquals("AIRBNB · BRECKENRIDGE", h.microLine)
    }

    @Test fun statsLineCountsDaysAwayOnlyForStartedTrips() {
        val snaps = listOf(trip(1, "2026-05-01", "2026-05-03"), trip(2, "2026-03-01", "2026-03-04")).map { TripSnapshot(it, emptyList(), now) }
        assertEquals("2 trips · 4 days away · 0 places · \$0", statsLine(snaps))
    }

    @Test fun fmtDurations() {
        assertEquals("45m", Fmt.duration(45 * 60L))
        assertEquals("3h 12m", Fmt.duration(3 * 3600L + 12 * 60))
        assertEquals("1d 4h", Fmt.duration(28 * 3600L))
    }
}
