package samalabs.trippy.ui.components

import androidx.compose.ui.graphics.Color
import samalabs.trippy.data.ApiFlight
import samalabs.trippy.data.ApiLodging
import samalabs.trippy.data.ApiRentalCar

/** Brand colors for the letter tiles (the iOS app ships logos; we approximate with color + code). */
object Brands {
    private val airlines = mapOf(
        "UA" to 0xFF0A2F6B, "AA" to 0xFF0078D2, "DL" to 0xFFC01933, "WN" to 0xFF304CB2, "AS" to 0xFF01426A,
        "B6" to 0xFF003876, "LH" to 0xFF05164D, "AF" to 0xFF002157, "KL" to 0xFF00A1DE, "BA" to 0xFF075AAA,
        "EK" to 0xFFD71921, "SQ" to 0xFF1D3F73, "CX" to 0xFF006564, "NH" to 0xFF13448F, "JL" to 0xFFCC0000,
        "TP" to 0xFF00A06A, "IB" to 0xFFD7192D, "AC" to 0xFFD22630, "QF" to 0xFFE0001B, "AI" to 0xFFD71920,
    )
    private val rentals = mapOf(
        "hertz" to 0xFFFFCC00, "avis" to 0xFFD4002A, "enterprise" to 0xFF169A5A, "national" to 0xFF0A7A3E,
        "budget" to 0xFFF68B1F, "sixt" to 0xFFFF5F00, "alamo" to 0xFF1C5DAA, "turo" to 0xFF593CFB,
    )
    private val hotels = mapOf(
        "hyatt" to 0xFF2F5F8F, "marriott" to 0xFF8C1C13, "hilton" to 0xFF104C97, "ihg" to 0xFF1E3D59,
        "airbnb" to 0xFFFF5A5F, "accor" to 0xFF1E1852, "four seasons" to 0xFF333333,
    )

    fun carrierCode(f: ApiFlight): String = f.flightNumber.orEmpty().takeWhile { !it.isDigit() && it != ' ' }.uppercase()
        .ifEmpty { f.flightNumber.orEmpty().take(2).uppercase() }

    fun flight(f: ApiFlight): Color = Color(airlines[carrierCode(f)] ?: 0xFF2C2C2E)
    fun rental(r: ApiRentalCar): Color = Color(rentals.entries.firstOrNull { r.company.orEmpty().contains(it.key, true) }?.value ?: 0xFF2C2C2E)
    fun hotel(h: ApiLodging): Color {
        val key = listOfNotNull(h.brand, h.bookingSource, h.name).joinToString(" ")
        return Color(hotels.entries.firstOrNull { key.contains(it.key, true) }?.value ?: 0xFF8A4B38)
    }
    fun hotelLetter(h: ApiLodging): String = (h.brand ?: h.name ?: "H").trim().take(1).uppercase()
}
