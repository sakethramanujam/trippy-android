package samalabs.trippy.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class ApiStatItem(val name: String = "", val count: Int = 0)

@Serializable
data class ApiFlightStats(
    val count: Int = 0,
    val airports: Int = 0,
    @SerialName("distance_miles") val distanceMiles: Double = 0.0,
    @SerialName("duration_minutes") val durationMinutes: Int = 0,
    @SerialName("top_aircraft") val topAircraft: List<ApiStatItem>? = null,
    @SerialName("top_airlines") val topAirlines: List<ApiStatItem>? = null,
)

@Serializable
data class ApiStayStats(
    val count: Int = 0,
    val nights: Int = 0,
    @SerialName("total_cost_by_currency") val totalCostByCurrency: Map<String, Double>? = null,
    @SerialName("top_brands") val topBrands: List<ApiStatItem>? = null,
    @SerialName("top_booking_sources") val topBookingSources: List<ApiStatItem>? = null,
    val cities: Int? = null,
)

/** `GET /stats/`: nested flights/stays blocks plus the older flat aliases. */
@Serializable
data class ApiStats(
    val year: Int? = null,
    val flights: ApiFlightStats? = null,
    val stays: ApiStayStats? = null,
    @SerialName("total_trips") val totalTrips: Int = 0,
    @SerialName("total_expenses_by_currency") val totalExpensesByCurrency: Map<String, Double>? = null,
    @SerialName("total_expenses") val totalExpenses: Double = 0.0,
    @SerialName("total_flights") val totalFlights: Int = 0,
    @SerialName("total_airports") val totalAirports: Int = 0,
    @SerialName("top_aircraft") val topAircraft: List<ApiStatItem>? = null,
    @SerialName("top_airlines") val topAirlines: List<ApiStatItem>? = null,
) {
    val flightCount: Int get() = flights?.count ?: totalFlights
    val airportCount: Int get() = flights?.airports ?: totalAirports
    val flightMiles: Double get() = flights?.distanceMiles ?: 0.0
    val logbookAircraft: List<ApiStatItem> get() = flights?.topAircraft ?: topAircraft.orEmpty()
    val logbookAirlines: List<ApiStatItem> get() = flights?.topAirlines ?: topAirlines.orEmpty()
    val usdExpenses: Double get() = totalExpensesByCurrency?.get("USD") ?: totalExpenses
}

@Serializable private data class ApiStatsYears(val years: List<Int> = emptyList())

class StatsRepository(private val api: ApiClient) {
    suspend fun stats(year: Int?): ApiStats =
        TrippyJson.decodeFromString(ApiStats.serializer(), api.get("/stats/" + (year?.let { "?year=$it" } ?: "")))

    suspend fun years(): List<Int> = TrippyJson.decodeFromString(ApiStatsYears.serializer(), api.get("/stats/years")).years
}

/** "1d 4h" / "5h 20m" / "0h" (iOS `formatHours`). */
fun formatFlightHours(minutes: Int): String {
    if (minutes <= 0) return "0h"
    val d = minutes / 1440; val h = (minutes % 1440) / 60
    return if (d > 0) "${d}d ${h}h" else "${h}h ${minutes % 60}m"
}
