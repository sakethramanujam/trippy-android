package samalabs.trippy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import samalabs.trippy.data.ApiFlight
import samalabs.trippy.data.ApiLodging
import samalabs.trippy.data.ApiRentalCar
import samalabs.trippy.data.ApiTrip

class TripSearchTest {
    private val trip = ApiTrip(
        id = 1, title = "Swiss Alps", startDate = "2026-12-01", endDate = "2026-12-08",
        flights = listOf(
            ApiFlight(3, airline = "United", flightNumber = "UA 837", departureAirport = "SFO", arrivalAirport = "ZRH",
                departureTime = "2026-12-01T10:00:00", confirmationNumber = "K7XQ2P"),
            ApiFlight(4, airline = "Swiss", flightNumber = "LX 38", departureAirport = "ZRH", arrivalAirport = "SFO", departureTime = "2026-12-08T13:00:00"),
        ),
        lodgings = listOf(ApiLodging(5, name = "Hotel Schweizerhof", city = "Zürich", country = "Switzerland", checkIn = "2026-12-01T15:00:00")),
        rentalCars = listOf(ApiRentalCar(9, company = "Sixt", carModel = "VW Golf", pickupCity = "Zürich", confirmationNumber = "SX-99812")),
    )
    private val other = ApiTrip(id = 2, title = "Austin", startDate = "2026-03-01")

    private fun kinds(q: String) = TripSearch.search(q, listOf(trip, other), emptyList()).associate { (k, v) -> k to v.map { it.id } }

    @Test fun foldsAccentsAndCase() {
        assertTrue(TripSearch.matches("zurich", listOf("Zürich")))
        assertTrue(TripSearch.matches("ua837", listOf("UA 837")))
        assertFalse(TripSearch.matches("ua", listOf("Lufthansa 8")))
    }

    @Test fun groupsByKindInIosOrder() {
        val r = TripSearch.search("zurich", listOf(trip, other), emptyList())
        assertEquals(listOf(SearchHit.Kind.TRIP, SearchHit.Kind.FLIGHT, SearchHit.Kind.STAY, SearchHit.Kind.RENTAL), r.map { it.first })
        // Newer flight first.
        assertEquals(listOf("f4", "f3"), r.first { it.first == SearchHit.Kind.FLIGHT }.second.map { it.id })
    }

    @Test fun confirmationsWinOverTheBookingRow() {
        val k = kinds("k7xq")
        assertEquals(listOf("c-f3"), k[SearchHit.Kind.CONFIRMATION])
        assertEquals(null, k[SearchHit.Kind.FLIGHT])
        assertEquals(listOf("c-r9"), kinds("sx99812")[SearchHit.Kind.CONFIRMATION])
    }

    @Test fun flightNumberAndTargets() {
        val hit = TripSearch.search("UA837", listOf(trip), emptyList()).single().second.single()
        assertEquals(SearchHit.Target.Flight(3, 1), hit.target)
        assertEquals("United UA 837 · SFO → ZRH", hit.title)
        assertEquals(SearchHit.Target.Stay(5, 1), TripSearch.search("schweizerhof", listOf(trip), emptyList()).single().second.single().target)
    }

    @Test fun shortQueriesFindNothing() {
        assertTrue(TripSearch.search("a", listOf(trip), emptyList()).isEmpty())
        assertTrue(TripSearch.search("  ", listOf(trip), emptyList()).isEmpty())
    }

    @Test fun globalRentalsAreUsedOnce() {
        val r = TripSearch.search("golf", listOf(trip), listOf(trip.rentalCars.single(), trip.rentalCars.single()))
        assertEquals(1, r.single().second.size)
    }
}
