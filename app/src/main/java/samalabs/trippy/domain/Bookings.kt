package samalabs.trippy.domain

import samalabs.trippy.data.ApiFlight
import samalabs.trippy.data.ApiLodging
import samalabs.trippy.data.ApiRentalCar
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

// Flights

val ApiFlight.departureDate: LocalDateTime? get() = Fmt.parse(departureTime)
val ApiFlight.arrivalDate: LocalDateTime? get() = Fmt.parse(arrivalTime)
val ApiFlight.fromCode: String get() = departureAirport?.uppercase().orEmpty()
val ApiFlight.toCode: String get() = arrivalAirport?.uppercase().orEmpty()
val ApiFlight.number: String get() = flightNumber.orEmpty()
val ApiFlight.airlineName: String get() = airline?.takeIf { it.isNotBlank() } ?: "Flight"

fun ApiFlight.progress(now: LocalDateTime): Double {
    val d = departureDate ?: return 0.0
    val a = arrivalDate ?: return 0.0
    if (!a.isAfter(d)) return 0.0
    return (Duration.between(d, now).seconds.toDouble() / Duration.between(d, a).seconds).coerceIn(0.0, 1.0)
}

/** "Departs in 3d 4h" / "Arrives in 2h 10m" / "Landed". */
fun ApiFlight.countdown(now: LocalDateTime): String? {
    val d = departureDate ?: return null
    if (now.isBefore(d)) return "Departs in " + Fmt.duration(now, d)
    val a = arrivalDate
    if (a != null && now.isBefore(a)) return "Arrives in " + Fmt.duration(now, a)
    return "Landed"
}

enum class StatusTone { GREEN, AMBER, RED, GRAY }

val ApiFlight.statusTone: StatusTone
    get() {
        val s = status.orEmpty().lowercase()
        return when {
            "cancel" in s -> StatusTone.RED
            "delay" in s || (delayMinutes ?: 0) > 0 -> StatusTone.AMBER
            "land" in s || "arriv" in s -> StatusTone.GRAY
            else -> StatusTone.GREEN
        }
    }

/** Server minutes, else the gap between the two local times read in each airport's zone. */
val ApiFlight.durationText: String?
    get() {
        durationMinutes?.let { return Fmt.duration(it * 60L) }
        val d = departureDate?.atZone(Airports.zone(departureAirport) ?: return null) ?: return null
        val a = arrivalDate?.atZone(Airports.zone(arrivalAirport) ?: return null) ?: return null
        return if (a.isAfter(d)) Fmt.duration(Duration.between(d, a).seconds) else null
    }

// Rentals

val ApiRentalCar.pickupDate: LocalDateTime? get() = Fmt.parse(pickupTime)
val ApiRentalCar.dropoffDate: LocalDateTime? get() = Fmt.parse(dropoffTime)
val ApiRentalCar.companyName: String get() = company?.takeIf { it.isNotBlank() } ?: "Rental"

val ApiRentalCar.pickupDisplayCode: String get() = stationCode(pickupCode, pickupCity, pickupLocation.orEmpty())
val ApiRentalCar.dropoffDisplayCode: String
    get() = stationCode(dropoffCode ?: pickupCode, dropoffCity ?: pickupCity, dropoffLocation.orEmpty())
val ApiRentalCar.pickupDisplayCity: String get() = cityName(pickupCity, pickupLocation.orEmpty()) ?: "Pickup"
val ApiRentalCar.dropoffDisplayCity: String
    get() = cityName(dropoffCity ?: pickupCity, dropoffLocation.orEmpty()) ?: pickupDisplayCity

val ApiRentalCar.isOneWay: Boolean
    get() = pickupDisplayCode != dropoffDisplayCode && pickupDisplayCode != "···" && dropoffDisplayCode != "···"

val ApiRentalCar.vehicleName: String
    get() {
        val raw = carModel?.trim().orEmpty()
        if (raw.isEmpty()) {
            vehicleMake?.takeIf { it.isNotBlank() }?.let { return it }
            DisplayName.vehicleClass(vehicleClass)?.let { return "$it car" }
            return "Rental car"
        }
        val model = raw.replace(Regex("\\s+or [Ss]imilar$"), "")
        val make = vehicleMake?.trim().orEmpty()
        return if (make.isNotEmpty() && !model.startsWith(make, ignoreCase = true)) "$make $model" else model
    }

val ApiRentalCar.rentalDays: Int?
    get() {
        val s = pickupDate ?: return null
        val e = dropoffDate ?: return null
        if (!e.isAfter(s)) return null
        val secs = Duration.between(s, e).seconds
        return maxOf(1, ceil((secs - 3_600) / 86_400.0).toInt())
    }

