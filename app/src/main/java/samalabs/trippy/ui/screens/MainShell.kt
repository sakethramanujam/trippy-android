package samalabs.trippy.ui.screens

import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.Alignment
import kotlinx.coroutines.CoroutineScope
import samalabs.trippy.AppContainer
import samalabs.trippy.data.ApiTrip
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Route as RouteIcon
import androidx.compose.material.icons.filled.RemoveCircleOutline
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import samalabs.trippy.domain.TripEvent
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import samalabs.trippy.domain.TripSnapshot
import samalabs.trippy.ui.LocalContainer
import samalabs.trippy.ui.theme.TColor
import java.time.LocalDateTime

/** Where the user is. A plain back stack: the app is small and every screen is one level deep. */
sealed interface Route {
    data object Trips : Route
    data class Trip(val id: Int) : Route
    data object Account : Route
    data class Flight(val tripId: Int, val id: Int) : Route
    data class Stay(val tripId: Int, val id: Int) : Route
    data class Rental(val id: Int) : Route
    data class Journey(val id: Int) : Route
    data object Trash : Route
}

class Navigator {
    val stack = mutableStateListOf<Route>(Route.Trips)
    val top: Route get() = stack.last()
    fun push(r: Route) { stack.add(r) }
    fun pop(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
}

/** A clock that ticks every 30 s so countdowns stay live. */
@Composable
fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalDateTime.now() } }
    return now
}

