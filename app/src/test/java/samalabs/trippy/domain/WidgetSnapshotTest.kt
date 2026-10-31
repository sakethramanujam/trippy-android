package samalabs.trippy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import samalabs.trippy.data.ApiFlight
import samalabs.trippy.data.ApiTrip
import java.time.LocalDate
import java.time.LocalDateTime

class WidgetSnapshotTest {
    private val now = LocalDateTime.of(2026, 11, 1, 10, 0)

    @Test fun picksNextTripAndFlight() {
        val trips = listOf(
            ApiTrip(1, "Past", startDate = "2026-10-01", endDate = "2026-10-05"),
            ApiTrip(2, "Tokyo", startDate = "2026-11-10", endDate = "2026-11-17", flights = listOf(
                ApiFlight(5, airline = "ANA", flightNumber = "NH 7", departureAirport = "sfo", arrivalAirport = "NRT",
                    departureTime = "2026-11-10T11:00:00", arrivalTime = "2026-11-11T15:00:00", confirmationNumber = "K7XQ"),
            )),
            ApiTrip(3, "Austin", startDate = "2026-10-30", endDate = "2026-11-02", flights = listOf(
                ApiFlight(6, flightNumber = "WN 1", departureAirport = "AUS", arrivalAirport = "SJC", departureTime = "2026-11-01T08:00:00", arrivalTime = "2026-11-01T09:30:00"),
            )),
        )
        val s = WidgetSnapshot.make(trips, now)
        assertEquals("Austin", s.nextTrip?.title)
        assertEquals(0, s.daysToTrip(now.toLocalDate()))
        assertEquals("NH 7", s.nextFlight?.number)
        assertEquals("SFO", s.nextFlight?.from)
        assertEquals("K7XQ", s.nextFlight?.confirmation)
        assertEquals(s, WidgetSnapshot.decode(s.encode()))
    }

    @Test fun daysCountdown() {
        val s = WidgetSnapshot.make(listOf(ApiTrip(2, "Tokyo", startDate = "2026-11-10")), now)
        assertEquals(9, s.daysToTrip(LocalDate.of(2026, 11, 1)))
    }

    @Test fun emptyAndGarbage() {
        val s = WidgetSnapshot.make(emptyList(), now)
        assertNull(s.nextTrip); assertNull(s.nextFlight)
        assertEquals(WidgetSnapshot(), WidgetSnapshot.decode("not json"))
    }
}
