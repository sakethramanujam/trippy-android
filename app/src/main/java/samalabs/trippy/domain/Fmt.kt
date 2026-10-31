package samalabs.trippy.domain

import java.text.NumberFormat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Formatting shared by every screen, matching the iOS app's Fmt. */
object Fmt {
    private val offset = Regex("([+-]\\d{2}:\\d{2}|Z)$")

    /** The API sends naive ISO strings (local wall-clock time at the place); offsets are ignored like on iOS. */
    fun parse(raw: String?): LocalDateTime? {
        val s = raw?.trim()?.replace(offset, "")?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { LocalDateTime.parse(s) }.getOrNull()
            ?: runCatching { LocalDate.parse(s.take(10)).atStartOfDay() }.getOrNull()
    }

    private fun f(pattern: String) = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())

    fun time(d: LocalDateTime): String = d.format(f("h:mm a"))
    fun day(d: LocalDateTime): String = d.format(f("EEE, MMM d"))
    fun dayYear(d: LocalDateTime): String = d.format(f("EEE, MMM d, yyyy"))
    fun monthDay(d: LocalDateTime): String = d.format(f("MMM d"))
    fun monthDayYear(d: LocalDateTime): String = d.format(f("MMM d, yyyy"))

    /** "1d 4h", "3h 12m", "45m". */
    fun duration(seconds: Long): String {
        val total = maxOf(0, seconds)
        val d = total / 86_400
        val h = (total % 86_400) / 3_600
        val m = (total % 3_600) / 60
        if (d > 0) return if (h > 0) "${d}d ${h}h" else "${d}d"
        if (h > 0) return if (m > 0) "${h}h ${m}m" else "${h}h"
        return "${m}m"
    }

    fun duration(from: LocalDateTime, to: LocalDateTime): String = duration(Duration.between(from, to).seconds)

    fun money(amount: Double, currency: String? = "USD", decimals: Boolean = true): String {
        val nf = NumberFormat.getCurrencyInstance(Locale.US)
        runCatching { nf.currency = Currency.getInstance(currency ?: "USD") }
        nf.maximumFractionDigits = if (decimals) 2 else 0
        nf.minimumFractionDigits = if (decimals) 2 else 0
        return nf.format(amount)
    }

    /** "$11.6k" for hero numbers. */
    fun compactMoney(amount: Double, currency: String? = "USD"): String {
        if (abs(amount) >= 10_000) {
            val symbol = if ((currency ?: "USD") == "USD") "$" else "${currency.orEmpty()} "
            return symbol + String.format(Locale.US, "%.1fk", amount / 1000)
        }
        return money(amount, currency, decimals = false)
    }

    fun number(value: Double): String = NumberFormat.getIntegerInstance(Locale.US).format(value.roundToLong())
}

/** Human labels for raw enum-ish API strings. */
object DisplayName {
    private val acronyms = setOf("ev", "suv", "awd", "4wd", "2wd", "fwd", "rwd", "phev", "hev", "vip", "usa", "uk")

    fun humanize(raw: String?): String {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return ""
        val looksRaw = "_" in t || t == t.lowercase() || t == t.uppercase()
        return t.replace("_", " ").replace(Regex("(?<=[a-z])-(?=[a-z])"), " ").split(Regex("\\s+")).joinToString(" ") { w ->
            when {
                w.lowercase() in acronyms -> w.uppercase()
                !looksRaw -> w
                else -> w.lowercase().replaceFirstChar { it.uppercase() }
            }
        }
    }

    private fun norm(raw: String?) = raw?.trim()?.lowercase()?.replace("_", " ")?.replace("-", " ").orEmpty()

    fun cabin(raw: String?): String? = when (norm(raw)) {
        "" -> null
        "economy", "coach", "main", "main cabin" -> "Economy"
        "business", "business class" -> "Business"
        "first", "first class" -> "First"
        else -> humanize(raw)
    }

    fun flightStatus(raw: String?): String? = when (norm(raw)) {
        "" -> null
        "scheduled" -> "Scheduled"
        "on time", "ontime" -> "On time"
        "active", "en route", "enroute", "in air", "airborne" -> "In flight"
        "landed", "arrived" -> "Landed"
        "delayed" -> "Delayed"
        "cancelled", "canceled" -> "Cancelled"
        "diverted" -> "Diverted"
        "boarding" -> "Boarding"
        else -> humanize(raw)
    }

    fun vehicleClass(raw: String?): String? = when (norm(raw)) {
        "" -> null
        "full size", "fullsize" -> "Full Size"
        "mid size", "midsize" -> "Midsize"
        "intermediate" -> "Intermediate"
        "suv" -> "SUV"
        else -> humanize(raw)
    }

    fun propertyType(raw: String?): String = when (norm(raw)) {
        "motel" -> "Motel"
        "rental" -> "Rental"
        "hostel" -> "Hostel"
        else -> "Hotel"
    }
}
