package samalabs.trippy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import samalabs.trippy.data.ApiFlight
import java.time.LocalDateTime
import java.time.ZoneId

class AirlineCheckInTest {
    private val ua = ApiFlight(1, airline = "United", flightNumber = "UA 837", departureAirport = "SFO", arrivalAirport = "NRT",
        departureTime = "2026-11-10T11:00:00", arrivalTime = "2026-11-11T15:00:00", confirmationNumber = "K7XQ2P", seat = "31C")

    @Test fun checkInTableAndFallback() {
        val i = AirlineCheckIn.info(ua)
        assertTrue(i.known); assertEquals(24, i.opensHours); assertEquals("https://www.united.com/en/us/checkin", i.url)
        assertEquals(30, AirlineCheckIn.info(ua.copy(flightNumber = "KL0605")).opensHours)
        val unknown = AirlineCheckIn.info(ua.copy(flightNumber = "ZZ 1", airline = "Zed Air"))
        assertFalse(unknown.known)
        assertEquals("https://www.google.com/search?q=Zed+Air+online+check-in", unknown.url)
        assertEquals("Search for Zed Air online check-in", AirlineCheckIn.subtitle(ua.copy(flightNumber = "ZZ 1", airline = "Zed Air")))
    }

    @Test fun checkInCountdownWindows() {
        val dep = LocalDateTime.of(2026, 11, 10, 11, 0)
        assertNull(AirlineCheckIn.countdown(ua, dep.minusDays(9)))
        val c = AirlineCheckIn.countdown(ua, dep.minusHours(30))!!
        assertFalse(c.open); assertTrue(c.text, c.text.startsWith("Check-in opens in 6h"))
        assertEquals(AirlineCheckIn.Countdown("Check-in is open", true), AirlineCheckIn.countdown(ua, dep.minusHours(5)))
        assertNull(AirlineCheckIn.countdown(ua, dep.minusMinutes(30)))
    }
}
