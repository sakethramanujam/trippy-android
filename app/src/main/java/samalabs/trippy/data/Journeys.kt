package samalabs.trippy.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import samalabs.trippy.domain.Fmt

@Serializable
data class SuggestionTrip(val id: Int, val title: String = "", @SerialName("start_date") val startDate: String? = null, @SerialName("end_date") val endDate: String? = null)

@Serializable
data class JourneySuggestion(
    val key: String,
    @SerialName("trip_ids") val tripIds: List<Int> = emptyList(),
    val trips: List<SuggestionTrip> = emptyList(),
    val title: String = "",
    val score: Double = 0.0,
    val confidence: String = "",
    val reasons: List<String> = emptyList(),
    @SerialName("journey_id") val journeyId: Int? = null,
    val dismissed: Boolean = false,
)

@Serializable
data class SuggestionList(val suggestions: List<JourneySuggestion> = emptyList())

/** "Apr 10 – May 7, 2026", like the iOS journey card. */
val ApiJourney.dateText: String
    get() {
        val s = Fmt.parse(startDate) ?: return "No dates"
        val e = Fmt.parse(endDate)
        if (e == null || e.toLocalDate() == s.toLocalDate()) return Fmt.monthDayYear(s)
        if (e.year != s.year) return "${Fmt.monthDayYear(s)} – ${Fmt.monthDayYear(e)}"
        return "${Fmt.monthDay(s)} – ${Fmt.monthDay(e)}, ${e.year}"
    }

val ApiJourney.costText: String?
    get() = costByCurrency.filter { it.value > 0 }.entries.sortedByDescending { it.value }
        .takeIf { it.isNotEmpty() }?.joinToString(" + ") { Fmt.compactMoney(it.value, it.key) }

fun JourneyLeg.subtitle(): String = buildList {
    Fmt.parse(startDate)?.let { s ->
        val e = Fmt.parse(endDate)
        add(if (e != null && e.toLocalDate() != s.toLocalDate()) "${Fmt.monthDay(s)} – ${Fmt.monthDay(e)}" else Fmt.monthDay(s))
    }
    route.mapNotNull { it.code ?: it.city }.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" → ")) }
}.joinToString("  •  ")

/** Journey detail, suggestions and the edits that link trips together. Lists live in [TripsRepository]. */
class JourneysRepository(private val api: ApiClient, private val trips: TripsRepository) {
    private val _suggestions = MutableStateFlow<List<JourneySuggestion>>(emptyList())
    val suggestions: StateFlow<List<JourneySuggestion>> = _suggestions.asStateFlow()
    private val _details = MutableStateFlow<Map<Int, ApiJourney>>(emptyMap())
    val details: StateFlow<Map<Int, ApiJourney>> = _details.asStateFlow()

    suspend fun loadSuggestions() {
        runCatching { TrippyJson.decodeFromString(SuggestionList.serializer(), api.get("/journeys/suggestions")) }
            .onSuccess { l -> _suggestions.value = l.suggestions.filterNot { it.dismissed } }
    }

    /** Returns an error message, or null once [details] holds the journey. */
    suspend fun load(id: Int): String? = try {
        val j = TrippyJson.decodeFromString(ApiJourney.serializer(), api.get("/journeys/$id"))
        _details.update { it + (id to j) }
        null
    } catch (e: Exception) { e.message ?: "Couldn't load this journey." }

    suspend fun create(title: String?, tripIds: List<Int>): Pair<Int?, String?> = mutate {
        val body = buildJsonObject {
            putJsonArray("trip_ids") { tripIds.forEach { add(it) } }
            title?.trim()?.takeIf { it.isNotEmpty() }?.let { put("title", it) }
        }
        val res = TrippyJson.parseToJsonElement(api.post("/journeys/", body.toString())) as? JsonObject
        res?.get("id")?.jsonPrimitive?.int
    }

    suspend fun addTrips(id: Int, tripIds: List<Int>): String? = mutate(id) { api.post("/journeys/$id/trips", ids(tripIds)) }.second
    suspend fun removeTrip(id: Int, tripId: Int): String? = mutate(id) { api.delete("/journeys/$id/trips/$tripId") }.second
    suspend fun reorder(id: Int, tripIds: List<Int>): String? = mutate(id) { api.put("/journeys/$id/order", ids(tripIds)) }.second
    suspend fun rename(id: Int, title: String): String? =
        mutate(id) { api.patch("/journeys/$id", buildJsonObject { put("title", title.trim()) }.toString()) }.second

    suspend fun dismiss(s: JourneySuggestion) {
        _suggestions.update { l -> l.filterNot { it.key == s.key } }
        runCatching { api.post("/journeys/suggestions/dismiss", ids(s.tripIds)) }
    }

    /** Into its existing journey when some trips are already linked, else a new one. */
    suspend fun accept(s: JourneySuggestion): Pair<Int?, String?> =
        s.journeyId?.let { j -> j to addTrips(j, s.tripIds) } ?: create(s.title, s.tripIds)

    fun clear() { _suggestions.value = emptyList(); _details.value = emptyMap() }

    private fun ids(tripIds: List<Int>) = buildJsonObject { putJsonArray("trip_ids") { tripIds.forEach { add(it) } } }.toString()

    private suspend fun <T> mutate(id: Int? = null, block: suspend () -> T): Pair<T?, String?> {
        val result = try { block() to null } catch (e: Exception) { null to (e.message ?: "Something went wrong.") }
        trips.refresh()
        loadSuggestions()
        id?.let { load(it) }
        return result
    }
}
