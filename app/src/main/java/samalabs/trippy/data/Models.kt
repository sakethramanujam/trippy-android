package samalabs.trippy.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One Json for the whole app: tolerant of fields older or newer servers add or drop. */
val TrippyJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

@Serializable
data class ServerHealth(
    val status: String = "",
    val service: String? = null,
    val version: String? = null,
    val signup: String? = null,
    @SerialName("password_reset") val passwordReset: String? = null,
) {
    /** Servers before 0.2 answer only {"status","auth"}; reject anything naming a different service. */
    val isTrippy: Boolean get() = status == "healthy" && (service == null || service == "trippy")

    val summary: String
        get() {
            if (service == null) return "Trippy (older server)"
            val signupText = when (signup) {
                "invite" -> "invite-only sign-up"
                "closed" -> "sign-up closed"
                else -> "open sign-up"
            }
            return listOf("Trippy ${version.orEmpty()}".trim(), signupText).joinToString(" · ")
        }
}

@Serializable
data class Profile(
    val id: Int,
    val email: String,
    val name: String? = null,
    val initials: String? = null,
    @SerialName("home_airport") val homeAirport: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")
    val initialsOrGuess: String
        get() = initials?.takeIf { it.isNotBlank() }
            ?: displayName.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
}

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Int? = null,
    val user: Profile? = null,
)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class SignupRequest(
    val email: String,
    val password: String,
    val name: String,
    @SerialName("home_airport") val homeAirport: String? = null,
    @SerialName("invite_code") val inviteCode: String? = null,
)

@Serializable
data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class ApiParticipant(
    val id: Int,
    val username: String? = null,
    val email: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("is_placeholder") val isPlaceholder: Boolean = false,
) {
    val name: String get() = displayName?.takeIf { it.isNotBlank() } ?: username ?: email?.substringBefore("@") ?: "Traveler"
    val initials: String
        get() = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
}

@Serializable
data class ApiFlight(
    val id: Int,
    @SerialName("trip_id") val tripId: Int? = null,
    @SerialName("owner_id") val ownerId: Int? = null,
    val airline: String? = null,
    @SerialName("flight_number") val flightNumber: String? = null,
    @SerialName("departure_airport") val departureAirport: String? = null,
    @SerialName("arrival_airport") val arrivalAirport: String? = null,
    @SerialName("departure_time") val departureTime: String? = null,
    @SerialName("arrival_time") val arrivalTime: String? = null,
    @SerialName("aircraft_type") val aircraftType: String? = null,
    @SerialName("aircraft_model") val aircraftModel: String? = null,
    @SerialName("aircraft_manufacturer") val aircraftManufacturer: String? = null,
    @SerialName("delay_minutes") val delayMinutes: Int? = null,
    val status: String? = null,
    @SerialName("confirmation_number") val confirmationNumber: String? = null,
    @SerialName("distance_miles") val distanceMiles: Double? = null,
    @SerialName("duration_minutes") val durationMinutes: Int? = null,
    val cost: Double? = null,
    val currency: String? = null,
    val seat: String? = null,
    @SerialName("cabin_class") val cabinClass: String? = null,
    @SerialName("booking_source") val bookingSource: String? = null,
)

@Serializable
data class ApiLodging(
    val id: Int,
    @SerialName("trip_id") val tripId: Int? = null,
    @SerialName("owner_id") val ownerId: Int? = null,
    val name: String? = null,
    val address: String? = null,
    @SerialName("check_in") val checkIn: String? = null,
    @SerialName("check_out") val checkOut: String? = null,
    @SerialName("confirmation_number") val confirmationNumber: String? = null,
    val cost: Double? = null,
    val currency: String? = null,
    @SerialName("booking_source") val bookingSource: String? = null,
    val notes: String? = null,
    val brand: String? = null,
    @SerialName("property_type") val propertyType: String? = null,
    val city: String? = null,
    val country: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("time_approx") val timeApprox: Boolean = false,
    val nights: Int? = null,
    @SerialName("night_count") val nightCount: Int? = null,
    @SerialName("room_type") val roomType: String? = null,
    val rooms: Int? = null,
    val guests: Int? = null,
    @SerialName("taxes_fees") val taxesFees: Double? = null,
    @SerialName("loyalty_program") val loyaltyProgram: String? = null,
    @SerialName("stay_status") val stayStatus: String? = null,
    @SerialName("check_in_tz") val checkInTz: String? = null,
    @SerialName("check_out_tz") val checkOutTz: String? = null,
    @SerialName("loyalty_points") val loyaltyPoints: Int? = null,
    val status: String? = null,
)

