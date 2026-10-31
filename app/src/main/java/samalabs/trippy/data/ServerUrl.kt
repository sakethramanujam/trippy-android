package samalabs.trippy.data

import java.net.URI

object ServerUrl {
    /**
     * Accepts "host:port", "https://host/" and similar; adds http:// when the scheme is missing
     * and drops trailing slashes. Returns null for anything that isn't an http(s) URL with a host.
     */
    fun normalize(input: String): String? {
        var s = input.trim()
        if (s.isEmpty()) return null
        if (!s.contains("://")) s = "http://$s"
        val uri = runCatching { URI(s) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null
        return s.trimEnd('/')
    }

    /** "https://trippy.example.com" -> "trippy.example.com", for compact rows. */
    fun displayHost(url: String): String = url.removePrefix("https://").removePrefix("http://")
}
