package samalabs.trippy.domain

import kotlinx.serialization.Serializable
import samalabs.trippy.data.ApiTrip
import samalabs.trippy.data.TrippyJson
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** What the home-screen widget shows; written by the app whenever trips change (iOS WidgetSnapshot). */
@Serializable
data class WidgetSnapshot(val nextTrip: TripLine? = null, val nextFlight: FlightLine? = null, val written: String? = null) {
    @Serializable data class TripLine(val title: String, val start: String, val end: String? = null)
    @Serializable data class FlightLine(
        val number: String, val airline: String? = null, val from: String, val to: String,
        val departs: String, val arrives: String? = null, val confirmation: String? = null,
    )

    fun encode(): String = TrippyJson.encodeToString(serializer(), this)

    /** Days until the trip starts, 0 when it's under way. */
    fun daysToTrip(today: LocalDate): Int? = nextTrip?.let { Fmt.parse(it.start)?.toLocalDate() }?.let { ChronoUnit.DAYS.between(today, it).toInt().coerceAtLeast(0) }

    companion object {
        fun decode(s: String?): WidgetSnapshot = s?.let { runCatching { TrippyJson.decodeFromString(serializer(), it) }.getOrNull() } ?: WidgetSnapshot()

        fun make(trips: List<ApiTrip>, now: LocalDateTime): WidgetSnapshot {
            val today = now.toLocalDate().atStartOfDay()
            val trip = trips.mapNotNull { t ->
                val s = Fmt.parse(t.startDate) ?: return@mapNotNull null
                val e = Fmt.parse(t.endDate) ?: s
                if (!e.isBefore(today)) t to s else null
            }.minByOrNull { it.second }
            val flight = trips.flatMap { it.flights }
                .filter { (it.arrivalDate ?: it.departureDate)?.isAfter(now) == true }
                .minByOrNull { it.departureDate ?: LocalDateTime.MAX }
            return WidgetSnapshot(
                nextTrip = trip?.let { (t, s) -> TripLine(t.displayTitle, s.toString(), Fmt.parse(t.endDate)?.toString()) },
                nextFlight = flight?.departureDate?.let { d ->
                    FlightLine(flight.number, flight.airline, flight.fromCode, flight.toCode, d.toString(), flight.arrivalDate?.toString(), flight.confirmationNumber)
                },
                written = now.toString(),
            )
        }
    }
}
