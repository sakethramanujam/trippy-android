package samalabs.trippy.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class DrivePlace(val name: String? = null, val lat: Double? = null, val lng: Double? = null)

@Serializable
data class DriveStop(val name: String? = null, val lat: Double? = null, val lng: Double? = null, val minutes: Int? = null)

/** A drive imported from Google Maps Timeline. Private to you, even on shared trips. */
@Serializable
data class ApiDrive(
    val id: Int,
    @SerialName("trip_id") val tripId: Int? = null,
    @SerialName("journey_id") val journeyId: Int? = null,
    @SerialName("rental_id") val rentalId: Int? = null,
    val source: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    val date: String? = null,
    @SerialName("distance_miles") val distanceMiles: Double = 0.0,
    @SerialName("duration_minutes") val durationMinutes: Int? = null,
    @SerialName("start_place") val startPlace: DrivePlace = DrivePlace(),
    @SerialName("end_place") val endPlace: DrivePlace = DrivePlace(),
    val stops: List<DriveStop> = emptyList(),
    /** [[lat, lng], …], downsampled on import. Missing when listed with with_path=false. */
    val path: List<List<Double>> = emptyList(),
)

@Serializable
data class SuggestionRental(
    val id: Int,
    val company: String? = null,
    val car: String? = null,
    @SerialName("pickup_time") val pickupTime: String? = null,
    @SerialName("dropoff_time") val dropoffTime: String? = null,
    @SerialName("pickup_location") val pickupLocation: String? = null,
    @SerialName("dropoff_location") val dropoffLocation: String? = null,
)

/** "This drive looks like your Hertz pickup": nothing is linked until you accept. */
@Serializable
data class DriveRentalSuggestion(
    @SerialName("drive_id") val driveId: Int,
    @SerialName("rental_id") val rentalId: Int,
    val match: String = "pickup",
    val reason: String = "",
    val drive: ApiDrive,
    val rental: SuggestionRental,
)

class DrivesRepository(private val api: ApiClient) {
    private val list = ListSerializer(ApiDrive.serializer())

    suspend fun forTrip(tripId: Int): List<ApiDrive> = decode(api.get("/drives?trip_id=$tripId"))
    suspend fun forJourney(journeyId: Int): List<ApiDrive> = decode(api.get("/drives?journey_id=$journeyId"))
    suspend fun forYear(year: Int?): List<ApiDrive> = decode(api.get("/drives?with_path=false" + (year?.let { "&year=$it" } ?: "")))

    suspend fun rentalSuggestions(): List<DriveRentalSuggestion> =
        TrippyJson.decodeFromString(ListSerializer(DriveRentalSuggestion.serializer()), api.get("/drives/rental-suggestions"))

    suspend fun linkRental(driveId: Int, rentalId: Int?) {
        api.post("/drives/$driveId/rental", buildJsonObject { put("rental_id", rentalId) }.toString())
    }

    suspend fun dismissRental(driveId: Int, rentalId: Int) {
        api.post("/drives/$driveId/rental/dismiss", buildJsonObject { put("rental_id", rentalId) }.toString())
    }

    suspend fun delete(driveId: Int) { api.delete("/drives/$driveId") }

    private fun decode(body: String) = TrippyJson.decodeFromString(list, body)
}
