package samalabs.trippy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import samalabs.trippy.data.ApiStatItem
import samalabs.trippy.data.ApiStats
import samalabs.trippy.data.ApiStayStats
import samalabs.trippy.data.StatsRepository
import samalabs.trippy.data.formatFlightHours
import samalabs.trippy.domain.DisplayName
import samalabs.trippy.domain.Fmt
import samalabs.trippy.ui.LocalContainer
import samalabs.trippy.ui.components.BrandLogos
import samalabs.trippy.ui.components.LogoTile
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType

/** Driver's Log tab with the server `/stats/` blocks (flights & trips, aircraft, airlines, stays, hotel chains) for the chosen year. */
@Composable
fun StatsDriversLog(nav: Navigator) {
    val c = LocalContainer.current
    val repo = remember { StatsRepository(c.api) }
    var years by remember { mutableStateOf(emptyList<Int>()) }
    var stats by remember { mutableStateOf<ApiStats?>(null) }
    var year by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) { runCatching { repo.years() }.onSuccess { years = it } }
    LaunchedEffect(year) { runCatching { repo.stats(year) }.onSuccess { stats = it } }
    DriversLogScreen(
        nav, statsYears = years,
        statsBlocks = { y -> if (y != year) LaunchedEffect(y) { year = y }; stats?.let { StatsBlocks(it) } },
        onRefresh = { y -> runCatching { repo.stats(y) }.onSuccess { stats = it } },
    )
}

@Composable
private fun ColumnScope.StatsBlocks(s: ApiStats) {
    LogCard(listOf(TColor.blue.copy(alpha = 0.9f), Color(0xFF0B2A6B), Color(0xFF071436))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("FLIGHTS & TRIPS", style = TType.monoLarge, color = Color.White, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Flight, null, tint = Color.White)
        }
        Row {
            HeroStat("Flights", "${s.flightCount}", Modifier.weight(1f))
            HeroStat("Distance", Fmt.number(s.flightMiles) + " mi", Modifier.weight(2f))
        }
        Row {
            HeroStat("Flight time", formatFlightHours(s.flights?.durationMinutes ?: 0), Modifier.weight(1f))
            HeroStat("Airports", "${s.airportCount}", Modifier.weight(1f))
            HeroStat("Trips", "${s.totalTrips}", Modifier.weight(1f))
        }
        LogFooter("Trip expenses", Fmt.money(s.usdExpenses, "USD", decimals = false))
    }
    if (s.logbookAircraft.isNotEmpty()) LogbookSection("Top aircraft", Icons.Filled.Flight, TColor.blue, s.logbookAircraft)
    if (s.logbookAirlines.isNotEmpty()) LogbookSection("Top airlines", Icons.Filled.ConfirmationNumber, TColor.purple, s.logbookAirlines)
    s.stays?.takeIf { it.count > 0 }?.let { st ->
        StaysCard(st)
        st.topBrands?.takeIf { it.isNotEmpty() }?.let { LogbookSection("Top hotel chains", Icons.Filled.Hotel, TColor.stay, it) }
    }
}

@Composable
private fun StaysCard(st: ApiStayStats) {
    val spend = st.totalCostByCurrency.orEmpty().entries.sortedByDescending { it.value }
    LogCard(listOf(TColor.stay.copy(alpha = 0.95f), Color(0xFF8A2E1C), Color(0xFF2A0E08))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("STAYS", style = TType.monoLarge, color = Color.White, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Hotel, null, tint = Color.White)
        }
        Row {
            HeroStat("Nights", "${st.nights}", Modifier.weight(1f))
            HeroStat("Hotels", "${st.count}", Modifier.weight(1f))
            HeroStat("Cities", "${st.cities ?: 0}", Modifier.weight(1f))
        }
        if (spend.isNotEmpty()) LogFooter("Spent on stays", spend.take(2).joinToString(" + ") { Fmt.money(it.value, it.key, decimals = false) })
        st.topBookingSources?.firstOrNull()?.let { Text("Booked most via ${it.name}", style = TType.caption, color = Color.White.copy(alpha = 0.7f)) }
    }
}

@Composable
private fun LogbookSection(title: String, icon: ImageVector, color: Color, items: List<ApiStatItem>) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TColor.surface)
            .border(1.dp, TColor.hairline, RoundedCornerShape(16.dp)),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Text("  " + title.uppercase(), style = TType.label, color = TColor.textSecondary)
        }
        items.forEachIndexed { i, item ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                val logo = BrandLogos.airline(item.name) ?: BrandLogos.rental(item.name)
                if (logo != null) LogoTile(logo, 50)
                else Box(Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)).background(TColor.surfaceRaised), Alignment.Center) { Icon(icon, null, tint = Color.Gray) }
                Text(DisplayName.humanize(item.name), style = TType.calloutStrong, color = Color.White, modifier = Modifier.weight(1f).padding(horizontal = 16.dp))
                Text("${item.count}x", style = TType.callout, color = color,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 4.dp))
            }
            if (i != items.lastIndex) HorizontalDivider(Modifier.padding(start = 82.dp), color = TColor.surfaceRaised)
        }
    }
}
