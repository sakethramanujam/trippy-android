package samalabs.trippy.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

data class TripsState(
    val trips: List<ApiTrip> = emptyList(),
    val rentals: List<ApiRentalCar> = emptyList(),
    val journeys: List<ApiJourney> = emptyList(),
    /** True once anything (network or cache) filled the lists. */
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
    /** Epoch millis of the last fresh copy from the server. */
    val lastSynced: Long? = null,
)

/** The signed-in user's trips, rentals and journeys, kept in one observable state. */
class TripsRepository(
    private val api: ApiClient,
    private val cache: ResponseCache = ResponseCache.None,
    /** Which account's copy to read and write; see [Session.scope]. */
    private val scope: () -> String = { "" },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(TripsState())
    val state: StateFlow<TripsState> = _state.asStateFlow()

    fun trip(id: Int): ApiTrip? = _state.value.trips.firstOrNull { it.id == id }

    /** Fill the lists from the saved copy so the app opens instantly and works offline. False if there is none. */
    suspend fun prime(): Boolean {
        val key = scope()
        val t = cache.read(key, TRIPS) ?: return false
        val trips = runCatching { TrippyJson.decodeFromString(ListSerializer(ApiTrip.serializer()), t.body) }.getOrNull() ?: return false
        val rentals = cache.read(key, RENTALS)?.let { runCatching { TrippyJson.decodeFromString(ListSerializer(ApiRentalCar.serializer()), it.body) }.getOrNull() }
        val journeys = cache.read(key, JOURNEYS)?.let { runCatching { TrippyJson.decodeFromString(ListSerializer(ApiJourney.serializer()), it.body) }.getOrNull() }
        _state.update {
            if (it.lastSynced != null && it.lastSynced >= t.savedAt) it
            else it.copy(trips = trips, rentals = rentals.orEmpty(), journeys = journeys.orEmpty(), loaded = true, lastSynced = t.savedAt)
        }
        return true
    }

    suspend fun wipeCache() = cache.clear()

    fun markOffline() {
        _state.update { if (!it.offline) it.copy(offline = true) else it }
    }

    suspend fun refresh() {
        if (!_state.value.loaded) prime()
        _state.update { it.copy(refreshing = true) }
        try {
            coroutineScope {
                val trips = async { fetch(TRIPS, ListSerializer(ApiTrip.serializer())) }
                val rentals = async { runCatching { fetch(RENTALS, ListSerializer(ApiRentalCar.serializer())) }.getOrNull() }
                val journeys = async { runCatching { fetch(JOURNEYS, ListSerializer(ApiJourney.serializer())) }.getOrNull() }
                val t = trips.await()
                val r = rentals.await()
                val j = journeys.await()
                _state.update {
                    it.copy(
                        trips = t, rentals = r ?: it.rentals, journeys = j ?: it.journeys,
                        loaded = true, refreshing = false, offline = false, error = null,
                        lastSynced = clock(),
                    )
                }
            }
        } catch (e: OfflineException) {
            if (_state.value.trips.isEmpty()) prime()
            _state.update { it.copy(refreshing = false, offline = true, loaded = true, error = null) }
        } catch (e: SessionExpiredException) {
            _state.update { it.copy(refreshing = false) }
        } catch (e: Exception) {
            _state.update { it.copy(refreshing = false, loaded = true, error = e.message ?: "Couldn't load your trips.") }
        }
    }

    /** Pins are per user. Optimistic, rolled back if the server refuses. */
    suspend fun setPinned(id: Int, pinned: Boolean): String? {
        _state.update { s -> s.copy(trips = s.trips.map { if (it.id == id) it.copy(pinned = pinned) else it }) }
        return try {
            if (pinned) api.put("/trips/$id/pin") else api.delete("/trips/$id/pin")
            null
        } catch (e: Exception) {
            _state.update { s -> s.copy(trips = s.trips.map { if (it.id == id) it.copy(pinned = !pinned) else it }) }
            e.message
        }
    }

    /** Creates a flight on a trip, updates in-memory state + Room offline cache immediately, and refreshes from the server. */
    suspend fun addFlight(tripId: Int, bodyJson: String): ApiFlight {
        val raw = api.post("/trips/$tripId/flights/", bodyJson)
        val created = TrippyJson.decodeFromString(ApiFlight.serializer(), raw)
        val nowMs = clock()
        val updatedTrips = _state.value.trips.map { t ->
            if (t.id == tripId) t.copy(flights = t.flights + created) else t
        }
        _state.update { it.copy(trips = updatedTrips, offline = false, error = null, lastSynced = nowMs) }
        val encoded = TrippyJson.encodeToString(ListSerializer(ApiTrip.serializer()), updatedTrips)
        cache.write(CachedResponse(scope(), TRIPS, encoded, nowMs))
        runCatching {
            val fresh = fetch(TRIPS, ListSerializer(ApiTrip.serializer()))
            _state.update { it.copy(trips = fresh, offline = false, error = null, lastSynced = clock()) }
        }
        return created
    }

    /** Updates a flight on a trip, updates in-memory state + Room offline cache immediately, and refreshes from the server. */
    suspend fun updateFlight(flightId: Int, tripId: Int?, bodyJson: String): ApiFlight {
        val raw = api.patch("/flights/$flightId", bodyJson)
        val updated = TrippyJson.decodeFromString(ApiFlight.serializer(), raw)
        val nowMs = clock()
        val targetTripId = tripId ?: updated.tripId
        val updatedTrips = _state.value.trips.map { t ->
            if (targetTripId == null || t.id == targetTripId) {
                val newFlights = t.flights.map { if (it.id == flightId) updated else it }
                t.copy(flights = newFlights)
            } else t
        }
        _state.update { it.copy(trips = updatedTrips, offline = false, error = null, lastSynced = nowMs) }
        val encoded = TrippyJson.encodeToString(ListSerializer(ApiTrip.serializer()), updatedTrips)
        cache.write(CachedResponse(scope(), TRIPS, encoded, nowMs))
        runCatching {
            val fresh = fetch(TRIPS, ListSerializer(ApiTrip.serializer()))
            _state.update { it.copy(trips = fresh, offline = false, error = null, lastSynced = clock()) }
        }
        return updated
    }

    /** Deletes a flight from a trip, updates in-memory state + Room offline cache immediately, and refreshes from the server. */
    suspend fun deleteFlight(flightId: Int, tripId: Int?) {
        api.delete("/flights/$flightId")
        val nowMs = clock()
        val updatedTrips = _state.value.trips.map { t ->
            if (tripId == null || t.id == tripId) {
                t.copy(flights = t.flights.filterNot { it.id == flightId })
            } else t
        }
        _state.update { it.copy(trips = updatedTrips, offline = false, error = null, lastSynced = nowMs) }
        val encoded = TrippyJson.encodeToString(ListSerializer(ApiTrip.serializer()), updatedTrips)
        cache.write(CachedResponse(scope(), TRIPS, encoded, nowMs))
        runCatching {
            val fresh = fetch(TRIPS, ListSerializer(ApiTrip.serializer()))
            _state.update { it.copy(trips = fresh, offline = false, error = null, lastSynced = clock()) }
        }
    }

    /** Adds a traveler by name (+ optional email) to [tripId], updating state and Room cache immediately. */
    suspend fun addTraveler(tripId: Int, name: String, email: String? = null): ApiTrip {
        val body = TrippyJson.encodeToString(
            CreateTravelerRequest.serializer(),
            CreateTravelerRequest(name = name.trim(), email = email?.trim()?.takeIf { it.isNotEmpty() }),
        )
        val raw = api.post("/trips/$tripId/travelers", body)
        return replaceTripInCache(TrippyJson.decodeFromString(ApiTrip.serializer(), raw))
    }

    /** Links an already-known participant [userId] to [tripId]. */
    suspend fun addKnownParticipant(tripId: Int, userId: Int): ApiTrip {
        val raw = api.post("/trips/$tripId/participants/$userId")
        return replaceTripInCache(TrippyJson.decodeFromString(ApiTrip.serializer(), raw))
    }

    /** Removes [userId] from [tripId]. Throws [ApiException] with code 409 when unsettled expenses require [force] = true. */
    suspend fun removeTraveler(tripId: Int, userId: Int, force: Boolean = false): ApiTrip {
        val suffix = if (force) "?force=true" else ""
        val raw = api.delete("/trips/$tripId/participants/$userId$suffix")
        return replaceTripInCache(TrippyJson.decodeFromString(ApiTrip.serializer(), raw))
    }

    /** Merges [sourceTripId] into [targetTripId] and deletes [sourceTripId]. */
    suspend fun mergeTrip(sourceTripId: Int, targetTripId: Int): ApiTrip {
        val body = TrippyJson.encodeToString(MergeTripRequest.serializer(), MergeTripRequest(sourceTripId = sourceTripId, confirm = true))
        val raw = api.post("/trips/$targetTripId/merge", body)
        val merged = TrippyJson.decodeFromString(ApiTrip.serializer(), raw)
        val nowMs = clock()
        val updatedTrips = _state.value.trips
            .filter { it.id != sourceTripId }
            .map { if (it.id == targetTripId) merged.copy(pinned = it.pinned) else it }
        _state.update { it.copy(trips = updatedTrips, offline = false, error = null, lastSynced = nowMs) }
        val encoded = TrippyJson.encodeToString(ListSerializer(ApiTrip.serializer()), updatedTrips)
        cache.write(CachedResponse(scope(), TRIPS, encoded, nowMs))
        runCatching { refresh() }
        return merged
    }

    private suspend fun replaceTripInCache(updated: ApiTrip): ApiTrip {
        val nowMs = clock()
        val updatedTrips = _state.value.trips.map { t ->
            if (t.id == updated.id) updated.copy(pinned = t.pinned) else t
        }
        _state.update { it.copy(trips = updatedTrips, offline = false, error = null, lastSynced = nowMs) }
        val encoded = TrippyJson.encodeToString(ListSerializer(ApiTrip.serializer()), updatedTrips)
        cache.write(CachedResponse(scope(), TRIPS, encoded, nowMs))
        return updated
    }

    fun clear() { _state.value = TripsState() }

    /** Decode first, then save: a body the app can't read never replaces a good copy. */
    private suspend fun <T> fetch(path: String, serializer: KSerializer<T>): T {
        val key = scope()
        val body = api.get(path)
        val value = TrippyJson.decodeFromString(serializer, body)
        cache.write(CachedResponse(key, path, body, clock()))
        return value
    }

    companion object {
        const val TRIPS = "/trips/?limit=500"
        const val RENTALS = "/rental_cars/?limit=1000"
        const val JOURNEYS = "/journeys/"
    }
}
