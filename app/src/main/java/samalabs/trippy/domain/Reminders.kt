package samalabs.trippy.domain

import samalabs.trippy.data.ApiTrip
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Local reminders, same plan as iOS TripReminders: check-in opens, 3h before departure, and hotel check-in morning. */
object Reminders {
    data class Planned(val id: String, val title: String, val body: String, val at: Instant)

    /** Times are wall-clock at the airport (or hotel); read them there so a reminder fires at the right moment wherever the phone is. */
    fun plan(trips: List<ApiTrip>, now: Instant, device: ZoneId = ZoneId.systemDefault()): List<Planned> {
        val horizon = now.plus(Duration.ofDays(14))
        val out = mutableListOf<Planned>()
        fun at(t: LocalDateTime, zone: ZoneId?) = t.atZone(zone ?: device).toInstant()
        for (f in trips.flatMap { it.flights }) {
            val depLocal = f.departureDate ?: continue
            val zone = Airports.zone(f.departureAirport)
            val dep = at(depLocal, zone)
            if (!dep.isAfter(now) || !dep.isBefore(horizon)) continue
            val route = "${f.fromCode} → ${f.toCode}"
            AirlineCheckIn.opensAt(f)?.let { at(it, zone) }?.takeIf { it.isAfter(now) }?.let {
                out += Planned("trippy.checkin.${f.id}", "Check in for ${f.number}", "Online check-in usually opens now for $route.", it)
            }
            val soon = dep.minus(Duration.ofHours(3))
            if (soon.isAfter(now)) {
                out += Planned("trippy.depart.${f.id}", "${f.number} departs in 3 hours",
                    "$route at ${Fmt.time(depLocal)}. Directions to ${f.fromCode} are in the flight.", soon)
            }
        }
        for (s in trips.flatMap { it.lodgings }) {
            val ciLocal = s.checkInDate ?: continue
            val zone = s.checkInTz?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            val ci = at(ciLocal, zone)
            if (!ci.isAfter(now) || !ci.isBefore(horizon)) continue
            val morning = at(ciLocal.toLocalDate().atTime(LocalTime.of(9, 0)), zone)
            if (!morning.isAfter(now) || !morning.isBefore(ci)) continue
            out += Planned("trippy.stay.${s.id}", "Check in at ${s.displayName} today", s.address?.takeIf { it.isNotBlank() } ?: "Your stay starts today.", morning)
        }
        return out.sortedBy { it.at }.take(60)
    }
}
