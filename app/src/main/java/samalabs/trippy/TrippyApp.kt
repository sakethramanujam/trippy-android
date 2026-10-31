package samalabs.trippy

import android.app.Application
import samalabs.trippy.data.ApiClient
import samalabs.trippy.data.AuthRepository
import samalabs.trippy.data.SessionStore
import samalabs.trippy.data.JourneysRepository
import samalabs.trippy.data.TripsRepository
import samalabs.trippy.data.TrashRepository
import samalabs.trippy.data.RoomResponseCache
import samalabs.trippy.data.TrippyDatabase
import samalabs.trippy.notify.TripReminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Hand-wired dependencies; small enough that a DI framework would only add weight. */
class AppContainer(app: Application) {
    val sessions = SessionStore(app)
    val api = ApiClient(sessions)
    val auth = AuthRepository(api, sessions)
    val database = TrippyDatabase.open(app)
    val trips = TripsRepository(api, RoomResponseCache(database.cache()), scope = { sessions.current.scope })
    val journeys = JourneysRepository(api, trips)
    val trash = TrashRepository(api, trips, journeys)
    val reminders = TripReminders(app)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val inbound = samalabs.trippy.data.InboundRepository(api)

    init {
        api.onSessionExpired = { sessions.signOut(); trips.wipeCache() }
        auth.onSignedOut = { trips.wipeCache(); reminders.cancelAll() }
        TripReminders.createChannel(app)
        // Re-plan reminders whenever the trips change (cache prime, refresh, edits).
        appScope.launch {
            trips.state.filter { it.loaded }.map { it.trips }.distinctUntilChanged().collect { reminders.reschedule(it) }
        }
    }
}

class TrippyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
