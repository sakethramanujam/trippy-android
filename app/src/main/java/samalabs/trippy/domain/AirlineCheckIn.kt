package samalabs.trippy.domain

import samalabs.trippy.data.ApiFlight
import java.net.URLEncoder
import java.time.Duration
import java.time.LocalDateTime

/** Airline check-in pages and when check-in usually opens. Same table as iOS QuickActions.swift. */
object AirlineCheckIn {
    data class Info(val url: String, val opensHours: Int, val known: Boolean)
    data class Countdown(val text: String, val open: Boolean)

    private val table = mapOf(
        "UA" to ("https://www.united.com/en/us/checkin" to 24),
        "AA" to ("https://www.aa.com/reservation/view/find-your-trip" to 24),
        "DL" to ("https://www.delta.com/mytrips/" to 24),
        "WN" to ("https://www.southwest.com/air/check-in/" to 24),
        "AS" to ("https://www.alaskaair.com/booking/check-in" to 24),
        "B6" to ("https://www.jetblue.com/check-in" to 24),
        "NK" to ("https://www.spirit.com/check-in" to 24),
        "F9" to ("https://www.flyfrontier.com/travel/my-trips/manage-trip/" to 24),
        "HA" to ("https://www.hawaiianairlines.com/manage-flights/check-in" to 24),
        "AC" to ("https://www.aircanada.com/us/en/aco/home/app.html#/olci" to 24),
        "WS" to ("https://www.westjet.com/en-us/check-in" to 24),
        "BA" to ("https://www.britishairways.com/travel/olcilandingpageauthreq/public/en_us" to 24),
        "VS" to ("https://www.virginatlantic.com/check-in" to 24),
        "LH" to ("https://www.lufthansa.com/us/en/online-check-in" to 23),
        "AF" to ("https://wwws.airfrance.us/check-in" to 30),
        "KL" to ("https://www.klm.com/check-in" to 30),
        "EK" to ("https://www.emirates.com/us/english/manage-booking/online-check-in/" to 48),
        "QR" to ("https://www.qatarairways.com/en/manage-booking/olci.html" to 24),
        "SQ" to ("https://www.singaporeair.com/en_UK/us/travel-info/check-in/" to 48),
        "CX" to ("https://www.cathaypacific.com/cx/en_US/manage-booking/check-in.html" to 48),
        "NH" to ("https://www.ana.co.jp/en/us/guide/boarding-procedures/checkin/online/" to 24),
        "JL" to ("https://www.jal.co.jp/jp/en/inter/boarding/online/" to 24),
        "AI" to ("https://www.airindia.com/in/en/manage/web-checkin.html" to 48),
        "6E" to ("https://www.goindigo.in/check-in.html" to 48),
        "TK" to ("https://www.turkishairlines.com/en-us/flights/manage-booking/online-check-in/" to 24),
        "QF" to ("https://www.qantas.com/us/en/travel-info/check-in.html" to 24),
        "AM" to ("https://aeromexico.com/en-us/web-check-in" to 24),
        "IB" to ("https://www.iberia.com/us/online-check-in/" to 24),
        "EI" to ("https://www.aerlingus.com/html/flightSearch.html#/checkin" to 30),
    )

    fun carrier(flightNumber: String?): String {
        val t = flightNumber.orEmpty().trim().uppercase()
        return if (' ' in t) t.substringBefore(' ') else t.take(2)
    }

    fun info(f: ApiFlight): Info {
        table[carrier(f.flightNumber)]?.let { (u, h) -> return Info(u, h, true) }
        val q = URLEncoder.encode("${f.airlineName} online check-in", "UTF-8")
        return Info("https://www.google.com/search?q=$q", 24, false)
    }

    fun opensAt(f: ApiFlight): LocalDateTime? = f.departureDate?.minusHours(info(f).opensHours.toLong())

    /** Null when there's nothing useful to say: more than a week out, or within 45 minutes of departure. */
    fun countdown(f: ApiFlight, now: LocalDateTime): Countdown? {
        val dep = f.departureDate ?: return null
        val opens = opensAt(f) ?: return null
        if (!now.isBefore(dep.minusMinutes(45))) return null
        if (!now.isBefore(opens)) return Countdown("Check-in is open", true)
        val wait = Duration.between(now, opens).seconds
        if (wait > 7 * 86_400) return null
        return Countdown("Check-in opens in " + Fmt.duration(wait), false)
    }

    fun subtitle(f: ApiFlight): String = info(f).let {
        if (it.known) "${f.airlineName} usually opens check-in ${it.opensHours}h before departure" else "Search for ${f.airlineName} online check-in"
    }
}
