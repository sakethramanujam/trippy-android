package samalabs.trippy.domain

import samalabs.trippy.data.ApiRentalCar
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Where a rental is relative to now, as on iOS (`RentalPhase`). */
enum class RentalPhase(val label: String) {
    UPCOMING("Upcoming"), ACTIVE("On the road"), OVERDUE("Overdue"), RETURNED("Returned");

    val isLive: Boolean get() = this != RETURNED
}

/** Receipt-only rentals have a guessed date; they have already ended. */
val ApiRentalCar.isApproximate: Boolean get() = dateApprox == true

fun ApiRentalCar.phase(now: LocalDateTime): RentalPhase {
    if (isApproximate) return RentalPhase.RETURNED
    val start = pickupDate ?: return RentalPhase.RETURNED
    if (now.isBefore(start)) return RentalPhase.UPCOMING
    dropoffDate?.let { end -> return if (now.isBefore(end)) RentalPhase.ACTIVE else RentalPhase.RETURNED }
    // No recorded return: on the road for 3 days after pickup.
    return if (Duration.between(start, now).seconds < 3 * 86_400) RentalPhase.ACTIVE else RentalPhase.RETURNED
}

/** Fraction of the rental elapsed (0..1), for the progress bar. */
fun ApiRentalCar.progress(now: LocalDateTime): Double {
    val s = pickupDate; val e = dropoffDate
    if (s == null || e == null || !e.isAfter(s)) return if (phase(now) == RentalPhase.RETURNED) 1.0 else 0.0
    val f = Duration.between(s, now).seconds.toDouble() / Duration.between(s, e).seconds
    return f.coerceIn(0.0, 1.0)
}

/** "Pickup in 2d 3h", "Return in 1d 4h", "Returned 12 days ago". */
fun ApiRentalCar.countdown(now: LocalDateTime): String = when (phase(now)) {
    RentalPhase.UPCOMING -> "Pickup in " + Fmt.duration(Duration.between(now, pickupDate!!).seconds)
    RentalPhase.ACTIVE, RentalPhase.OVERDUE ->
        dropoffDate?.let { "Return in " + Fmt.duration(Duration.between(now, it).seconds) } ?: "Return time not set"
    RentalPhase.RETURNED -> {
        val end = dropoffDate ?: pickupDate
        if (end == null) "Returned" else {
            val days = ChronoUnit.DAYS.between(end, now)
            when {
                days == 0L -> "Returned today"
                days == 1L -> "Returned yesterday"
                days < 45 -> "Returned $days days ago"
                else -> "Returned " + Fmt.monthDayYear(end)
            }
        }
    }
}

/** Seconds between pickup and return, when both are known and in order. */
val ApiRentalCar.rentalDuration: Long?
    get() {
        val s = pickupDate ?: return null
        val e = dropoffDate ?: return null
        return if (e.isAfter(s)) Duration.between(s, e).seconds else null
    }

/** Only the booked class ("... or similar") is known, not the actual car. */
val ApiRentalCar.isSimilarModel: Boolean get() = carModel.orEmpty().contains("or similar", ignoreCase = true)

val ApiRentalCar.vehicleSubtitle: String
    get() = buildList {
        vehicleYear?.let { add("$it") }
        if (isSimilarModel) add("or similar")
        vehicleColor?.takeIf { it.isNotBlank() && it != "Per Mfg Spec" }?.let { c ->
            add(c.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar(Char::uppercase) })
        }
        DisplayName.vehicleClass(vehicleClass)?.let { add(it) }
    }.joinToString(" · ")

val ApiRentalCar.pickupYear: Int? get() = pickupDate?.year

/** Live rentals soonest first; the rest newest first, as the iOS rentals screen splits them. */
fun List<ApiRentalCar>.liveRentals(now: LocalDateTime): List<ApiRentalCar> =
    filter { it.phase(now).isLive }.sortedBy { it.pickupDate ?: LocalDateTime.MAX }

fun List<ApiRentalCar>.pastRentals(now: LocalDateTime): List<ApiRentalCar> =
    filter { !it.phase(now).isLive }.sortedByDescending { it.pickupDate ?: LocalDateTime.MIN }

/** Past rentals grouped by pickup year, newest year first; undated ones (year 0) last. */
fun List<ApiRentalCar>.byPickupYear(): List<Pair<Int, List<ApiRentalCar>>> =
    groupBy { it.pickupYear ?: 0 }.toList().sortedWith(compareBy<Pair<Int, List<ApiRentalCar>>> { it.first == 0 }.thenByDescending { it.first })