@Composable
fun MainShell() {
    val container = LocalContainer.current
    val session by container.sessions.session.collectAsState()
    val state by container.trips.state.collectAsState()
    val suggestions by container.journeys.suggestions.collectAsState()
    val journeyDetails by container.journeys.details.collectAsState()
    val nav = remember { Navigator().apply { stack[0] = homeRoute } }
    val context = LocalContext.current
    val shellScope = rememberCoroutineScope()
    fun toastIfError(err: String?) { err?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } }
    fun openEvent(tripId: Int, e: TripEvent) {
        when {
            e.flight != null -> nav.push(Route.Flight(tripId, e.flight.id))
            e.lodging != null -> nav.push(Route.Stay(tripId, e.lodging.id))
            e.rental != null -> nav.push(Route.Rental(e.rental.id))
        }
    }
    val now = rememberNow()
    LaunchedEffect(Unit) {
        container.trips.clear()
        container.journeys.clear()
        container.trips.refresh()
        container.journeys.loadSuggestions()
    }
    BackHandler(enabled = nav.stack.size > 1) { nav.pop() }
    val depth = nav.stack.size
    val myId = session?.profile?.id
    val owns: (ApiTrip) -> Boolean = { t -> t.ownerId == null || t.ownerId == myId }
    var deleting by remember { mutableStateOf<List<Int>?>(null) }
    val listUi = remember { TripListUi() }
    fun createJourney(ids: List<Int>) { shellScope.launch { val (id, err) = container.journeys.create(null, ids); toastIfError(err); if (err == null && id != null) nav.push(Route.Journey(id)) } }
    Box(Modifier.fillMaxSize().background(TColor.canvas)) {
        AnimatedContent(
            targetState = nav.top to depth, label = "nav",
            transitionSpec = {
                val forward = targetState.second >= initialState.second
                (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
            },
        ) { (route, _) ->
            when (route) {
                Route.Trips -> {
                    val listActions = TripsListActions(
                        open = { nav.push(Route.Trip(it)) },
                        refresh = { container.trips.refresh(); container.journeys.loadSuggestions() },
                        account = { nav.push(Route.Account) },
                        search = { nav.push(SearchRoute) },
                    )
                    val snapsById = remember(state.trips, state.rentals, now.toLocalDate()) {
                        state.trips.associate { it.id to TripSnapshot(it, state.rentals, now) }
                    }
                    val journeyActions = JourneyListActions(
                        open = { nav.push(Route.Journey(it)) },
                        accept = { s -> shellScope.launch { val (id, err) = container.journeys.accept(s); toastIfError(err); if (err == null && id != null) nav.push(Route.Journey(id)) } },
                        dismiss = { s -> shellScope.launch { container.journeys.dismiss(s) } },
                    )
                    TripsScreen(
                        state = state,
                        initials = session?.profile?.initialsOrGuess ?: "?",
                        now = now,
                        actions = listActions,
                        journeyTripIds = remember(state.journeys) { state.journeys.flatMap { it.tripIds }.toSet() },
                        journeysSection = { row -> journeysSection(state.journeys, suggestions, snapsById, now, journeyActions, listActions, row) },
                        row = { s, content -> TripActionRow(listUi, s, rowCallbacks(owns, nav, shellScope, container) { deleting = it }, content) },
                    )
                    if (listUi.selecting) Box(Modifier.fillMaxSize(), Alignment.BottomCenter) { SelectionBar(listUi, delete = { deleting = it }) }
                    listUi.linking?.let { ids ->
                        LinkTripsFlow(ids, state.journeys, state.trips, onDismiss = { listUi.linking = null },
                            addTo = { jid, l -> listUi.linking = null; listUi.endSelect(); shellScope.launch { toastIfError(container.journeys.addTrips(jid, l)) } },
                            create = { l -> listUi.linking = null; listUi.endSelect(); createJourney(l) })
                    }
                }
                is Route.Journey -> {
                    LaunchedEffect(route.id) { container.journeys.load(route.id)?.let { if (journeyDetails[route.id] == null) toastIfError(it) } }
                    val j = journeyDetails[route.id] ?: state.journeys.firstOrNull { it.id == route.id }
                    fun edit(block: suspend () -> String?) { shellScope.launch { toastIfError(block()) } }
                    JourneyDetailScreen(j, null, state.trips, state.rentals, now, JourneyDetailActions(
                        close = { nav.pop() },
                        openTrip = { nav.push(Route.Trip(it)) },
                        openEvent = { tid, e -> openEvent(tid, e) },
                        rename = { t -> edit { container.journeys.rename(route.id, t) } },
                        addTrips = { ids -> edit { container.journeys.addTrips(route.id, ids) } },
                        removeTrip = { tid -> edit { container.journeys.removeTrip(route.id, tid) } },
                        reorder = { ids -> edit { container.journeys.reorder(route.id, ids) } },
                    ))
                }
                is Route.Trip -> {
                    val trip = state.trips.firstOrNull { it.id == route.id }
                    if (trip == null) TripPlaceholder(route.id, onBack = { nav.pop() })
                    else {
                        val snap = remember(trip, state.rentals, now.toLocalDate()) { TripSnapshot(trip, state.rentals, now) }
                        val journey = state.journeys.firstOrNull { route.id in it.tripIds }
                        val scope = rememberCoroutineScope()
                        var choosing by remember { mutableStateOf(false) }
                        var startingNew by remember { mutableStateOf(false) }
                        var inviting by remember { mutableStateOf(false) }
                        TripDetailScreen(snap, journey, now, TripDetailActions(
                            close = { nav.pop() },
                            openEvent = { e -> openEvent(trip.id, e) },
                            openJourney = { nav.push(Route.Journey(it)) },
                            menu = buildList {
                                add(MenuAction(if (trip.pinned) "Unpin" else "Pin to top", Icons.Filled.PushPin) {
                                    scope.launch { container.trips.setPinned(trip.id, !trip.pinned) }
                                })
                                add(MenuAction("Story", Icons.Filled.AutoStories) { nav.push(StoryRoute(trip.id)) })
                                add(MenuAction("Share card", Icons.Filled.IosShare) { nav.push(ShareCardRoute(trip.id)) })
                                add(MenuAction("Expenses", Icons.Filled.AccountBalanceWallet) { nav.push(ExpensesRoute(trip.id)) })
                                add(MenuAction("Invite travelers", Icons.Filled.PersonAdd) { inviting = true })
                                add(MenuAction("Import into trip", Icons.Filled.FileOpen) { nav.push(ImportRoute(trip.id)) })
                                if (journey == null) add(MenuAction("Add to a journey…", Icons.Filled.RouteIcon) { choosing = true })
                                else if (journey.isOwner) add(MenuAction("Remove from journey", Icons.Filled.RemoveCircleOutline, destructive = true) {
                                    shellScope.launch { toastIfError(container.journeys.removeTrip(journey.id, trip.id)) }
                                })
                                if (owns(trip)) add(MenuAction("Delete trip…", Icons.Filled.Delete, destructive = true) { deleting = listOf(trip.id) })
                                else add(MenuAction("Leave trip…", Icons.AutoMirrored.Filled.ExitToApp, destructive = true) { deleting = listOf(trip.id) })
                            },
                        ))
                        if (inviting) InviteTravelersFlow(trip) { inviting = false }
                        if (choosing) JourneyChooserDialog(
                            state.journeys, onDismiss = { choosing = false },
                            onPick = { jid -> choosing = false; shellScope.launch { toastIfError(container.journeys.addTrips(jid, listOf(trip.id))) } },
                            onNew = { choosing = false; startingNew = true },
                        )
                        if (startingNew) TripPickerSheet(
                            "New journey with ${trip.title}", state.trips.filter { it.id != trip.id && it.journeyId == null && state.journeys.none { j -> it.id in j.tripIds } },
                            "Link", onDismiss = { startingNew = false },
                        ) { ids ->
                            startingNew = false
                            shellScope.launch {
                                val (id, err) = container.journeys.create(null, listOf(trip.id) + ids)
                                toastIfError(err); if (err == null && id != null) nav.push(Route.Journey(id))
                            }
                        }
                    }
                }
                is Route.Flight -> state.trips.firstOrNull { it.id == route.tripId }?.flights?.firstOrNull { it.id == route.id }
                    ?.let { FlightDetailScreen(it, now, onClose = { nav.pop() }) } ?: TripPlaceholder(route.id, onBack = { nav.pop() })
                is Route.Stay -> state.trips.firstOrNull { it.id == route.tripId }?.lodgings?.firstOrNull { it.id == route.id }
                    ?.let { StayDetailScreen(it, now, onClose = { nav.pop() }) } ?: TripPlaceholder(route.id, onBack = { nav.pop() })
                is Route.Rental -> (state.rentals + state.trips.flatMap { it.rentalCars }).firstOrNull { it.id == route.id }
                    ?.let { RentalDetailScreen(it, now, onClose = { nav.pop() }, extras = rentalDetailExtras(it, now)) } ?: TripPlaceholder(route.id, onBack = { nav.pop() })
                Route.Account -> ProfileScreen(nav, cards = { DeviceSettings() })
                Route.Trash -> RecentlyDeletedScreen(onBack = { nav.pop() })
                else -> ToolRouteScreen(route, nav)
            }
        }
        if (depth == 1 && !listUi.selecting) RootTabBar(nav, Modifier.align(Alignment.BottomCenter))
        UndoToastHost(Modifier.align(Alignment.BottomCenter))
    }
    deleting?.let { ids ->
        DeleteTripSheet(ids, onDismiss = { deleting = null }) {
            deleting = null
            listUi.endSelect()
            val idx = nav.stack.indexOfFirst { it is Route.Trip && it.id in ids }
            if (idx > 0) while (nav.stack.size > idx) nav.pop()
        }
    }
}

private fun rowCallbacks(owns: (ApiTrip) -> Boolean, nav: Navigator, scope: CoroutineScope, c: AppContainer, delete: (List<Int>) -> Unit) = TripRowCallbacks(
    owns = owns,
    pin = { t -> scope.launch { c.trips.setPinned(t.id, !t.pinned) } },
    share = { nav.push(ShareCardRoute(it)) },
    story = { nav.push(StoryRoute(it)) },
    delete = delete,
)
