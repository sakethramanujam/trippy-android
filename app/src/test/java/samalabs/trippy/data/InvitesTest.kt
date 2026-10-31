package samalabs.trippy.data

import org.junit.Assert.assertEquals
import org.junit.Test

class InvitesTest {
    private val json = """{"code":"ABCDE-FGHIJ","expires_at":"2026-11-14T10:00:00","trip_id":3,"user_id":9,"name":"Val"}"""

    @Test fun decodesAndFormatsExpiry() {
        val inv = TrippyJson.decodeFromString(ApiInviteCode.serializer(), json)
        assertEquals("ABCDE-FGHIJ", inv.code)
        assertEquals("Nov 14", inv.expiry)
    }

    @Test fun shareTextMatchesIos() {
        val inv = TrippyJson.decodeFromString(ApiInviteCode.serializer(), json)
        assertEquals("Join me on Lisbon in Trippy. Server: https://t.example\nInvite code: ABCDE-FGHIJ (single use, expires Nov 14)",
            InviteText.share("Lisbon", "https://t.example", inv))
    }

    @Test fun joinedCopy() {
        assertEquals("You've joined the trip.", InviteText.joined(1))
        assertEquals("You've joined 3 trips.", InviteText.joined(3))
    }
}
