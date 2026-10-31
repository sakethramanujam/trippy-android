package samalabs.trippy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import samalabs.trippy.data.ApiFlight
import samalabs.trippy.data.ApiTrip
import samalabs.trippy.domain.Fmt
import samalabs.trippy.domain.airlineName
import samalabs.trippy.domain.arrivalDate
import samalabs.trippy.domain.countdown
import samalabs.trippy.domain.departureDate
import samalabs.trippy.domain.displayTitle
import samalabs.trippy.domain.durationText
import samalabs.trippy.domain.fromCode
import samalabs.trippy.domain.number
import samalabs.trippy.domain.phase
import samalabs.trippy.domain.pastRentals
import samalabs.trippy.domain.liveRentals
import samalabs.trippy.domain.progress
import samalabs.trippy.domain.statusTone
import samalabs.trippy.domain.StatusTone
import samalabs.trippy.domain.toCode
import samalabs.trippy.ui.LocalContainer
import samalabs.trippy.ui.components.AirlineBadge
import samalabs.trippy.ui.components.Pill
import samalabs.trippy.ui.components.PrimaryButton
import samalabs.trippy.ui.components.plainClick
import samalabs.trippy.ui.components.trippyCard
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Purple-to-blue initials circle used for the signed-in user (iOS `InitialsAvatar`). */
@Composable
fun InitialsAvatar(initials: String, size: Int, dot: Boolean = false) {
    Box {
        Box(
            Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(listOf(TColor.purple, TColor.blue))),
            Alignment.Center,
        ) { Text(initials, style = if (size >= 56) TType.title2 else TType.footnoteStrong, color = Color.White) }
        if (dot) Box(
            Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp).size(11.dp).clip(CircleShape)
                .background(TColor.canvas).padding(2.dp).clip(CircleShape).background(TColor.red),
        )
    }
}

/** "Today": the trip coming up and its first flight, the car you're driving (or the last one), quick actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(nav: Navigator, unreadEmails: Int = 0) {
    val container = LocalContainer.current
    val state by container.trips.state.collectAsState()
    val session by container.sessions.session.collectAsState()
    val now = rememberNow()
    val scope = rememberCoroutineScope()
    var showServer by rememberSaveable { mutableStateOf(false) }
    val trip = remember(state.trips, now.toLocalDate()) { nextTrip(state.trips, now) }

    PullToRefreshBox(isRefreshing = state.refreshing && state.loaded, onRefresh = { scope.launch { container.trips.refresh() } }, modifier = Modifier.fillMaxSize().background(TColor.canvas)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Today", style = TType.largeTitle, color = TColor.textPrimary)
                    Text(Fmt.dayYear(now), style = TType.callout, color = TColor.textSecondary)
                }
                Box(Modifier.padding(top = 6.dp).plainClick { nav.push(Route.Account) }) {
                    InitialsAvatar(session?.profile?.initialsOrGuess ?: "?", 38, dot = unreadEmails > 0)
                }
            }

            when {
                state.trips.isEmpty() && state.rentals.isEmpty() && state.loaded && state.error == null && !state.offline -> NewAccount(nav)
                state.trips.isEmpty() && state.rentals.isEmpty() -> Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator(color = Color.White)
                    Text("Connecting to ${container.sessions.current.server}…", style = TType.footnote, color = TColor.textSecondary)
                    state.error?.let { Text(it, style = TType.footnote, color = TColor.red) }
                    Text("Change server…", style = TType.calloutStrong, color = TColor.blue, modifier = Modifier.plainClick { showServer = true })
                }
                trip != null -> Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    TripHero(trip, now) { nav.push(Route.Trip(trip.id)) }
                    trip.flights.firstOrNull()?.let { f -> TodayFlightCard(f, now) { nav.push(Route.Flight(trip.id, f.id)) } }
                }
            }

            val live = remember(state.rentals, now) { state.rentals.liveRentals(now) }
            (live.firstOrNull() ?: state.rentals.pastRentals(now).firstOrNull())?.let { r ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(if (live.isEmpty()) "Last rental" else "Your car", style = TType.title3, color = TColor.textPrimary, modifier = Modifier.weight(1f))
                        if (live.isNotEmpty()) Text("${live.size} upcoming", style = TType.footnote, color = TColor.textSecondary)
                    }
                    RentalCard(r, now) { nav.push(Route.Rental(r.id)) }
                }
            }

            if (state.trips.isNotEmpty() || state.rentals.isNotEmpty()) Column {
                HorizontalDivider(color = TColor.divider)
                trip?.let { t -> QuickTile(Icons.Filled.CreditCard, "Split expenses", TColor.orange) { nav.push(ExpensesRoute(t.id)) }; HorizontalDivider(Modifier.padding(start = 44.dp), color = TColor.divider) }
                QuickTile(Icons.Filled.DirectionsCar, "All rentals", TColor.green) { nav.stack[0] = RentalsRoute }
                HorizontalDivider(Modifier.padding(start = 44.dp), color = TColor.divider)
                QuickTile(Icons.Filled.Download, "Import trips", TColor.blue) { nav.push(ImportRoute(null)) }
                HorizontalDivider(color = TColor.divider)
            }
            Spacer(Modifier.height(110.dp))
        }
    }
    if (showServer) ServerSettingsSheet(onDismiss = { showServer = false }, onSaved = { showServer = false; scope.launch { container.trips.refresh() } })
}

/** The trip that ends next from now; else the first one (iOS `nextTrip`). */
internal fun nextTrip(trips: List<ApiTrip>, now: LocalDateTime): ApiTrip? =
    trips.mapNotNull { t -> Fmt.parse(t.endDate ?: t.startDate)?.let { t to it } }
        .filter { !it.second.isBefore(now.toLocalDate().atStartOfDay()) }
        .minByOrNull { it.second }?.first ?: trips.firstOrNull()

