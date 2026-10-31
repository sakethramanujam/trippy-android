package samalabs.trippy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LiveStatusTest {
    private fun decode(s: String) = TrippyJson.decodeFromString(LiveStatus.serializer(), s)

    @Test fun notConfigured() {
        val r = decode("""{"configured": false, "provider": null, "status": null, "message": "No flight data key"}""")
        assertFalse(r.configured); assertNull(r.status); assertEquals("No flight data key", r.message)
    }

    @Test fun fullStatus() {
        val r = decode("""{"configured": true, "provider": "aerodatabox", "status": {"provider": "aerodatabox", "status": "delayed",
            "departure_scheduled": "2026-11-01 11:00-07:00", "departure_estimated": "2026-11-01 11:45-07:00", "departure_terminal": "3",
            "departure_gate": "G92", "arrival_terminal": "I", "arrival_scheduled": "2026-11-02 15:05+09:00", "baggage_claim": "7",
            "delay_minutes": 45, "fetched_at": "2026-11-01T17:30:00", "extra": 1}}""")
        val s = r.status!!
        assertEquals("Delayed · 45m late", s.label)
        assertEquals(LiveStatus.Tone.AMBER, s.tone)
        assertEquals("T3 · G92", s.departureGateText)
        assertEquals("Terminal I", s.arrivalGateText)
        assertEquals("11:45 AM", s.expectedDeparture)
        assertEquals("3:05 PM", s.expectedArrival)
        assertEquals("AeroDataBox", s.providerName)
        assertNotNull(s.fetchedInstant)
    }

    @Test fun labelsAndTones() {
        fun st(status: String, delay: Int? = null) = LiveStatus.Status(status = status, delayMinutes = delay)
        assertEquals("In the air", st("en_route").label)
        assertEquals("Cancelled", st("cancelled", 30).label)
        assertEquals(LiveStatus.Tone.RED, st("diverted").tone)
        assertEquals(LiveStatus.Tone.GRAY, st("landed").tone)
        assertEquals(LiveStatus.Tone.AMBER, st("scheduled", 20).tone)
        assertEquals(LiveStatus.Tone.GREEN, st("scheduled", 10).tone)
        assertEquals("Status unknown", st("weird").label)
    }
}
