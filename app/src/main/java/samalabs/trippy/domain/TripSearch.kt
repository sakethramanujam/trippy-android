package samalabs.trippy.domain

import samalabs.trippy.data.ApiRentalCar
import samalabs.trippy.data.ApiTrip
import java.text.Normalizer
import java.time.LocalDateTime

/** One search result. Mirrors iOS SearchHit. */
data class SearchHit(
    val id: String,
    val kind: Kind,
    val title: String,
    val subtitle: String,
    val target: Target,
    /** Newer first inside a group. */
    val date: LocalDateTime?,
) {
    enum class Kind(val title: String) { CONFIRMATION("Confirmations"), TRIP("Trips"), FLIGHT("Flights"), STAY("Stays"), RENTAL("Rentals") }

    sealed interface Target {
        data class Trip(val id: Int) : Target
        data class Flight(val id: Int, val tripId: Int) : Target
        data class Stay(val id: Int, val tripId: Int) : Target
        data class Rental(val id: Int) : Target
    }
}

/** Search what's on the phone (works offline): trips, cities, airports, airlines, flight numbers, hotels, rentals, PNRs. */
object TripSearch {
    private val marks = Regex("\\p{Mn}+")

    /** Fold case and accents so "zurich" finds "Zürich". */
    fun norm(s: String): String = marks.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase()

    /** Letters and digits only, so "ua837" finds "UA 837". */
    fun squash(s: String): String = norm(s).filter { it.isLetterOrDigit() }

    fun matches(q: String, fields: List<String?>): Boolean {
        val nq = norm(q).trim()
        val sq = squash(q)
        if (nq.isEmpty()) return false
        return fields.any { f ->
            if (f.isNullOrEmpty()) false
            else norm(f).contains(nq) || (sq.length >= 3 && squash(f).contains(sq))
        }
    }

    private fun city(code: String?) = code?.let { Airports.city(it) }
    private fun day(d: LocalDateTime?) = d?.let { Fmt.monthDayYear(it) }.orEmpty()

    fun search(query: String, trips: List<ApiTrip>, rentals: List<ApiRentalCar>): List<Pair<SearchHit.Kind, List<SearchHit>>> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val hits = mutableListOf<SearchHit>()

        fun confirmation(code: String?, id: String, what: String, target: SearchHit.Target, date: LocalDateTime?): Boolean {
            if (code.isNullOrEmpty() || !matches(q, listOf(code))) return false
            hits += SearchHit("c-$id", SearchHit.Kind.CONFIRMATION, code, what, target, date)
            return true
        }

        for (t in trips) {
            val places = buildList {
                t.flights.forEach { addAll(listOf(it.departureAirport, it.arrivalAirport, city(it.departureAirport), city(it.arrivalAirport))) }
                t.lodgings.forEach { addAll(listOf(it.city, it.country)) }
                t.rentalCars.forEach { addAll(listOf(it.pickupCity, it.dropoffCity, it.pickupCode)) }
            }
            if (matches(q, listOf(t.title, t.description) + places)) {
                val span = listOfNotNull(Fmt.parse(t.startDate), Fmt.parse(t.endDate)).map { Fmt.monthDayYear(it) }
                hits += SearchHit("t${t.id}", SearchHit.Kind.TRIP, t.title, if (span.isEmpty()) "Trip" else span.joinToString(" – "),
                    SearchHit.Target.Trip(t.id), Fmt.parse(t.startDate))
            }
            for (f in t.flights) {
                val d = f.departureDate
                val name = listOfNotNull(f.airline, f.flightNumber).joinToString(" ")
                val route = "${f.fromCode} → ${f.toCode}"
                val target = SearchHit.Target.Flight(f.id, t.id)
                if (confirmation(f.confirmationNumber, "f${f.id}", "$name · $route · ${t.title}", target, d)) continue
                if (matches(q, listOf(f.airline, f.flightNumber, name, f.departureAirport, f.arrivalAirport, city(f.departureAirport), city(f.arrivalAirport), f.aircraftType))) {
                    hits += SearchHit("f${f.id}", SearchHit.Kind.FLIGHT, "$name · $route",
                        listOf(day(d), t.title).filter { it.isNotEmpty() }.joinToString(" · "), target, d)
                }
            }
            for (l in t.lodgings) {
                val d = l.checkInDate
                val target = SearchHit.Target.Stay(l.id, t.id)
                if (confirmation(l.confirmationNumber, "s${l.id}", "${l.displayName} · ${t.title}", target, d)) continue
                if (matches(q, listOf(l.name, l.brand, l.city, l.country, l.address))) {
                    hits += SearchHit("s${l.id}", SearchHit.Kind.STAY, l.displayName,
                        listOfNotNull(l.city, day(d).ifEmpty { null }).joinToString(" · "), target, d)
                }
            }
        }
        // The global rentals list has every rental, with or without a trip.
        val all = (rentals.ifEmpty { trips.flatMap { it.rentalCars } }).distinctBy { it.id }
        for (r in all) {
            val d = r.pickupDate
            val car = r.carModel ?: r.vehicleClass ?: "Rental"
            val target = SearchHit.Target.Rental(r.id)
            if (confirmation(r.confirmationNumber, "r${r.id}", "${r.companyName} · $car", target, d)) continue
            if (confirmation(r.agreementNumber, "ra${r.id}", "Agreement · ${r.companyName}", target, d)) continue
            if (matches(q, listOf(r.company, r.carModel, r.vehicleMake, r.pickupLocation, r.dropoffLocation, r.pickupCity, r.dropoffCity, r.pickupCode, r.dropoffCode, r.licensePlate))) {
                hits += SearchHit("r${r.id}", SearchHit.Kind.RENTAL, "${r.companyName} · $car",
                    listOfNotNull(r.pickupCity ?: r.pickupCode, day(d)).filter { it.isNotEmpty() }.joinToString(" · "), target, d)
            }
        }
        return SearchHit.Kind.entries.mapNotNull { k ->
            hits.filter { it.kind == k }.takeIf { it.isNotEmpty() }?.let { list -> k to list.sortedByDescending { it.date ?: LocalDateTime.MIN } }
        }
    }
}
