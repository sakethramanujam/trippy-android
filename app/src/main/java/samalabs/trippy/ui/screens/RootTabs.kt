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
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import samalabs.trippy.ui.components.plainClick
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType

/** Tab roots, as on iOS. Tabs swap the bottom of the nav stack. */
data object RentalsRoute : Route

private class RootTab(val title: String, val icon: ImageVector, val route: Route)

private val rootTabs = listOf(
    RootTab("Trips", Icons.Filled.Flight, Route.Trips),
    RootTab("Rentals", Icons.Filled.DirectionsCar, RentalsRoute),
)

/** The first tab; back from any other tab root returns here. */
internal val homeRoute: Route get() = rootTabs.first().route

/** Floating tab bar shown on tab roots only. */
@Composable
fun RootTabBar(nav: Navigator, modifier: Modifier = Modifier) {
    val current = nav.stack.firstOrNull()
    BackHandler(enabled = nav.stack.size == 1 && current != homeRoute) { nav.stack[0] = homeRoute }
    Row(
        modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 10.dp).fillMaxWidth()
            .clip(RoundedCornerShape(28.dp)).background(TColor.surfaceHigh.copy(alpha = 0.96f)).padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        rootTabs.forEach { t ->
            val on = t.route == current
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(if (on) TColor.coral.copy(alpha = 0.16f) else TColor.surfaceHigh.copy(alpha = 0f))
                    .plainClick { if (!on) nav.stack[0] = t.route }.padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(t.icon, t.title, tint = if (on) TColor.coral else TColor.textSecondary, modifier = Modifier.size(22.dp))
                Text(t.title, style = TType.micro, color = if (on) TColor.coral else TColor.textSecondary)
            }
        }
    }
}
