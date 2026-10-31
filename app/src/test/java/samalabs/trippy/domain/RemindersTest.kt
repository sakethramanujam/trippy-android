package samalabs.trippy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import samalabs.trippy.data.ApiFlight
import samalabs.trippy.data.ApiLodging
import samalabs.trippy.data.ApiTrip
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class RemindersTest {
    private val la = ZoneId.of("America/Los_Angeles")
    private val now = ZonedDateTime.of(2026, 11, 1, 8, 0, 0, 0, la).toInstant()

    private fun trip(vararg f: ApiFlight, stays: List<ApiLodging> = emptyList()) = ApiTrip(1, "T", flights = f.toList(), lodgings = stays)

    @Test fun flightGetsCheckInAndThreeHourReminders() {
        val f = ApiFlight(7, flightNumber = "UA 837", departureAirport = "SFO", arrivalAirport = "NRT", departureTime = "2026-11-05T11:00:00")
        val p = Reminders.plan(listOf(trip(f)), now, la)
        assertEquals(listOf("trippy.checkin.7", "trippy.depart.7"), p.map { it.id })
        assertEquals("Check in for UA 837", p[0].title)
        assertEquals(ZonedDateTime.of(2026, 11, 4, 11, 0, 0, 0, la).toInstant(), p[0].at)
        assertEquals("UA 837 departs in 3 hours", p[1].title)
        assertEquals(ZonedDateTime.of(2026, 11, 5, 8, 0, 0, 0, la).toInstant(), p[1].at)
        assertTrue(p[1].body.startsWith("SFO → NRT at 11:00"))
    }

    @Test fun departureTimeIsReadAtTheAirport() {
        // 09:00 in Tokyo is 16:00 the day before in Los Angeles (PST).
        val f = ApiFlight(8, flightNumber = "NH 7", departureAirport = "NRT", arrivalAirport = "SFO", departureTime = "2026-11-10T09:00:00")
        val dep = Reminders.plan(listOf(trip(f)), now, la).first { it.id == "trippy.depart.8" }
        assertEquals(ZonedDateTime.of(2026, 11, 10, 9, 0, 0, 0, ZoneId.of("Asia/Tokyo")).minusHours(3).toInstant(), dep.at)
    }

    @Test fun skipsPastAndBeyondTwoWeeks() {
        val past = ApiFlight(1, flightNumber = "UA 1", departureAirport = "SFO", departureTime = "2026-10-30T09:00:00")
        val far = ApiFlight(2, flightNumber = "UA 2", departureAirport = "SFO", departureTime = "2026-12-30T09:00:00")
        assertTrue(Reminders.plan(listOf(trip(past, far)), now, la).isEmpty())
    }

    @Test fun stayMorningReminder() {
        val s = ApiLodging(3, name = "Ace Hotel", address = "1 Main St", checkIn = "2026-11-03T15:00:00")
        val p = Reminders.plan(listOf(trip(stays = listOf(s))), now, la).single()
        assertEquals("Check in at Ace Hotel today", p.title)
        assertEquals("1 Main St", p.body)
        assertEquals(ZonedDateTime.of(2026, 11, 3, 9, 0, 0, 0, la).toInstant(), p.at)
    }

    @Test fun earlyCheckInBeforeNineGetsNoMorningReminder() {
        val s = ApiLodging(4, name = "Red-eye Inn", checkIn = "2026-11-03T06:00:00")
        assertTrue(Reminders.plan(listOf(trip(stays = listOf(s))), now, la).isEmpty())
    }

    @Test fun sortedAndCapped() {
        val flights = (1..40).map { ApiFlight(it, flightNumber = "UA $it", departureAirport = "SFO", departureTime = "2026-11-%02dT10:00:00".format(2 + it % 12)) }
        val p = Reminders.plan(listOf(trip(*flights.toTypedArray())), now, la)
        assertEquals(60, p.size)
        assertEquals(p.sortedBy { it.at }, p)
        assertTrue(p.all { it.id.startsWith("trippy.") && it.at.isAfter(Instant.EPOCH) })
    }
}
