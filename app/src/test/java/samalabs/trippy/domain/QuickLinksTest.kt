package samalabs.trippy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import samalabs.trippy.data.ApiFlight
import samalabs.trippy.data.ApiLodging
import java.time.LocalDateTime
import java.time.ZoneId

class QuickLinksTest {
    private val ua = ApiFlight(1, airline = "United", flightNumber = "UA 837", departureAirport = "SFO", arrivalAirport = "NRT",
        departureTime = "2026-11-10T11:00:00", arrivalTime = "2026-11-11T15:00:00", confirmationNumber = "K7XQ2P", seat = "31C")

    @Test fun mapsAndDirectionsAreGoogleMapsUrls() {
        assertEquals("https://www.google.com/maps/search/?api=1&query=1+Main+St%2C+Austin", Links.maps("1 Main St, Austin"))
        assertTrue(Links.directions(Links.airportQuery("SFO")).startsWith("https://www.google.com/maps/dir/?api=1&destination=SFO+Airport"))
        assertEquals("XXX Airport", Links.airportQuery("XXX"))
    }

    @Test fun phoneKeepsDigitsAndPlus() {
        assertEquals("tel:+15125550100", Links.phone("+1 (512) 555-0100"))
        assertNull(Links.phone("ext 12"))
    }

    @Test fun flightCalendarDraftMatchesIos() {
        val d = CalendarDraft.flight(ua)!!
        assertEquals("✈︎ UA 837 SFO → NRT", d.title)
        assertEquals(ZoneId.of("America/Los_Angeles"), d.zone)
        assertTrue(d.location!!.startsWith("SFO Airport"))
        assertEquals("United UA 837\nConfirmation: K7XQ2P\nSeat: 31C", d.notes)
        // 15:00 in Tokyo is 23:00 the day before in San Francisco: an 11h flight, not 28h.
        assertEquals(11 * 3_600_000L, d.endMillis() - d.startMillis())
    }

    @Test fun flightWithoutArrivalLastsTwoHours() {
        val d = CalendarDraft.flight(ua.copy(arrivalTime = null))!!
        assertEquals(2 * 3_600_000L, d.endMillis() - d.startMillis())
    }

    @Test fun eventIsAtLeastFifteenMinutes() {
        val t = LocalDateTime.of(2026, 1, 1, 9, 0)
        assertEquals(15 * 60_000L, CalendarDraft("x", t, t).let { it.endMillis() - it.startMillis() })
    }

    @Test fun stayDraftUsesCheckInAndOut() {
        val h = ApiLodging(id = 2, name = "Hotel Ryokan", address = "1-2 Asakusa", checkIn = "2026-11-11T15:00:00", checkOut = "2026-11-14T11:00:00", confirmationNumber = "HR1")
        val d = CalendarDraft.stay(h)!!
        assertTrue(d.title.endsWith("Hotel Ryokan"))
        assertEquals("1-2 Asakusa", d.location)
        assertEquals(LocalDateTime.of(2026, 11, 14, 11, 0), d.end)
    }
}
