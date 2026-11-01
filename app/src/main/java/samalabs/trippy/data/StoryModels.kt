package samalabs.trippy.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StoryPlace(
    val code: String? = null,
    val city: String? = null,
    val country: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)

@Serializable
data class StoryPerson(val id: Int? = null, val name: String = "")

@Serializable
data class StoryItem(
    val key: String,
    val kind: String = "flight",
    val date: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val title: String = "",
    val subtitle: String? = null,
    val place: StoryPlace? = null,
    @SerialName("from_place") val fromPlace: StoryPlace? = null,
    @SerialName("to_place") val toPlace: StoryPlace? = null,
    val metric: String? = null,
    val miles: Double? = null,
    val brand: String? = null,
    val code: String? = null,
    val people: List<StoryPerson>? = null,
    val money: Boolean = false,
    val day: Int? = null,
    val hidden: Boolean = false,
    val note: String? = null,
    val photos: List<String> = emptyList(),
    /** kind "drive": every drive's [[lat, lng]] path that day, its stops, and the drive ids. */
    val paths: List<List<List<Double>>> = emptyList(),
    val stops: List<DriveStop> = emptyList(),
    @SerialName("drive_ids") val driveIds: List<Int> = emptyList(),
)

@Serializable
data class StoryHighlight(
    val icon: String? = null,
    val label: String = "",
    val value: String = "",
    val detail: String? = null,
    val money: Boolean = false,
)

@Serializable
data class StoryStats(
    val days: Int = 0,
    val places: Int = 0,
    val countries: Int = 0,
    val flights: Int = 0,
    val rentals: Int = 0,
    val stays: Int = 0,
    val nights: Int = 0,
    @SerialName("flight_miles") val flightMiles: Double = 0.0,
    @SerialName("drive_miles") val driveMiles: Double = 0.0,
    @SerialName("distance_miles") val distanceMiles: Double = 0.0,
    val drives: Int = 0,
    @SerialName("timeline_miles") val timelineMiles: Double = 0.0,
    val travelers: Int = 0,
    val spend: Map<String, Double> = emptyMap(),
    @SerialName("spend_text") val spendText: String? = null,
    val cities: List<String> = emptyList(),
)

@Serializable
data class TripStory(
    @SerialName("trip_id") val tripId: Int = 0,
    @SerialName("generated_title") val generatedTitle: String = "",
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val items: List<StoryItem> = emptyList(),
    val route: List<StoryPlace> = emptyList(),
    val travelers: List<StoryPerson> = emptyList(),
    val highlights: List<StoryHighlight> = emptyList(),
    val stats: StoryStats = StoryStats(),
    val title: String? = null,
    val subtitle: String? = null,
    val theme: String? = null,
    val edited: Boolean = false,
    /** Your photo thumbnails on this trip, tagged with their day and story item. */
    @SerialName("trip_photos") val tripPhotos: List<TripPhoto> = emptyList(),
) {
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: generatedTitle.ifBlank { "Trip" }
}

@Serializable
data class StoryItemEdit(val hidden: Boolean = false, val note: String? = null, val photos: List<String> = emptyList())

@Serializable
data class StoryEditBody(
    val title: String? = null,
    val subtitle: String? = null,
    val theme: String? = null,
    val order: List<String>? = null,
    val items: Map<String, StoryItemEdit>? = null,
)

@Serializable
data class YearPoint(
    val code: String? = null,
    val city: String? = null,
    val country: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val visits: Int = 1,
)

@Serializable
data class YearArc(val from: List<Double> = emptyList(), val to: List<Double> = emptyList(), val count: Int = 1)

@Serializable
data class YearDestination(val city: String = "", val visits: Int = 0)

@Serializable
data class YearStory(
    val year: Int,
    val trips: Int = 0,
    @SerialName("days_away") val daysAway: Int = 0,
    val flights: Int = 0,
    val rentals: Int = 0,
    val cities: Int = 0,
    val countries: Int = 0,
    @SerialName("flight_miles") val flightMiles: Double = 0.0,
    @SerialName("drive_miles") val driveMiles: Double = 0.0,
    /** Part of [driveMiles]: Google Timeline drives not already counted by a rental. */
    @SerialName("timeline_drive_miles") val timelineDriveMiles: Double = 0.0,
    val drives: Int = 0,
    val miles: Double = 0.0,
    @SerialName("around_earth") val aroundEarth: Double = 0.0,
    @SerialName("top_destinations") val topDestinations: List<YearDestination> = emptyList(),
    val home: StoryPlace? = null,
    val points: List<YearPoint> = emptyList(),
    val arcs: List<YearArc> = emptyList(),
)

class StoryRepository(private val api: ApiClient) {
    suspend fun story(tripId: Int): TripStory = TrippyJson.decodeFromString(TripStory.serializer(), api.get("/trips/$tripId/story"))

    suspend fun save(tripId: Int, body: StoryEditBody): TripStory {
        api.put("/trips/$tripId/story", TrippyJson.encodeToString(StoryEditBody.serializer(), body))
        return story(tripId)
    }

    suspend fun reset(tripId: Int): TripStory {
        api.delete("/trips/$tripId/story")
        return story(tripId)
    }

    suspend fun year(year: Int): YearStory = TrippyJson.decodeFromString(YearStory.serializer(), api.get("/stories/year?year=$year"))
}
