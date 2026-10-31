package samalabs.trippy.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** Email-in: bookings forwarded to the server's Trippy address (backend `inbound_routes.py`). */
@Serializable
data class ApiInboundSettings(
    val enabled: Boolean = false,
    val mode: String = "off",
    val address: String? = null,
    @SerialName("sender_match") val senderMatch: Boolean = false,
    @SerialName("account_email") val accountEmail: String? = null,
    val message: String = "",
) {
    companion object {
        /** Servers without the inbound routes at all (404) behave like forwarding is off. */
        val unavailable = ApiInboundSettings(message = "Email forwarding isn't set up on this server.")
    }
}

@Serializable
data class ApiInboundItem(
    val kind: String = "",
    val id: Int? = null,
    @SerialName("trip_id") val tripId: Int? = null,
    val reason: String? = null,
    val summary: String? = null,
) {
    val label: String get() = summary ?: kind.replaceFirstChar { it.uppercase() }
    val skippedLabel: String get() = "$label — " + if (reason == "deleted") "in Recently Deleted" else reason ?: "skipped"
}

@Serializable
data class ApiInboundPart(val part: String = "", val found: Int = 0, val note: String? = null) {
    val label: String get() = "$part: $found found" + (note?.let { " ($it)" } ?: "")
}

@Serializable
data class ApiInboundEmail(
    val id: Int,
    val subject: String? = null,
    val sender: String? = null,
    @SerialName("received_at") val receivedAt: String? = null,
    val status: String = "pending",
    @SerialName("routed_by") val routedBy: String? = null,
    val error: String? = null,
    val seen: Boolean = true,
    val attempts: Int = 0,
    @SerialName("can_retry") val canRetry: Boolean = false,
    val created: List<ApiInboundItem> = emptyList(),
    val skipped: List<ApiInboundItem> = emptyList(),
    @SerialName("new_trip_ids") val newTripIds: List<Int> = emptyList(),
    val parts: List<ApiInboundPart> = emptyList(),
) {
    val statusText: String
        get() = when (status) {
            "imported" -> "Added ${created.size} item" + if (created.size == 1) "" else "s"
            "duplicate" -> "Already imported"
            "failed" -> "Couldn't import"
            "nothing_found" -> "No bookings found"
            "pending" -> "Waiting"
            else -> status.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }

    /** received_at is UTC; naive timestamps are read as UTC too. */
    val receivedInstant: Instant?
        get() = receivedAt?.let { raw ->
            runCatching { Instant.parse(raw) }.getOrNull()
                ?: runCatching { java.time.OffsetDateTime.parse(raw).toInstant() }.getOrNull()
                ?: runCatching { LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC) }.getOrNull()
        }
}

@Serializable
data class ApiInboundUnread(val unread: Int = 0, val failed: Int = 0)

class InboundRepository(private val api: ApiClient) {
    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()
    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    suspend fun settings(regenerate: Boolean = false): ApiInboundSettings {
        val s = try {
            val raw = if (regenerate) api.post("/inbound/address/regenerate") else api.get("/inbound/settings")
            TrippyJson.decodeFromString(ApiInboundSettings.serializer(), raw)
        } catch (e: ApiException) {
            if (e.code == 404) ApiInboundSettings.unavailable else throw e
        }
        _enabled.value = s.enabled
        return s
    }

    suspend fun emails(): List<ApiInboundEmail> =
        TrippyJson.decodeFromString(ListSerializer(ApiInboundEmail.serializer()), api.get("/inbound/emails"))

    suspend fun refreshBadge() {
        runCatching { TrippyJson.decodeFromString(ApiInboundUnread.serializer(), api.get("/inbound/emails/unread")) }
            .onSuccess { _unread.value = it.unread }
    }

    suspend fun markSeen() {
        runCatching { api.post("/inbound/emails/seen") }
        _unread.value = 0
    }

    suspend fun retry(id: Int): ApiInboundEmail =
        TrippyJson.decodeFromString(ApiInboundEmail.serializer(), api.post("/inbound/emails/$id/retry"))

    suspend fun delete(id: Int) { api.delete("/inbound/emails/$id") }

    fun clear() { _unread.value = 0; _enabled.value = true }
}
