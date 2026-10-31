package samalabs.trippy.ui.screens

import androidx.compose.runtime.Composable

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
        TodayRoute -> TodayScreen(nav)
        DriversLogRoute -> DriversLogScreen(nav)
        RentalsRoute -> RentalsScreen(onOpen = { nav.push(Route.Rental(it)) })
        else -> Unit
    }
}
