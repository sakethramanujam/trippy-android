package samalabs.trippy.data

import java.net.URI

/**
 * Android 17 blocks connections to the local network (RFC 1918, link-local, .local) for apps
 * targeting API 37 unless the user grants ACCESS_LOCAL_NETWORK. Tailscale's 100.64.0.0/10,
 * loopback, the emulator's host alias and public hosts don't need it.
 */
object LocalNetwork {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    const val MIN_SDK = 37

    fun needsPermission(serverUrl: String): Boolean {
        val host = runCatching { URI(serverUrl).host }.getOrNull()?.lowercase() ?: return false
        if (host.endsWith(".local")) return true
        val parts = host.split(".").map { it.toIntOrNull() ?: return false }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        val (a, b) = parts
        return when {
            a == 10 -> true
            a == 172 && b in 16..31 -> true
            a == 192 && b == 168 -> true
            a == 169 && b == 254 -> true
            else -> false
        }
    }
}
