package samalabs.trippy.ui.screens

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
import androidx.compose.material.icons.filled.PushPin
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
    val nav = remember { Navigator() }
    val now = rememberNow()
    LaunchedEffect(Unit) {
        container.trips.clear()
        container.trips.refresh()
    }
    BackHandler(enabled = nav.stack.size > 1) { nav.pop() }
    val depth = nav.stack.size
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
                Route.Trips -> TripsScreen(
                    state = state,
                    initials = session?.profile?.initialsOrGuess ?: "?",
                    now = now,
                    actions = TripsListActions(
                        open = { nav.push(Route.Trip(it)) },
                        refresh = { container.trips.refresh() },
                        account = { nav.push(Route.Account) },
                    ),
                )
                is Route.Trip -> {
                    val trip = state.trips.firstOrNull { it.id == route.id }
                    if (trip == null) TripPlaceholder(route.id, onBack = { nav.pop() })
                    else {
                        val snap = remember(trip, state.rentals, now.toLocalDate()) { TripSnapshot(trip, state.rentals, now) }
                        val journey = state.journeys.firstOrNull { route.id in it.tripIds }
                        val scope = rememberCoroutineScope()
                        TripDetailScreen(snap, journey, now, TripDetailActions(
                            close = { nav.pop() },
                            openEvent = { e ->
                                when {
                                    e.flight != null -> nav.push(Route.Flight(trip.id, e.flight.id))
                                    e.lodging != null -> nav.push(Route.Stay(trip.id, e.lodging.id))
                                    e.rental != null -> nav.push(Route.Rental(e.rental.id))
                                }
                            },
                            menu = listOf(
                                MenuAction(if (trip.pinned) "Unpin" else "Pin to top", Icons.Filled.PushPin) {
                                    scope.launch { container.trips.setPinned(trip.id, !trip.pinned) }
                                },
                            ),
                        ))
                    }
                }
                is Route.Flight -> state.trips.firstOrNull { it.id == route.tripId }?.flights?.firstOrNull { it.id == route.id }
                    ?.let { FlightDetailScreen(it, now, onClose = { nav.pop() }) } ?: TripPlaceholder(route.id, onBack = { nav.pop() })
                is Route.Stay -> state.trips.firstOrNull { it.id == route.tripId }?.lodgings?.firstOrNull { it.id == route.id }
                    ?.let { StayDetailScreen(it, now, onClose = { nav.pop() }) } ?: TripPlaceholder(route.id, onBack = { nav.pop() })
                is Route.Rental -> (state.rentals + state.trips.flatMap { it.rentalCars }).firstOrNull { it.id == route.id }
                    ?.let { RentalDetailScreen(it, now, onClose = { nav.pop() }) } ?: TripPlaceholder(route.id, onBack = { nav.pop() })
                Route.Account -> AccountScreen(onBack = { nav.pop() })
            }
        }
    }
}
