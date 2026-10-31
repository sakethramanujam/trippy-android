package samalabs.trippy.domain

import samalabs.trippy.data.StoryPlace
import samalabs.trippy.data.TripStory
import samalabs.trippy.data.YearStory
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

data class CardPalette(val colors: List<Long>, val accent: Long)

enum class StoryTheme(val id: String, val label: String, val palette: CardPalette) {
    Indigo("indigo", "Indigo", CardPalette(listOf(0xFF3B2BB8, 0xFF241673, 0xFF0D0730), 0xFFA99BFF)),
    Sunset("sunset", "Sunset", CardPalette(listOf(0xFFFF8A3D, 0xFFC2335B, 0xFF2B0B2E), 0xFFFFC36B)),
    Ocean("ocean", "Ocean", CardPalette(listOf(0xFF1E9BD7, 0xFF14508F, 0xFF061632), 0xFF7FD6FF)),
    Forest("forest", "Forest", CardPalette(listOf(0xFF2FB67C, 0xFF116149, 0xFF04201A), 0xFF8CF0BE)),
    Rose("rose", "Rose", CardPalette(listOf(0xFFF0629B, 0xFF8E2A7A, 0xFF220A2C), 0xFFFFB0D2));

    companion object {
        fun from(id: String?): StoryTheme = entries.firstOrNull { it.id == id } ?: Indigo
        val Year = CardPalette(listOf(0xFF5B3BF0, 0xFF2D138F, 0xFF10063A), 0xFFC6B8FF)
    }
}

/** Layout units; the bitmap is drawn at 3x (1080x1920 or 1080x1080). */
enum class CardFormat(val label: String, val width: Int, val height: Int) { Story("Story", 360, 640), Square("Square", 360, 360) }

object StoryText {
    private val us = Locale.US

    fun kindLabel(kind: String): String = when (kind) {
        "flight" -> "FLIGHT"
        "rental" -> "ROAD TRIP"
        "lodging" -> "STAY"
        "expenses" -> "SPENT"
        "travelers" -> "CREW"
        else -> kind.uppercase(us)
    }

    /** "Mar 1 – 8, 2026", "Mar 28 – Apr 4, 2026", "Dec 28, 2025 – Jan 3, 2026". */
    fun dateText(start: LocalDate?, end: LocalDate?): String {
        if (start == null) return ""
        val e = end?.takeIf { !it.isBefore(start) } ?: start
        fun f(p: String, d: LocalDate) = d.format(DateTimeFormatter.ofPattern(p, us))
        return when {
            e == start -> f("MMM d, yyyy", start)
            e.year != start.year -> "${f("MMM d, yyyy", start)} – ${f("MMM d, yyyy", e)}"
            e.month == start.month -> "${f("MMM d", start)} – ${f("d, yyyy", e)}"
            else -> "${f("MMM d", start)} – ${f("MMM d, yyyy", e)}"
        }
    }

    fun storyDates(s: TripStory): Pair<LocalDate?, LocalDate?> =
        Fmt.parse(s.startDate)?.toLocalDate() to Fmt.parse(s.endDate)?.toLocalDate()

    /** Airport code, else the first three letters of the city; repeats in a row collapse. */
    fun routeCodes(route: List<StoryPlace>): List<String> {
        val out = ArrayList<String>()
        for (p in route) {
            val c = p.code?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(us)
                ?: p.city?.trim()?.takeIf { it.isNotEmpty() }?.take(3)?.uppercase(us) ?: continue
            if (out.lastOrNull() != c) out += c
        }
        return out
    }

    /** At most [max] codes plus how many were left off. */
    fun strip(codes: List<String>, max: Int = 6): Pair<List<String>, Int> =
        if (codes.size <= max) codes to 0 else codes.take(max) to codes.size - max

    fun compactMiles(m: Double): String {
        val v = m.roundToLong()
        return when {
            v < 10_000 -> String.format(us, "%,d", v)
            v < 1_000_000 -> String.format(us, "%.1fK", v / 1000.0).replace(".0K", "K")
            else -> String.format(us, "%.1fM", v / 1_000_000.0).replace(".0M", "M")
        }
    }

