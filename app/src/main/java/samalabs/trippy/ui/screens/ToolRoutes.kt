package samalabs.trippy.ui.screens

import androidx.compose.runtime.Composable

/** Routes for search and device tools. Kept apart from MoreRoutes so parallel work doesn't collide. */
data object SearchRoute : Route

@Composable
fun ToolRouteScreen(route: Route, nav: Navigator) {
    when (route) {
        SearchRoute -> SearchScreen(onBack = { nav.pop() }, open = { t -> nav.push(t.toRoute()) })
        else -> MoreRouteScreen(route, nav)
    }
}

private fun samalabs.trippy.domain.SearchHit.Target.toRoute(): Route = when (this) {
    is samalabs.trippy.domain.SearchHit.Target.Trip -> Route.Trip(id)
    is samalabs.trippy.domain.SearchHit.Target.Flight -> Route.Flight(tripId, id)
    is samalabs.trippy.domain.SearchHit.Target.Stay -> Route.Stay(tripId, id)
    is samalabs.trippy.domain.SearchHit.Target.Rental -> Route.Rental(id)
}