/** Totals for the rentals header and the Driver's Log (iOS `RentalSummary`). */
class RentalSummary(rentals: List<ApiRentalCar>) {
    data class CompanyRow(val company: String, val count: Int, val days: Double, val spend: Double)
    data class YearRow(val year: Int, val count: Int, val spend: Double)

    val count: Int = rentals.size
    val days: Double = rentals.sumOf { (it.rentalDuration ?: 0L) / 86_400.0 }
    val miles: Double = rentals.sumOf { it.milesDriven ?: 0.0 }
    val spend: Double = rentals.filter { (it.currency ?: "USD") == "USD" }.sumOf { it.cost ?: 0.0 }

    val byCompany: List<CompanyRow> = rentals.groupBy { it.company.orEmpty() }
        .map { (k, v) -> CompanyRow(k, v.size, v.sumOf { (it.rentalDuration ?: 0L) / 86_400.0 }, v.sumOf { it.cost ?: 0.0 }) }
        .sortedWith(compareByDescending<CompanyRow> { it.count }.thenByDescending { it.spend })

    val topCars: List<Pair<String, Int>> = rentals
        .filter { !it.isSimilarModel && it.carModel.orEmpty().contains(" ") }
        .groupingBy { it.vehicleName }.eachCount().toList()
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

    val byYear: List<YearRow> = rentals.mapNotNull { r -> r.pickupYear?.let { it to r } }
        .groupBy({ it.first }, { it.second })
        .map { (y, v) -> YearRow(y, v.size, v.sumOf { it.cost ?: 0.0 }) }
        .sortedBy { it.year }
}

/** Rental company colors and short codes for letter badges (iOS `RentalBrand`). */
data class RentalBrand(val name: String, val short: String, val color: Long, val foreground: Long = 0xFFFFFFFF) {
    companion object {
        fun forCompany(company: String?): RentalBrand {
            val c = company.orEmpty().trim()
            val k = c.lowercase()
            fun has(s: String) = k.contains(s)
            return when {
                has("avis") -> RentalBrand("Avis", "AV", 0xFFD4002A)
                has("budget") -> RentalBrand("Budget", "BG", 0xFFF68621, 0xFF0B1F44)
                has("enterprise") -> RentalBrand("Enterprise", "EN", 0xFF169A5A)
                has("national") -> RentalBrand("National", "NA", 0xFF0B7B53)
                has("alamo") -> RentalBrand("Alamo", "AL", 0xFF0060A9)
                has("hertz") -> RentalBrand("Hertz", "HZ", 0xFFFFD100, 0xFF111111)
                has("sixt") -> RentalBrand("Sixt", "SX", 0xFFFF5F00)
                has("thrifty") -> RentalBrand("Thrifty", "TH", 0xFF0072CE)
                has("dollar") -> RentalBrand("Dollar", "DR", 0xFFC8102E)
                has("europcar") -> RentalBrand("Europcar", "EC", 0xFF00953A, 0xFFFFE600)
                k == "fox" || has("fox rent") -> RentalBrand("Fox", "FX", 0xFFE35205)
                has("payless") -> RentalBrand("Payless", "PL", 0xFF6C2C91)
                has("turo") -> RentalBrand("Turo", "TU", 0xFF593CFB)
                else -> {
                    val words = c.split(" ").filter { it.isNotBlank() }
                    val short = words.take(2).joinToString("") { it.take(1) }.uppercase().ifEmpty { "?" }
                    RentalBrand(c.ifEmpty { "Rental" }, short, 0xFF636366)
                }
            }
        }
    }
}

/** Keys of the bundled logo drawables (iOS `BrandLogos`); the UI maps them to resources. */
object LogoKeys {
    private val rentalNoise = Regex("""(rent[\s-]*a[\s-]*car|car rental|rentals?|car hire|inc\.?|llc)""")
    private val airlineNoise = Regex("""\b(air ?lines?|airways|international|intl)\b""")

    fun rental(company: String?): String =
        company.orEmpty().lowercase().replace(rentalNoise, "").filter { it.isLetter() }