@Serializable
data class ApiActivity(
    val id: Int,
    @SerialName("trip_id") val tripId: Int? = null,
    val title: String = "",
    val description: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("location_map_link") val mapLink: String? = null,
)

@Serializable
data class ApiRentalCar(
    val id: Int,
    @SerialName("trip_id") val tripId: Int? = null,
    @SerialName("owner_id") val ownerId: Int? = null,
    @SerialName("rental_company") val company: String? = null,
    @SerialName("car_model") val carModel: String? = null,
    @SerialName("vehicle_make") val vehicleMake: String? = null,
    @SerialName("vehicle_year") val vehicleYear: Int? = null,
    @SerialName("vehicle_color") val vehicleColor: String? = null,
    @SerialName("vehicle_class") val vehicleClass: String? = null,
    @SerialName("license_plate") val licensePlate: String? = null,
    @SerialName("pickup_location") val pickupLocation: String? = null,
    @SerialName("dropoff_location") val dropoffLocation: String? = null,
    @SerialName("pickup_time") val pickupTime: String? = null,
    @SerialName("dropoff_time") val dropoffTime: String? = null,
    @SerialName("pickup_code") val pickupCode: String? = null,
    @SerialName("dropoff_code") val dropoffCode: String? = null,
    @SerialName("pickup_city") val pickupCity: String? = null,
    @SerialName("dropoff_city") val dropoffCity: String? = null,
    @SerialName("pickup_lat") val pickupLat: Double? = null,
    @SerialName("pickup_lng") val pickupLng: Double? = null,
    @SerialName("dropoff_lat") val dropoffLat: Double? = null,
    @SerialName("dropoff_lng") val dropoffLng: Double? = null,
    @SerialName("mileage_driven") val mileageDriven: Double? = null,
    @SerialName("confirmation_number") val confirmationNumber: String? = null,
    @SerialName("agreement_number") val agreementNumber: String? = null,
    val cost: Double? = null,
    val currency: String? = null,
    @SerialName("fuel_type") val fuelType: String? = null,
    @SerialName("date_approx") val dateApprox: Boolean? = null,
    @SerialName("pickup_odometer") val pickupOdometer: Double? = null,
    @SerialName("dropoff_odometer") val dropoffOdometer: Double? = null,
    @SerialName("booking_source") val bookingSource: String? = null,
    val notes: String? = null,
)

@Serializable
data class ApiSplit(
    @SerialName("user_id") val userId: Int? = null,
    @SerialName("amount_cents") val amountCents: Long? = null,
    @SerialName("amount_owed") val amountOwed: Double? = null,
    val share: Double? = null,
)

@Serializable
data class ApiExpense(
    val id: Int,
    @SerialName("trip_id") val tripId: Int? = null,
    val amount: Double = 0.0,
    val description: String? = null,
    val date: String? = null,
    val currency: String? = null,
    @SerialName("payer_id") val payerId: Int? = null,
    @SerialName("amount_cents") val amountCents: Long? = null,
    @SerialName("split_mode") val splitMode: String? = null,
    val splits: List<ApiSplit> = emptyList(),
)

@Serializable
data class ApiTrip(
    val id: Int,
    val title: String = "",
    val description: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("owner_id") val ownerId: Int? = null,
    @SerialName("journey_id") val journeyId: Int? = null,
    @SerialName("journey_position") val journeyPosition: Int? = null,
    @SerialName("title_auto") val titleAuto: Boolean = false,
    val pinned: Boolean = false,
    val flights: List<ApiFlight> = emptyList(),
    val expenses: List<ApiExpense> = emptyList(),
    @SerialName("rental_cars") val rentalCars: List<ApiRentalCar> = emptyList(),
    val lodgings: List<ApiLodging> = emptyList(),
    val participants: List<ApiParticipant> = emptyList(),
    val activities: List<ApiActivity> = emptyList(),
)

@Serializable
data class JourneyStats(
    val days: Int = 0,
    val trips: Int = 0,
    val flights: Int = 0,
    val rentals: Int = 0,
    val stays: Int = 0,
    val nights: Int = 0,
    @SerialName("flight_miles") val flightMiles: Double = 0.0,
    @SerialName("drive_miles") val driveMiles: Double = 0.0,
    val miles: Double = 0.0,
    @SerialName("distance_miles") val distanceMiles: Double = 0.0,
    val places: Int = 0,
    val countries: Int = 0,
)

@Serializable
data class RoutePoint(
    val code: String? = null,
    val city: String? = null,
    val country: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)

