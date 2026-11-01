package samalabs.trippy.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import samalabs.trippy.data.ApiFlight
import samalabs.trippy.data.ApiLodging
import samalabs.trippy.ui.screens.StayPhase
import samalabs.trippy.ui.screens.flightFacts
import samalabs.trippy.ui.screens.phase
import samalabs.trippy.ui.screens.phonesIn
import samalabs.trippy.ui.screens.stayFacts
import samalabs.trippy.ui.screens.stayStatusTitle
import java.time.LocalDateTime

class BookingDetailTest {
    private val stay = ApiLodging(id = 1, name = "Hotel", checkIn = "2026-06-11T15:00:00", checkOut = "2026-06-16T11:00:00",
        confirmationNumber = "ABC123", address = "1 Main St", notes = "Call +1 (512) 555-0134 for parking", cost = 690.0, currency = "USD")

    @Test fun stayPhasesAndTitles() {
        assertEquals(StayPhase.UPCOMING, stay.phase(LocalDateTime.of(2026, 6, 1, 9, 0)))
        assertEquals("Check in in 10 days", stayStatusTitle(stay, LocalDateTime.of(2026, 6, 1, 9, 0)))
        assertEquals("Check in tomorrow", stayStatusTitle(stay, LocalDateTime.of(2026, 6, 10, 9, 0)))
        assertEquals("4 nights left", stayStatusTitle(stay, LocalDateTime.of(2026, 6, 12, 9, 0)))
        assertEquals("Check out today", stayStatusTitle(stay, LocalDateTime.of(2026, 6, 16, 9, 0)))
        assertEquals("Stayed", stayStatusTitle(stay, LocalDateTime.of(2026, 6, 17, 9, 0)))
        assertEquals("Cancelled", stayStatusTitle(stay.copy(status = "cancelled"), LocalDateTime.of(2026, 6, 12, 9, 0)))
    }

    @Test fun stayFactsIncludePhonesFromNotes() {
        assertEquals(listOf("+1 (512) 555-0134"), phonesIn(stay.notes!!))
        assertEquals(listOf("Confirmation", "Address", "Phone", "Total", "Notes"), stayFacts(stay).map { it.label })
    }

    @Test fun flightFactsOrder() {
        val f = ApiFlight(id = 1, flightNumber = "UA 837", confirmationNumber = "XYZ", seat = "12A", cabinClass = "economy", durationMinutes = 125, cost = 300.0)
        assertEquals(listOf("Confirmation", "Seat", "Cabin", "Duration", "Fare", "Flight"), flightFacts(f).map { it.label })
    }

    @Test fun flightFactsWithTerminalGateAndNotes() {
        val f = ApiFlight(
            id = 1,
            flightNumber = "UA 837",
            confirmationNumber = "XYZ",
            terminal = "3",
            gate = "F12",
            seat = "12A",
            cabinClass = "economy",
            durationMinutes = 125,
            cost = 300.0,
            notes = "Terminal change possible",
        )
        assertEquals(
            listOf("Confirmation", "Terminal", "Gate", "Seat", "Cabin", "Duration", "Fare", "Flight", "Notes"),
            flightFacts(f).map { it.label }
        )
    }
}