val ApiRentalCar.confirmationText: String?
    get() = (confirmationNumber?.takeIf { it.isNotBlank() } ?: agreementNumber)?.takeIf { it.isNotBlank() }

val ApiRentalCar.milesDriven: Double?
    get() {
        mileageDriven?.takeIf { it > 0 }?.let { return it }
        val a = pickupOdometer
        val b = dropoffOdometer
        return if (a != null && b != null && b > a) b - a else null
    }

val ApiRentalCar.costText: String? get() = cost?.let { Fmt.money(it, currency) }
val ApiRentalCar.pickupCoordinate: LatLng? get() = if (pickupLat != null && pickupLng != null) LatLng(pickupLat, pickupLng) else null
val ApiRentalCar.dropoffCoordinate: LatLng?
    get() = if (dropoffLat != null && dropoffLng != null) LatLng(dropoffLat, dropoffLng) else pickupCoordinate

internal fun stationCode(code: String?, city: String?, location: String): String {
    if (!code.isNullOrBlank()) return code.uppercase()
    Regex("\\b([A-Z]{3})(?=\\s+(Airport|Intl|International)\\b)").find(location)?.let { return it.groupValues[1] }
    val source = (city?.takeIf { it.isNotBlank() } ?: location).split(",").firstOrNull().orEmpty()
    val words = source.replace(Regex("[^A-Za-z ]"), " ").split(" ").filter { it.isNotBlank() }
    if (words.isEmpty()) return "···"
    val c = when {
        words.size >= 3 -> words.take(3).joinToString("") { it.take(1) }
        words.size == 2 -> words[0].take(2) + words[1].take(1)
        else -> words[0].take(3)
    }
    return c.uppercase()
}

internal fun cityName(city: String?, fallback: String): String? {
    if (!city.isNullOrBlank()) return city.split(",").first().trim()
    val f = fallback.split("·").first().trim()
    return f.takeIf { it.isNotEmpty() }?.split(" ")?.joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }
}

// Stays

val ApiLodging.checkInDate: LocalDateTime? get() = Fmt.parse(checkIn)
val ApiLodging.checkOutDate: LocalDateTime? get() = Fmt.parse(checkOut)
val ApiLodging.isCancelled: Boolean get() = status.orEmpty().lowercase() == "cancelled"
val ApiLodging.displayName: String get() = name?.takeIf { it.isNotBlank() } ?: "Stay"
val ApiLodging.isAirbnb: Boolean
    get() = listOf(bookingSource, brand).any { it.orEmpty().contains("airbnb", ignoreCase = true) }

private val ApiLodging.dateOnly: Boolean
    get() {
        if (timeApprox) return false
        val a = checkInDate ?: return false
        fun midnight(d: LocalDateTime) = d.hour == 0 && d.minute == 0
        return midnight(a) && (checkOutDate?.let(::midnight) ?: true)
    }

/** "3:00 PM", "~3:00 PM" when it's the brand's usual hour, "Any time" when only the date is known. */
fun ApiLodging.timeText(d: LocalDateTime?): String {
    d ?: return "—"
    if (dateOnly) return "Any time"
    return (if (timeApprox) "~" else "") + Fmt.time(d)
}

val ApiLodging.nightTotal: Int
    get() {
        (nightCount ?: nights)?.takeIf { it > 0 }?.let { return it }
        val a = checkInDate ?: return 0
        val b = checkOutDate ?: return 0
        return maxOf(0, ChronoUnit.DAYS.between(a.toLocalDate(), b.toLocalDate()).toInt())
    }

val ApiLodging.coordinate: LatLng? get() = if (lat != null && lng != null) LatLng(lat, lng) else null
val ApiLodging.costText: String? get() = cost?.let { Fmt.money(it, currency, decimals = false) }
val ApiLodging.place: String? get() = listOfNotNull(city, country).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { null }

/** "HYATT · TOKYO", or "AIRBNB · BRECKENRIDGE" for home rentals. */
val ApiLodging.microLine: String
    get() {
        val first = brand?.takeIf { it.isNotBlank() } ?: if (isAirbnb) "Airbnb" else DisplayName.propertyType(propertyType)
        return listOfNotNull(first, city?.takeIf { it.isNotBlank() }).joinToString(" · ").uppercase()
    }

/** "King room · via Airbnb". */
val ApiLodging.subtitleLine: String?
    get() = listOfNotNull(roomType?.takeIf { it.isNotBlank() }, bookingSource?.takeIf { it.isNotBlank() }?.let { "via ${DisplayName.humanize(it).replaceFirstChar(Char::uppercase)}" })
        .joinToString(" · ").ifEmpty { null }