@Serializable
data class JourneyLeg(
    @SerialName("trip_id") val tripId: Int,
    val title: String = "",
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val places: List<String> = emptyList(),
    val route: List<RoutePoint> = emptyList(),
    val stats: JourneyStats = JourneyStats(),
    @SerialName("cost_by_currency") val costByCurrency: Map<String, Double> = emptyMap(),
    val leg: Int = 0,
    @SerialName("item_count") val itemCount: Int = 0,
)

@Serializable
data class JourneyTimelineItem(
    val key: String,
    val kind: String,
    val date: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val place: RoutePoint? = null,
    @SerialName("from_place") val fromPlace: RoutePoint? = null,
    @SerialName("to_place") val toPlace: RoutePoint? = null,
    val metric: String? = null,
    val miles: Double? = null,
    val brand: String? = null,
    val code: String? = null,
    val day: Int? = null,
    @SerialName("trip_id") val tripId: Int? = null,
    val leg: Int? = null,
    @SerialName("trip_title") val tripTitle: String? = null,
)

@Serializable
data class ApiJourney(
    val id: Int,
    @SerialName("owner_id") val ownerId: Int? = null,
    val title: String = "",
    val description: String? = null,
    @SerialName("is_owner") val isOwner: Boolean = true,
    @SerialName("trip_ids") val tripIds: List<Int> = emptyList(),
    @SerialName("trip_count") val tripCount: Int = 0,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val places: List<String> = emptyList(),
    val stats: JourneyStats = JourneyStats(),
    @SerialName("cost_by_currency") val costByCurrency: Map<String, Double> = emptyMap(),
    val legs: List<JourneyLeg> = emptyList(),
    val timeline: List<JourneyTimelineItem> = emptyList(),
    val route: List<RoutePoint> = emptyList(),
)

@Serializable
data class JourneyCreate(val title: String, @SerialName("trip_ids") val tripIds: List<Int>)

@Serializable
data class JourneyAddTrips(@SerialName("trip_ids") val tripIds: List<Int>)

@Serializable
data class JourneyAfterDelete(
    val id: Int,
    val title: String = "",
    @SerialName("trips_left") val tripsLeft: Int = 0,
    @SerialName("journey_deleted") val journeyDeleted: Boolean = false,
)

@Serializable
data class DeletePreview(
    @SerialName("trip_id") val tripId: Int,
    val title: String = "",
    @SerialName("is_owner") val isOwner: Boolean = true,
    val action: String = "delete",
    val counts: Map<String, Int> = emptyMap(),
    val unsettled: Boolean = false,
    @SerialName("unsettled_people") val unsettledPeople: Int = 0,
    @SerialName("unsettled_cents") val unsettledCents: Long = 0,
    val journey: JourneyAfterDelete? = null,
    @SerialName("keep_days") val keepDays: Int = 30,
)

@Serializable
data class BulkDeleteRequest(@SerialName("trip_ids") val tripIds: List<Int>, val permanent: Boolean = false)

@Serializable
data class BulkDeleteResult(@SerialName("keep_days") val keepDays: Int = 30)

@Serializable
data class TrashedTrip(
    val id: Int,
    val title: String = "",
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("purge_at") val purgeAt: String? = null,
    val counts: Map<String, Int> = emptyMap(),
)

@Serializable
data class TrashList(@SerialName("keep_days") val keepDays: Int = 30, val trips: List<TrashedTrip> = emptyList())

@Serializable
data class TripIds(@SerialName("trip_ids") val tripIds: List<Int>)

@Serializable
data class LiveFlightStatus(
    val provider: String? = null,
    val status: String = "unknown",
    @SerialName("departure_scheduled") val departureScheduled: String? = null,
    @SerialName("departure_estimated") val departureEstimated: String? = null,
    @SerialName("departure_actual") val departureActual: String? = null,
    @SerialName("departure_terminal") val departureTerminal: String? = null,
    @SerialName("departure_gate") val departureGate: String? = null,
    @SerialName("arrival_scheduled") val arrivalScheduled: String? = null,
    @SerialName("arrival_estimated") val arrivalEstimated: String? = null,
    @SerialName("arrival_actual") val arrivalActual: String? = null,
    @SerialName("arrival_terminal") val arrivalTerminal: String? = null,
    @SerialName("arrival_gate") val arrivalGate: String? = null,
    @SerialName("baggage_claim") val baggageClaim: String? = null,
    @SerialName("delay_minutes") val delayMinutes: Int? = null,
    @SerialName("fetched_at") val fetchedAt: String? = null,
)

@Serializable
data class FlightStatusResponse(
    val configured: Boolean = false,
    val provider: String? = null,
    val status: LiveFlightStatus? = null,
    val message: String? = null,
)
