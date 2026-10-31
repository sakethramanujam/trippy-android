package samalabs.trippy.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileRequestsTest {
    @Test fun profileUpdateUsesSnakeCase() {
        assertEquals("""{"name":"Val","home_airport":"SFO"}""", TrippyJson.encodeToString(ProfileUpdate.serializer(), ProfileUpdate("Val", "SFO")))
    }
    @Test fun passwordChangeUsesBackendKeys() {
        val s = TrippyJson.encodeToString(PasswordChange.serializer(), PasswordChange("a", "b"))
        assertEquals("""{"current_password":"a","new_password":"b"}""", s)
    }
}