@Composable
private fun NewAccount(nav: Navigator) {
    Column(Modifier.fillMaxWidth().trippyCard().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.FlightTakeoff, null, tint = TColor.blue, modifier = Modifier.size(34.dp))
            Text("No trips yet", style = TType.title, color = TColor.textPrimary)
            Text("Bring in what's already in your calendar, Flighty, Wallet or inbox, or start a trip from scratch.", style = TType.callout, color = TColor.textSecondary)
        }
        PrimaryButton("Import trips", onClick = { nav.push(ImportRoute(null)) }, modifier = Modifier.fillMaxWidth())
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TColor.blue.copy(alpha = 0.14f)).plainClick { nav.stack[0] = Route.Trips }.padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, null, tint = TColor.blue, modifier = Modifier.size(18.dp))
            Text("  Plan a new trip", style = TType.calloutStrong, color = TColor.blue)
        }
    }
}

@Composable
private fun TripHero(trip: ApiTrip, now: LocalDateTime, onClick: () -> Unit) {
    val start = Fmt.parse(trip.startDate)
    val end = Fmt.parse(trip.endDate ?: trip.startDate)
    val onTrip = start != null && !start.isAfter(now) && end != null && !now.isAfter(end.plusDays(1))
    val days = start?.let { maxOf(0, ChronoUnit.DAYS.between(now.toLocalDate(), it.toLocalDate())) } ?: 0
    val codes = trip.flights.flatMap { listOf(it.fromCode, it.toCode) }.filter { it.isNotEmpty() }
        .fold(mutableListOf<String>()) { acc, c -> if (acc.lastOrNull() != c) acc.add(c); acc }
    Column(Modifier.fillMaxWidth().plainClick(onClick), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(if (onTrip) "Happening now" else "Up next", TColor.amber)
            Spacer(Modifier.weight(1f))
            start?.let { Text(Fmt.monthDay(it).uppercase(), style = TType.micro, color = TColor.textSecondary) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (start != null) {
                Column(Modifier.widthIn(min = 80.dp)) {
                    Text("$days", style = TType.hero, color = if (onTrip) TColor.green else Color.White)
                    Text(if (onTrip) "ON TRIP" else if (days == 1L) "DAY TO GO" else "DAYS TO GO", style = TType.label, color = TColor.textSecondary)
                }
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(trip.displayTitle, style = TType.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (codes.isNotEmpty()) Text(codes.joinToString(" → "), style = TType.calloutStrong, color = TColor.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    HeroChip(Icons.Filled.Group, trip.participants.size)
                    HeroChip(Icons.Filled.Flight, trip.flights.size)
                    HeroChip(Icons.Filled.CreditCard, trip.expenses.size)
                }
            }
        }
    }
}

@Composable
private fun HeroChip(icon: ImageVector, n: Int) {
    Row(Modifier.background(TColor.surfaceRaised, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = TColor.textSecondary, modifier = Modifier.size(12.dp))
        Text(" $n", style = TType.caption, color = TColor.textSecondary)
    }
}

/** Flighty-style card for the trip's first flight. */
@Composable
fun TodayFlightCard(f: ApiFlight, now: LocalDateTime, onClick: () -> Unit) {
    val tone = when (f.statusTone) { StatusTone.GREEN -> TColor.green; StatusTone.AMBER -> TColor.amber; StatusTone.RED -> TColor.red; StatusTone.GRAY -> TColor.textSecondary }
    val d = f.departureDate; val a = f.arrivalDate
    Column(Modifier.fillMaxWidth().trippyCard().plainClick(onClick).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AirlineBadge(f.airline, f.flightNumber, 30)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(listOf(f.airlineName, f.number).filter { it.isNotBlank() }.joinToString(" · "), style = TType.headline, color = TColor.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                d?.let { Text(Fmt.day(it), style = TType.footnote, color = TColor.textSecondary) }
            }
            f.status?.takeIf { it.isNotBlank() }?.let { Text(it.uppercase(), style = TType.micro, color = tone, modifier = Modifier.border(1.dp, tone.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(f.fromCode.ifEmpty { "—" }, style = TType.code, color = TColor.textPrimary)
                d?.let { Text(Fmt.time(it), style = TType.timeSmall, color = TColor.textPrimary) }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Flight, null, tint = TColor.textSecondary, modifier = Modifier.size(18.dp).rotate(90f))
                f.durationText?.let { Text(it, style = TType.caption, color = TColor.textSecondary) }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(f.toCode.ifEmpty { "—" }, style = TType.code, color = TColor.textPrimary)
                a?.let { Text(Fmt.time(it), style = TType.timeSmall, color = TColor.textPrimary) }
            }
        }
        val p = f.progress(now)
        if (p > 0 && p < 1) LinearProgressIndicator(progress = { p.toFloat() }, color = TColor.blue, trackColor = TColor.surfaceRaised, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)))
        f.countdown(now)?.let { Text(it, style = TType.calloutStrong, color = if (it == "Landed") TColor.textSecondary else TColor.blue) }
    }
}

@Composable
private fun QuickTile(icon: ImageVector, title: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().plainClick(onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(32.dp), Alignment.CenterStart) { Icon(icon, null, tint = color, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(title, style = TType.body, color = Color.White, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TColor.textTertiary)
    }
}
