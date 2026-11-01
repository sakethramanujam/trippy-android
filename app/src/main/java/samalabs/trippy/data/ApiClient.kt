package samalabs.trippy.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.UnknownServiceException
import java.util.concurrent.TimeUnit

/** The server answered with an error; [message] is the server's `detail`, safe to show. */
class ApiException(val code: Int, message: String) : Exception(message)

/** Couldn't reach the server at all (no network, DNS, refused, timeout). */
class OfflineException(cause: Throwable? = null) :
    IOException("Can't reach your Trippy server. Check the connection and try again.", cause)

/** Session ended (refresh failed); the app returns to the welcome screen. */
class SessionExpiredException : Exception("Your session expired. Log in again.")

/** Thin OkHttp wrapper: bearer auth, one refresh-and-retry on 401, and readable errors. */
class ApiClient(
    private val sessions: TokenSource,
    private val http: OkHttpClient = defaultHttp(),
) {
    private val refreshLock = Mutex()
    var onSessionExpired: suspend () -> Unit = {}

    suspend fun get(path: String): String = send("GET", path, null)
    suspend fun post(path: String, json: String? = null, auth: Boolean = true): String = send("POST", path, json ?: "{}", auth)
    suspend fun put(path: String, json: String? = null): String = send("PUT", path, json ?: "{}")
    suspend fun patch(path: String, json: String): String = send("PATCH", path, json)
    suspend fun delete(path: String): String = send("DELETE", path, null)
    suspend fun delete(path: String, json: String): String = send("DELETE", path, json)

    /** A binary GET (photo thumbnails), with the same auth and refresh as [get]. */
    suspend fun getBytes(path: String, retried: Boolean = false): ByteArray {
        val session = sessions.current
        val token = session.accessToken
        val (code, body) = executeBytes("GET", session.server + path, null, token, accept = "*/*")
        if (code == 401 && !retried) {
            if (refresh(token)) return getBytes(path, retried = true)
            onSessionExpired()
            throw SessionExpiredException()
        }
        if (code !in 200..299) throw ApiException(code, errorMessage(code, body.decodeToString()))
        return body
    }

    suspend fun health(baseUrl: String): ServerHealth = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$baseUrl/health").header("Cache-Control", "no-cache").build()
        val quick = http.newBuilder().connectTimeout(3, TimeUnit.SECONDS).callTimeout(4, TimeUnit.SECONDS).build()
        val res = try { quick.newCall(req).execute() } catch (e: UnknownServiceException) { throw cleartextBlocked() } catch (e: IOException) { throw OfflineException(e) }
        res.use {
            if (it.code != 200) throw ApiException(it.code, "The server answered HTTP ${it.code}.")
            val h = runCatching { TrippyJson.decodeFromString<ServerHealth>(it.body.string()) }.getOrNull()
            if (h == null || !h.isTrippy) throw ApiException(0, "Something answered, but it isn't a Trippy server.")
            h
        }
    }

    private suspend fun send(method: String, path: String, json: String?, auth: Boolean = true, retried: Boolean = false): String {
        val session = sessions.current
        val token = if (auth) session.accessToken else null
        val (code, body) = execute(method, session.server + path, json, token)
        if (code == 401 && auth && !retried && path != "/auth/refresh") {
            if (refresh(token)) return send(method, path, json, auth, retried = true)
            onSessionExpired()
            throw SessionExpiredException()
        }
        if (code !in 200..299) throw ApiException(code, errorMessage(code, body))
        return body
    }

    /** Swaps the refresh token for a new access token; false when the session is gone. */
    private suspend fun refresh(staleAccess: String?): Boolean = refreshLock.withLock {
        val now = sessions.current
        if (now.accessToken != null && now.accessToken != staleAccess) return true // another call already refreshed
        val refresh = now.refreshToken ?: return false
        val payload = TrippyJson.encodeToString(RefreshRequest.serializer(), RefreshRequest(refresh))
        val (code, body) = execute("POST", now.server + "/auth/refresh", payload, null)
        if (code != 200) return false
        val auth = runCatching { TrippyJson.decodeFromString<AuthResponse>(body) }.getOrNull() ?: return false
        sessions.updateTokens(auth.accessToken, auth.refreshToken)
        true
    }

    private suspend fun execute(method: String, url: String, json: String?, token: String?): Pair<Int, String> =
        executeBytes(method, url, json, token).let { (code, body) -> code to body.decodeToString() }

    private suspend fun executeBytes(method: String, url: String, json: String?, token: String?, accept: String = "application/json"): Pair<Int, ByteArray> =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url).header("Accept", accept)
            token?.let { builder.header("Authorization", "Bearer $it") }
            val body = json?.toRequestBody(JSON)
            builder.method(method, body)
            try {
                http.newCall(builder.build()).execute().use { it.code to it.body.bytes() }
            } catch (e: UnknownServiceException) {
                throw cleartextBlocked()
            } catch (e: IOException) {
                throw OfflineException(e)
            } catch (e: IllegalArgumentException) {
                throw ApiException(0, "That server URL isn't valid.")
            }
        }

    companion object {
        private fun cleartextBlocked() = ApiException(
            0,
            "Android only allows plain http:// to local and Tailscale addresses. Use https:// for this server.",
        )

        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        /** FastAPI puts a sentence in `detail`, or a list of validation errors. */
        fun errorMessage(code: Int, body: String): String {
            val detail = runCatching { TrippyJson.parseToJsonElement(body).jsonObject["detail"] }.getOrNull()
            when (detail) {
                is JsonPrimitive -> detail.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
                is JsonArray -> {
                    val first = detail.firstOrNull() as? JsonObject
                    (first?.get("msg") as? JsonPrimitive)?.contentOrNull?.let { return it.replaceFirstChar(Char::uppercase) }
                }
                else -> Unit
            }
            return when (code) {
                401 -> "Wrong email or password."
                403 -> "You don't have access to that."
                404 -> "That isn't on the server any more."
                in 500..599 -> "The server had a problem (HTTP $code). Try again in a moment."
                else -> "The server answered HTTP $code."
            }
        }
    }
}