    fun mrzClean(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").uppercase(us)
            .replace(Regex("[^A-Z0-9]"), "<").replace(Regex("<+"), "<").trim('<')

    fun mrzPad(s: String, n: Int = 34): String = s.take(n).padEnd(n, '<')

    fun tripMrz(s: TripStory): Pair<String, String> {
        val codes = routeCodes(s.route)
        val start = storyDates(s).first
        val date = start?.format(DateTimeFormatter.ofPattern("ddMMMyy", us))?.uppercase(us) ?: "<<<<<<<"
        return mrzPad("TRP<" + mrzClean(s.displayTitle) + "<<" + codes.joinToString("<")) to
            mrzPad("$date<<${s.stats.days}D<<${s.stats.distanceMiles.roundToLong()}MI<<TRIPPY")
    }

    fun yearMrz(y: YearStory): Pair<String, String> =
        mrzPad("YIT<${y.year}<<" + mrzClean(y.home?.code ?: y.home?.city ?: "")) to
            mrzPad("${y.trips}T<<${y.daysAway}D<<${y.miles.roundToLong()}MI<<TRIPPY")

    /** Label/value cells for the recap grid, in priority order. */
    fun tripStatCells(s: TripStory, hideSpend: Boolean): List<Pair<String, String>> {
        val st = s.stats
        val out = ArrayList<Pair<String, String>>()
        out += "DAYS" to "${max(st.days, 1)}"
        if (st.distanceMiles > 0) out += "DISTANCE" to "${compactMiles(st.distanceMiles)} mi"
        if (st.places > 0) out += "PLACES" to "${st.places}"
        if (st.flights > 0) out += "FLIGHTS" to "${st.flights}"
        if (st.rentals > 0) out += "DRIVES" to "${st.rentals}"
        if (st.nights > 0) out += "NIGHTS" to "${st.nights}"
        if (st.countries > 1) out += "COUNTRIES" to "${st.countries}"
        if (st.travelers > 1) out += "TRAVELERS" to "${st.travelers}"
        if (!hideSpend && !st.spendText.isNullOrBlank()) out += "SPENT" to st.spendText
        return out
    }

    fun yearStatCells(y: YearStory): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        out += "MILES" to compactMiles(y.miles)
        out += "CITIES" to "${y.cities}"
        out += "COUNTRIES" to "${y.countries}"
        if (y.flights > 0) out += "FLIGHTS" to "${y.flights}"
        if (y.rentals > 0) out += "DRIVES" to "${y.rentals}"
        if (y.aroundEarth >= 0.05) out += "AROUND EARTH" to String.format(us, "%.1f×", y.aroundEarth)
        if (y.daysAway > 0) out += "DAYS AWAY" to "${y.daysAway}"
        return out
    }

    /**
     * Projects lat/lng into a w x h box with [pad] margin: equirectangular, longitude squeezed by
     * cos(mean latitude), uniform scale, centred. A single point lands in the middle.
     */
    fun project(points: List<Pair<Double, Double>>, w: Float, h: Float, pad: Float): List<Pair<Float, Float>> {
        if (points.isEmpty()) return emptyList()
        val k = cos(Math.toRadians(points.map { it.first }.average()))
        val xs = points.map { it.second * k }
        val ys = points.map { -it.first }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val spanX = max(maxX - minX, 1e-6); val spanY = max(maxY - minY, 1e-6)
        val scale = min((w - 2 * pad) / spanX, (h - 2 * pad) / spanY).let { if (maxX - minX < 1e-6 && maxY - minY < 1e-6) 0.0 else it }
        val offX = (w - spanX * scale) / 2 - minX * scale
        val offY = (h - spanY * scale) / 2 - minY * scale
        val single = maxX - minX < 1e-6 && maxY - minY < 1e-6
        return points.indices.map { i ->
            if (single) (w / 2) to (h / 2) else (xs[i] * scale + offX).toFloat() to (ys[i] * scale + offY).toFloat()
        }
    }
}
