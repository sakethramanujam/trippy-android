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
