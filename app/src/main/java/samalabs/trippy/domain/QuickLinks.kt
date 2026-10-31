package samalabs.trippy.domain

import samalabs.trippy.data.ApiFlight
import samalabs.trippy.data.ApiLodging
import samalabs.trippy.data.ApiRentalCar
import java.net.URLEncoder
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/** Maps, directions and phone links. Google Maps URLs open the Maps app when it's installed, else the browser. */
object Links {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun maps(query: String) = "https://www.google.com/maps/search/?api=1&query=${enc(query)}"
    fun directions(destination: String) = "https://www.google.com/maps/dir/?api=1&destination=${enc(destination)}&travelmode=driving"
    fun airportQuery(code: String): String = Airports.city(code)?.let { "$code Airport, $it" } ?: "$code Airport"

    /** tel: link, or null when there aren't enough digits to dial. */
    fun phone(text: String): String? {
        val digits = text.filter { it.isDigit() || it == '+' }
        return if (digits.count(Char::isDigit) >= 6) "tel:$digits" else null
    }
}

/** What "Add to Calendar" pre-fills. Times are wall-clock at [zone]. */
data class CalendarDraft(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val location: String? = null,
    val notes: String? = null,
    val zone: ZoneId? = null,
) {
    fun startMillis(): Long = start.atZone(zone ?: ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun endMillis(endZone: ZoneId? = zone): Long =
        maxOf(end.atZone(endZone ?: zone ?: ZoneId.systemDefault()).toInstant().toEpochMilli(), startMillis() + 15 * 60_000)

    companion object {
        fun flight(f: ApiFlight): CalendarDraft? {
            val dep = f.departureDate ?: return null
            val notes = buildList {
                add(listOfNotNull(f.airline, f.flightNumber).joinToString(" "))
                f.confirmationNumber?.takeIf { it.isNotBlank() }?.let { add("Confirmation: $it") }
                f.seat?.takeIf { it.isNotBlank() }?.let { add("Seat: $it") }
            }
            return CalendarDraft(
                title = "✈︎ ${f.number} ${f.fromCode} → ${f.toCode}",
                start = dep, end = f.arrivalDate?.let { convert(it, Airports.zone(f.arrivalAirport), Airports.zone(f.departureAirport)) } ?: dep.plusHours(2),
                location = Links.airportQuery(f.fromCode), notes = notes.joinToString("\n"), zone = Airports.zone(f.departureAirport),
            )
        }

        fun stay(h: ApiLodging): CalendarDraft? {
            val a = h.checkInDate ?: return null
            val zone = h.checkInTz?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            val notes = listOfNotNull(h.confirmationNumber?.takeIf { it.isNotBlank() }?.let { "Confirmation: $it" }, h.notes?.takeIf { it.isNotBlank() })
            return CalendarDraft("🛏 ${h.displayName}", a, h.checkOutDate ?: a.plusDays(1), h.address ?: h.place, notes.joinToString("\n").ifEmpty { null }, zone)
        }

        fun rental(r: ApiRentalCar): CalendarDraft? {
            val p = r.pickupDate ?: return null
            val zone = Airports.zone(r.pickupCode)
            val notes = listOfNotNull(r.confirmationText?.let { "Confirmation: $it" }, r.vehicleName.takeIf { it.isNotBlank() })
            return CalendarDraft("🚗 ${r.companyName} pickup", p, p.plusMinutes(30), r.pickupLocation ?: r.pickupDisplayCity, notes.joinToString("\n").ifEmpty { null }, zone)
        }

        /** Arrival is wall-clock at the arrival airport; re-express it at the departure zone for a single-zone event. */
        private fun convert(t: LocalDateTime, from: ZoneId?, to: ZoneId?): LocalDateTime =
            if (from == null || to == null) t else t.atZone(from).withZoneSameInstant(to).toLocalDateTime()
    }
}
