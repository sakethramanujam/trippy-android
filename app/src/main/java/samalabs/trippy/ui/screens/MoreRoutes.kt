package samalabs.trippy.ui.screens

import androidx.compose.runtime.Composable

/** Routes for the money, story and import screens. Kept here so MainShell only needs one `else` branch. */
data class ExpensesRoute(val tripId: Int) : Route

@Composable
fun MoreRouteScreen(route: Route, nav: Navigator) {
    when (route) {
        is ExpensesRoute -> ExpensesScreen(route.tripId, onBack = { nav.pop() })
        else -> Unit
    }
}
