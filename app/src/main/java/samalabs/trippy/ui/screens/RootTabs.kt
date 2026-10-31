package samalabs.trippy.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import samalabs.trippy.ui.components.plainClick
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType

/** Tab roots, as on iOS. Tabs swap the bottom of the nav stack. */
data object TodayRoute : Route
data object RentalsRoute : Route

private class RootTab(val title: String, val icon: ImageVector, val route: Route)

private val rootTabs = listOf(
    RootTab("Today", Icons.Filled.WbTwilight, TodayRoute),
    RootTab("Rentals", Icons.Filled.DirectionsCar, RentalsRoute),
    RootTab("Trips", Icons.Filled.Map, Route.Trips),
)

/** The first tab; back from any other tab root returns here. */
internal val homeRoute: Route get() = rootTabs.first().route

/** Bottom tab bar (iOS style: gray idle, white selected), shown on tab roots only. */
@Composable
fun RootTabBar(nav: Navigator, modifier: Modifier = Modifier) {
    val current = nav.stack.firstOrNull()
    BackHandler(enabled = nav.stack.size == 1 && current != homeRoute) { nav.stack[0] = homeRoute }
    Column(modifier.fillMaxWidth().background(TColor.canvas)) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.10f), thickness = 0.5.dp)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 6.dp, bottom = 4.dp)) {
            rootTabs.forEach { t ->
                val on = t.route == current
                val tint = if (on) Color.White else TColor.textTertiary
                Column(Modifier.weight(1f).plainClick { if (!on) nav.stack[0] = t.route }.padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(t.icon, t.title, tint = tint, modifier = Modifier.size(24.dp))
                    Text(t.title, style = TType.micro, color = tint)
                }
            }
        }
    }
}
