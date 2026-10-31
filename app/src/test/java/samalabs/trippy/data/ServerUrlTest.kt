package samalabs.trippy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {
    @Test fun addsHttpAndTrimsSlash() {
        assertEquals("http://192.168.1.20:8000", ServerUrl.normalize(" 192.168.1.20:8000/ "))
    }

    @Test fun keepsHttps() {
        assertEquals("https://trippy.example.com", ServerUrl.normalize("https://trippy.example.com/"))
    }

    @Test fun rejectsJunk() {
        assertNull(ServerUrl.normalize(""))
        assertNull(ServerUrl.normalize("ftp://x"))
        assertNull(ServerUrl.normalize("http://"))
    }

    @Test fun displayHostDropsScheme() {
        assertEquals("10.0.2.2:8000", ServerUrl.displayHost("http://10.0.2.2:8000"))
    }
}