    /** Candidate airline keys in lookup order: alias of the name, the bare name, then the IATA code. */
    fun airline(name: String?, flightNumber: String? = null): List<String> {
        val n = name.orEmpty().lowercase().filter { it.isLetter() || it == ' ' }
        val stripped = n.replace(airlineNoise, "").filter { it.isLetter() }
        return buildList {
            (airlineAliases[stripped] ?: airlineAliases[n.filter { it.isLetter() }])?.let { add(it) }
            if (stripped.isNotEmpty()) add(stripped)
            (iata(name.orEmpty()) ?: flightNumber?.let(::iata))?.let { code -> iataCodes[code]?.let { add(it) } }
        }.distinct()
    }

    fun iata(s: String): String? {
        val t = s.trim().uppercase()
        if (t.length < 2) return null
        val code = t.take(2)
        if (!code.all { it.isLetterOrDigit() } || code.none { it.isLetter() }) return null
        val rest = t.drop(2).trim()
        return if (rest.isEmpty() || rest.all { it.isDigit() }) code else null
    }

    private val airlineAliases = mapOf(
        "delta" to "delta", "unitedairlines" to "united", "united" to "united", "american" to "american",
        "southwest" to "southwest", "jetblue" to "jetblue", "alaska" to "alaska", "hawaiian" to "hawaiian",
        "spirit" to "spirit", "frontier" to "frontier", "allegiant" to "allegiant", "aircanada" to "aircanada",
        "westjet" to "westjet", "aeromexico" to "aeromexico", "british" to "britishairways", "lufthansa" to "lufthansa",
        "klm" to "klm", "klmroyaldutch" to "klm", "airfrance" to "airfrance", "emirates" to "emirates",
        "qatar" to "qatar", "etihad" to "etihad", "singapore" to "singapore", "ana" to "ana", "allnippon" to "ana",
        "japan" to "jal", "jal" to "jal", "qantas" to "qantas", "turkish" to "turkish", "iberia" to "iberia",
        "virginatlantic" to "virginatlantic", "ryanair" to "ryanair", "easyjet" to "easyjet", "norwegian" to "norwegian",
        "norwegianairshuttle" to "norwegian", "wizzair" to "wizzair", "wizz" to "wizzair", "airindia" to "airindia",
        "airchina" to "airchina", "chinaeastern" to "chinaeastern", "chinasouthern" to "chinasouthern", "copa" to "copa",
        "avianca" to "avianca", "ethiopian" to "ethiopian", "lotpolish" to "lot", "lot" to "lot", "indigo" to "indigo",
        "saudia" to "saudia", "saudi" to "saudia", "airasia" to "airasia", "pegasus" to "pegasus", "airtransat" to "airtransat",
        "transat" to "airtransat", "cathaypacific" to "cathay", "cathay" to "cathay", "korean" to "korean", "koreanair" to "korean",
        "eva" to "eva", "evaair" to "eva", "finnair" to "finnair", "scandinavian" to "sas", "sas" to "sas", "aerlingus" to "aerlingus",
        "airnewzealand" to "airnz", "swiss" to "swiss", "austrian" to "austrian", "icelandair" to "icelandair",
    )

    private val iataCodes = mapOf(
        "DL" to "delta", "UA" to "united", "AA" to "american", "WN" to "southwest", "B6" to "jetblue", "AS" to "alaska",
        "HA" to "hawaiian", "NK" to "spirit", "F9" to "frontier", "G4" to "allegiant", "AC" to "aircanada", "WS" to "westjet",
        "AM" to "aeromexico", "BA" to "britishairways", "LH" to "lufthansa", "KL" to "klm", "AF" to "airfrance", "EK" to "emirates",
        "QR" to "qatar", "EY" to "etihad", "SQ" to "singapore", "NH" to "ana", "JL" to "jal", "QF" to "qantas", "TK" to "turkish",
        "IB" to "iberia", "VS" to "virginatlantic", "FR" to "ryanair", "U2" to "easyjet", "DY" to "norwegian", "W6" to "wizzair",
        "AI" to "airindia", "CA" to "airchina", "MU" to "chinaeastern", "CZ" to "chinasouthern", "CM" to "copa", "AV" to "avianca",
        "ET" to "ethiopian", "LO" to "lot", "6E" to "indigo", "SV" to "saudia", "AK" to "airasia", "PC" to "pegasus", "TS" to "airtransat",
        "CX" to "cathay", "KE" to "korean", "BR" to "eva", "AY" to "finnair", "SK" to "sas", "EI" to "aerlingus", "NZ" to "airnz",
        "LX" to "swiss", "OS" to "austrian", "FI" to "icelandair",
    )
}
