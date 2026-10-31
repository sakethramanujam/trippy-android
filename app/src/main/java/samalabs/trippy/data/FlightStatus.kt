package samalabs.trippy.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** GET /flights/{id}/status. The server holds the provider key; off unless the admin adds one. */
@Serializable
data class LiveStatus(
    val configured: Boolean = false,
    val provider: String? = null,
    val status: Status? = null,
    val message: String? = null,
) {
    @Serializable
    data class Status(
        val provider: String = "",
        val status: String = "unknown",
        @SerialName("departure_scheduled") val departureScheduled: String? = null,
        @SerialName("departure_estimated") val departureEstimated: String? = null,
        @SerialName("departure_actual") val departureActual: String? = null,
        @SerialName("departure_terminal") val departureTerminal: String? = null,
        @SerialName("departure_gate") val departureGate: String? = null,
        @SerialName("arrival_scheduled") val arrivalScheduled: String? = null,
        @SerialName("arrival_estimated") val arrivalEstimated: String? = null,
        @SerialName("arrival_actual") val arrivalActual: String? = null,
        @SerialName("arrival_terminal") val arrivalTerminal: String? = null,
        @SerialName("arrival_gate") val arrivalGate: String? = null,
        @SerialName("baggage_claim") val baggageClaim: String? = null,
        @SerialName("delay_minutes") val delayMinutes: Int? = null,
        @SerialName("fetched_at") val fetchedAt: String? = null,
    ) {
        val label: String
            get() {
                val base = when (status) {
                    "scheduled" -> "On schedule"; "delayed" -> "Delayed"; "departed" -> "Departed"; "en_route" -> "In the air"
                    "landed" -> "Landed"; "cancelled" -> "Cancelled"; "diverted" -> "Diverted"; else -> "Status unknown"
                }
                val d = delayMinutes ?: 0
                return if (d > 0 && status != "cancelled") "$base · ${d}m late" else base
            }

        val tone: Tone
            get() = when (status) {
                "cancelled", "diverted" -> Tone.RED
                "delayed" -> Tone.AMBER
                "landed" -> Tone.GRAY
                else -> if ((delayMinutes ?: 0) > 15) Tone.AMBER else Tone.GREEN
            }

        val departureGateText: String get() = gate(departureTerminal, departureGate)
        val arrivalGateText: String get() = gate(arrivalTerminal, arrivalGate)
        val expectedDeparture: String? get() = localClock(departureActual ?: departureEstimated ?: departureScheduled)
        val expectedArrival: String? get() = localClock(arrivalActual ?: arrivalEstimated ?: arrivalScheduled)
        val fetchedInstant: Instant? get() = fetchedAt?.let { s -> runCatching { Instant.parse(if (s.endsWith("Z") || '+' in s.drop(10)) s else s + "Z") }.getOrNull() }
        val providerName: String get() = providerName(provider)
    }

    enum class Tone { GREEN, AMBER, RED, GRAY }

    companion object {
        /** "1" -> "T1"; "I" -> "Terminal I". */
        fun terminal(t: String): String = if (t.isNotEmpty() && t.all(Char::isDigit)) "T$t" else "Terminal $t"
        private fun gate(t: String?, g: String?) = listOfNotNull(t?.takeIf { it.isNotBlank() }?.let(::terminal), g?.takeIf { it.isNotBlank() }).joinToString(" · ")

        /** Provider times are airport-local with an offset ("2026-11-01 11:45-07:00"): show the clock time there. */
        fun localClock(raw: String?): String? {
            val s = raw?.trim()?.replace(' ', 'T') ?: return null
            val fmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
            runCatching { return OffsetDateTime.parse(s).toLocalDateTime().format(fmt) }
            runCatching { return OffsetDateTime.parse(s.replace(Regex("([+-]\\d{2})(\\d{2})$"), "$1:$2")).toLocalDateTime().format(fmt) }
            return runCatching { LocalDateTime.parse(s.removeSuffix("Z")).format(fmt) }.getOrNull()
        }

        fun providerName(p: String?): String = when (p) {
            "aerodatabox" -> "AeroDataBox"; "aviationstack" -> "aviationstack"; "aeroapi" -> "FlightAware AeroAPI"; else -> p.orEmpty()
        }
    }
}

suspend fun ApiClient.liveStatus(flightId: Int): LiveStatus =
    TrippyJson.decodeFromString(LiveStatus.serializer(), get("/flights/$flightId/status"))
