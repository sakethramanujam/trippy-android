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

class LocalNetworkTest {
    @Test fun privateAddressesNeedPermission() {
        org.junit.Assert.assertTrue(LocalNetwork.needsPermission("http://192.168.50.14:8840"))
        org.junit.Assert.assertTrue(LocalNetwork.needsPermission("http://10.0.2.2:8000"))
        org.junit.Assert.assertTrue(LocalNetwork.needsPermission("http://172.20.1.5"))
        org.junit.Assert.assertTrue(LocalNetwork.needsPermission("http://nas.local:8000"))
    }

    @Test fun tailscaleAndPublicDont() {
        org.junit.Assert.assertFalse(LocalNetwork.needsPermission("http://100.67.213.114:8840"))
        org.junit.Assert.assertFalse(LocalNetwork.needsPermission("https://trippy.example.com"))
        org.junit.Assert.assertFalse(LocalNetwork.needsPermission("http://127.0.0.1:8000"))
        org.junit.Assert.assertFalse(LocalNetwork.needsPermission("http://172.32.0.1"))
    }
}
