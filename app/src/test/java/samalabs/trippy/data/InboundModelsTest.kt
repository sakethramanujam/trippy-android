package samalabs.trippy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class InboundModelsTest {
    @Test fun decodesEmailAndStatus() {
        val e = TrippyJson.decodeFromString(ApiInboundEmail.serializer(), """
            {"id":4,"subject":"Your flight","status":"imported","seen":false,"attempts":1,"can_retry":false,
             "received_at":"2026-10-30T18:00:00Z","created":[{"kind":"flight","summary":"UA 1 SFO-LHR"}],
             "skipped":[{"kind":"stay","reason":"deleted"}],"new_trip_ids":[2],"parts":[{"part":"body","found":1}]}
        """.trimIndent())
        assertEquals("Added 1 item", e.statusText)
        assertEquals("Stay — in Recently Deleted", e.skipped[0].skippedLabel)
        assertEquals("body: 1 found", e.parts[0].label)
        assertNotNull(e.receivedInstant)
        assertEquals(e.receivedInstant, e.copy(receivedAt = "2026-10-30T18:00:00").receivedInstant)
    }

    @Test fun statusCopy() {
        val base = ApiInboundEmail(id = 1)
        assertEquals("Waiting", base.statusText)
        assertEquals("Couldn't import", base.copy(status = "failed").statusText)
        assertEquals("No bookings found", base.copy(status = "nothing_found").statusText)
        assertEquals("Already imported", base.copy(status = "duplicate").statusText)
        assertEquals("Added 2 items", base.copy(status = "imported", created = listOf(ApiInboundItem("a"), ApiInboundItem("b"))).statusText)
    }

    @Test fun disabledSettings() {
        val s = TrippyJson.decodeFromString(ApiInboundSettings.serializer(), """{"enabled":false,"mode":"off","sender_match":false,"message":"Off here."}""")
        assertFalse(s.enabled)
        assertFalse(ApiInboundSettings.unavailable.enabled)
    }
}
