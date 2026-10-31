package samalabs.trippy.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/** Routes for the money, story and import screens. Kept here so MainShell only needs one `else` branch. */
data class ExpensesRoute(val tripId: Int) : Route
data class ImportRoute(val tripId: Int?) : Route
data class StoryRoute(val tripId: Int) : Route
data class ShareCardRoute(val tripId: Int?, val year: Int? = null) : Route

@Composable
fun MoreRouteScreen(route: Route, nav: Navigator) {
    when (route) {
        is ExpensesRoute -> ExpensesScreen(route.tripId, onBack = { nav.pop() })
        is ImportRoute -> ImportScreen(route.tripId, onBack = { nav.pop() })
        is StoryRoute -> StoryScreen(route.tripId, onBack = { nav.pop() }, onShare = { nav.push(ShareCardRoute(route.tripId)) })
        is ShareCardRoute -> ShareCardScreen(route.tripId, route.year, onBack = { nav.pop() })
        TodayRoute -> {
            val inbound = samalabs.trippy.ui.LocalContainer.current.inbound
            val unread by inbound.unread.collectAsState()
            LaunchedEffect(Unit) { inbound.refreshBadge() }
            TodayScreen(nav, unread)
        }
        InboxRoute -> ForwardedEmailsScreen(onBack = { nav.pop() })
        DriversLogRoute -> StatsDriversLog(nav)
        RentalsRoute -> RentalsScreen(onOpen = { nav.push(Route.Rental(it)) })
        else -> Unit
    }
}
