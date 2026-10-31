package samalabs.trippy

import android.app.Application
import samalabs.trippy.data.ApiClient
import samalabs.trippy.data.AuthRepository
import samalabs.trippy.data.SessionStore
import samalabs.trippy.data.JourneysRepository
import samalabs.trippy.data.TripsRepository

/** Hand-wired dependencies; small enough that a DI framework would only add weight. */
class AppContainer(app: Application) {
    val sessions = SessionStore(app)
    val api = ApiClient(sessions)
    val auth = AuthRepository(api, sessions)
    val trips = TripsRepository(api)
    val journeys = JourneysRepository(api, trips)

    init {
        api.onSessionExpired = { sessions.signOut() }
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
