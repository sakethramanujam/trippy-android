package samalabs.trippy.domain

import samalabs.trippy.data.ApiExpense
import samalabs.trippy.data.ApiTrip
import samalabs.trippy.data.Profile
import samalabs.trippy.data.TripLedger
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/** Money is handled in whole cents everywhere; dollars only appear at the API edge. */
object Cents {
    fun format(cents: Long, currency: String? = "USD"): String = Fmt.money(cents / 100.0, currency ?: "USD")

    /** "$1,234.5" -> 123450. Rounds half-up to the cent; null when it isn't a number. */
    fun parse(text: String): Long? {
        var t = text.trim().replace("$", "").replace(" ", "")
        if (t.isEmpty()) return null
        t = if ("," in t && "." in t) t.replace(",", "") else t.replace(",", ".")
        val d = t.toBigDecimalOrNull() ?: return null
        return d.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    /** 1230 -> "12.30", for prefilling an amount field. */
    fun plain(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()

    /**
     * Splits [total] by [weights] with the largest-remainder method so the parts always sum to the total.
     * Ties go to earlier entries, so put whoever should absorb the odd cent (the payer) first.
     */
    fun allocate(total: Long, weights: List<Pair<Int, Double>>): Map<Int, Long> {
        val sum = weights.sumOf { it.second }
        if (sum <= 0.0 || weights.isEmpty()) return emptyMap()
        val out = LinkedHashMap<Int, Long>()
        val rest = ArrayList<Triple<Int, Double, Int>>()
        weights.forEachIndexed { i, (id, w) ->
            val exact = total * w / sum
            val fl = floor(exact)
            out[id] = fl.toLong()
            rest += Triple(id, exact - fl, i)
        }
        var left = total - out.values.sum()
        for ((id, _, _) in rest.sortedWith(compareByDescending<Triple<Int, Double, Int>> { it.second }.thenBy { it.third })) {
            if (left <= 0) break
            out[id] = out.getValue(id) + 1
            left--
        }
        return out
    }
}

enum class SplitMode(val raw: String, val label: String) {
    Equal("equal", "Equally"), Exact("exact", "Exact $"), Percent("percent", "Percent"), Shares("shares", "Shares");

    companion object {
        fun from(raw: String?): SplitMode = entries.firstOrNull { it.raw == raw } ?: Equal
    }
}

enum class LedgerMode(val raw: String, val label: String) { Simplified("simplified", "Simplified"), Pairwise("pairwise", "Pairwise") }

fun shortNumber(v: Double): String =
    if (v == floor(v)) v.toLong().toString() else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

/** Everything the split editor derives from its inputs: per-person preview, a status line, the request values. */
data class SplitDraft(val totalCents: Long?, val ordered: List<Int>, val mode: SplitMode, val values: Map<Int, String>) {
    fun number(id: Int): Double? = values[id]?.trim()?.replace(",", ".")?.toDoubleOrNull()

    val preview: Map<Int, Long>
        get() {
            val total = totalCents ?: return emptyMap()
            if (ordered.isEmpty()) return emptyMap()
            return when (mode) {
                SplitMode.Equal -> Cents.allocate(total, ordered.map { it to 1.0 })
                SplitMode.Exact -> ordered.associateWith { Cents.parse(values[it].orEmpty()) ?: 0L }
                else -> Cents.allocate(total, ordered.map { it to maxOf(number(it) ?: 0.0, 0.0) })
            }
        }

    /** Message plus whether the split is valid enough to save. Null until there is an amount. */
    val status: Pair<String, Boolean>?
        get() {
            val total = totalCents ?: return null
            if (ordered.isEmpty()) return "Pick at least one person" to false
            return when (mode) {
                SplitMode.Equal ->
                    if (ordered.size == 1) "${Cents.format(total)} for one person" to true
                    else "About ${Cents.format(total / ordered.size)} each, ${ordered.size} ways" to true
                SplitMode.Exact -> {
                    val left = total - preview.values.sum()
                    when {
                        left == 0L -> "Adds up to ${Cents.format(total)}" to true
                        left > 0 -> "${Cents.format(left)} left to assign" to false
                        else -> "${Cents.format(-left)} over the total" to false
                    }
                }
                SplitMode.Percent -> {
                    val left = 100.0 - ordered.sumOf { number(it) ?: 0.0 }
                    when {
                        abs(left) < 0.01 -> "Adds up to 100%" to true
                        left > 0 -> "${shortNumber(left)}% left" to false
                        else -> "${shortNumber(-left)}% over" to false
                    }
                }
                SplitMode.Shares -> {
                    val sum = ordered.sumOf { maxOf(number(it) ?: 0.0, 0.0) }
                    if (sum > 0) "${shortNumber(sum)} shares in total" to true else "Give someone a share" to false
                }
            }
        }

    /** The `splits` array values: dollars for exact, raw numbers for percent and shares, nothing for equal. */
    fun splitValues(): List<Pair<Int, Double>>? = when (mode) {
        SplitMode.Equal -> null
        SplitMode.Exact -> ordered.map { it to (Cents.parse(values[it].orEmpty()) ?: 0L) / 100.0 }
        else -> ordered.map { it to (number(it) ?: 0.0) }
    }

    companion object {
        fun payerFirst(ids: List<Int>, payer: Int?): List<Int> =
            if (payer == null || payer !in ids) ids else listOf(payer) + ids.filter { it != payer }
    }
}

val ApiExpense.cents: Long get() = amountCents ?: Math.round(amount * 100)

/** Prefill for the per-person fields when editing an existing split. */
fun ApiExpense.initialSplitValues(): Map<Int, String> {
    val out = LinkedHashMap<Int, String>()
    for (s in splits) {
        val id = s.userId ?: continue
        when (SplitMode.from(splitMode)) {
            SplitMode.Equal -> Unit
            SplitMode.Exact -> (s.amountCents ?: s.amountOwed?.let { Math.round(it * 100) })?.let { out[id] = Cents.plain(it) }
            else -> s.share?.let { out[id] = shortNumber(it) }
        }
    }
    return out
}

data class MoneyPerson(val id: Int, val name: String) {
    val first: String get() = name.trim().split(" ").firstOrNull()?.ifBlank { null } ?: name
    val initials: String get() = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }
}

/** Ids allowed on a split: the owner, then the travelers. */
fun splitMemberIds(trip: ApiTrip?): List<Int> {
    if (trip == null) return emptyList()
    return (listOfNotNull(trip.ownerId) + trip.participants.map { it.id }).distinct()
}

/** Everyone who appears on the trip's money, named from the best source we have. */
fun moneyPeople(trip: ApiTrip?, ledger: TripLedger?, me: Profile?): List<MoneyPerson> {
    fun name(id: Int): String =
        trip?.participants?.firstOrNull { it.id == id }?.name
            ?: me?.takeIf { it.id == id }?.displayName
            ?: ledger?.people?.firstOrNull { it.userId == id }?.name?.takeIf { it.isNotBlank() }
            ?: "User $id"
    val ids = LinkedHashSet<Int>()
    ids += splitMemberIds(trip)
    ledger?.people?.forEach { ids += it.userId }
    return ids.map { MoneyPerson(it, name(it)) }
}
