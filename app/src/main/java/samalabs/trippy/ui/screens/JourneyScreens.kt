package samalabs.trippy.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import samalabs.trippy.data.ApiJourney
import samalabs.trippy.data.ApiRentalCar
import samalabs.trippy.data.ApiTrip
import samalabs.trippy.data.JourneyLeg
import samalabs.trippy.data.JourneySuggestion
import samalabs.trippy.data.costText
import samalabs.trippy.data.dateText
import samalabs.trippy.data.subtitle
import samalabs.trippy.domain.Fmt
import samalabs.trippy.domain.LatLng
import samalabs.trippy.domain.TripSnapshot
import samalabs.trippy.domain.displayTitle
import samalabs.trippy.ui.components.ArtStop
import samalabs.trippy.ui.components.CircleIcon
import samalabs.trippy.ui.components.MicroLabel
import samalabs.trippy.ui.components.RouteArt
import samalabs.trippy.ui.components.plainClick
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType
import java.time.LocalDateTime

object JourneyPalette {
    val colors = listOf(TColor.blue, TColor.orange, TColor.green, TColor.purple, TColor.teal, TColor.coral)
    fun color(leg: Int): Color = colors[Math.floorMod(maxOf(leg - 1, 0), colors.size)]
}

@Composable
fun LegStripe(count: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(maxOf(count, 1)) { i -> Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(JourneyPalette.color(i + 1))) }
    }
}

@Composable
private fun GrayChip(text: String, icon: ImageVector) {
    Row(
        Modifier.border(1.dp, TColor.divider, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = TColor.textSecondary, modifier = Modifier.size(12.dp))
        Text(text, style = TType.caption, color = TColor.textSecondary, maxLines = 1)
    }
}

// Trips list

class JourneyListActions(
    val open: (Int) -> Unit,
    val accept: (JourneySuggestion) -> Unit,
    val dismiss: (JourneySuggestion) -> Unit,
)

/** Suggestion banners, then a "Journeys" section of stacked cards that expand to their legs. */
fun LazyListScope.journeysSection(
    journeys: List<ApiJourney>, suggestions: List<JourneySuggestion>, snaps: Map<Int, TripSnapshot>,
    now: LocalDateTime, actions: JourneyListActions, tripActions: TripsListActions,
    row: @Composable (TripSnapshot, @Composable () -> Unit) -> Unit,
) {
    suggestions.take(2).forEach { s ->
        item(key = "sug-${s.key}") { Box(Modifier.sheet().padding(horizontal = 20.dp).padding(top = 18.dp)) { SuggestionBanner(s, actions) } }
    }
    if (journeys.isEmpty()) return
    item(key = "h-journeys") { SectionHeader("Journeys", "${journeys.size}") }
    journeys.forEach { j ->
        item(key = "j-${j.id}") {
            var expanded by rememberSaveable(j.id) { mutableStateOf(false) }
            Column(Modifier.sheet()) {
                JourneyCard(j, expanded, onToggle = { expanded = !expanded }, onOpen = { actions.open(j.id) })
                AnimatedVisibility(expanded) {
                    Column {
                        j.tripIds.mapNotNull { snaps[it] }.forEachIndexed { i, s ->
                            Row(Modifier.height(IntrinsicSize.Min)) {
                                Box(Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp).width(3.dp).fillMaxHeight().background(JourneyPalette.color(i + 1)))
                                Box(Modifier.weight(1f)) { row(s) { TripRow(s, now, false, tripActions) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneyCard(j: ApiJourney, expanded: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).padding(bottom = 12.dp)) {
        Box(Modifier.matchParentSize().padding(horizontal = 16.dp).offset(y = 12.dp).clip(RoundedCornerShape(16.dp)).background(TColor.surfaceHigh.copy(alpha = 0.55f)))
        Box(Modifier.matchParentSize().padding(horizontal = 8.dp).offset(y = 6.dp).clip(RoundedCornerShape(16.dp)).background(TColor.surfaceRaised))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TColor.surface)
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)).plainClick(onOpen).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Route, null, tint = TColor.blue, modifier = Modifier.size(13.dp))
                Text(" JOURNEY", style = TType.micro, color = TColor.blue, modifier = Modifier.weight(1f))
                Text(j.dateText, style = TType.footnote, color = TColor.textSecondary)
            }
            Text(j.title, style = TType.title3, color = TColor.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            LegStripe(maxOf(j.tripCount, j.tripIds.size))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                GrayChip("${maxOf(j.tripCount, j.tripIds.size)} trips", Icons.Filled.Layers)
                if (j.stats.days > 0) GrayChip("${j.stats.days}d", Icons.Filled.CalendarMonth)
                if (j.stats.flights > 0) GrayChip("${j.stats.flights}", Icons.Filled.Flight)
                if (j.stats.nights > 0) GrayChip("${j.stats.nights}n", Icons.Filled.Bed)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(30.dp).clip(CircleShape).background(TColor.surfaceHigh).plainClick(onToggle), Alignment.Center) {
                    Icon(Icons.Filled.KeyboardArrowDown, if (expanded) "Collapse trips" else "Show trips", tint = Color.White,
                        modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f))
                }
            }
        }
    }
}

/** Behind-the-card pages need the card's size; a Box overlay with fillMaxSize does that inside the parent Box. */
@Composable
private fun Modifier.matchParentSize(): Modifier = this.fillMaxWidth().height(118.dp)

@Composable
private fun SuggestionBanner(s: JourneySuggestion, actions: JourneyListActions) {
    var busy by remember(s.key) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TColor.blue.copy(alpha = 0.10f))
            .border(1.dp, TColor.blue.copy(alpha = 0.30f), RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row {
            Icon(Icons.Filled.Link, null, tint = TColor.blue, modifier = Modifier.size(24.dp))
            Column(Modifier.padding(start = 10.dp)) {
                Text(if (s.journeyId == null) "Link these into one journey?" else "Add to your journey?", style = TType.calloutStrong, color = TColor.textPrimary)
                Text(s.trips.joinToString(" + ") { it.title }, style = TType.footnote, color = TColor.textSecondary, maxLines = 2)
            }
        }
        s.reasons.take(3).forEach { r ->
            Row {
                Icon(Icons.Filled.Check, null, tint = TColor.green, modifier = Modifier.size(13.dp).padding(top = 2.dp))
                Text(" $r", style = TType.caption, color = TColor.textSecondary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(TColor.blue.copy(alpha = 0.18f))
                    .plainClick { if (!busy) { busy = true; actions.accept(s) } }.padding(vertical = 11.dp, horizontal = 10.dp),
                Alignment.Center,
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = TColor.blue, strokeWidth = 2.dp)
                else Text("Link as “${s.title}”", style = TType.calloutStrong, color = TColor.blue, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f)).plainClick { actions.dismiss(s) }.padding(vertical = 11.dp, horizontal = 14.dp)) {
                Text("Dismiss", style = TType.calloutStrong, color = TColor.textSecondary)
            }
        }
    }
}

// Detail

class JourneyDetailActions(
    val close: () -> Unit,
    val openTrip: (Int) -> Unit,
    val openEvent: (Int, samalabs.trippy.domain.TripEvent) -> Unit,
    val rename: (String) -> Unit,
    val addTrips: (List<Int>) -> Unit,
    val removeTrip: (Int) -> Unit,
    val reorder: (List<Int>) -> Unit,
    val headerButtons: @Composable () -> Unit = {},
)

fun journeyArt(j: ApiJourney): List<ArtStop> {
    val legs = j.legs
    if (legs.isEmpty()) return j.route.mapNotNull { p -> if (p.lat != null && p.lng != null) ArtStop(p.code ?: p.city ?: "", LatLng(p.lat, p.lng)) else null }
    val out = mutableListOf<ArtStop>()
    legs.forEach { leg ->
        val c = JourneyPalette.color(leg.leg)
        leg.route.forEach { p ->
            if (p.lat == null || p.lng == null) return@forEach
            val code = p.code ?: p.city ?: ""
            if (out.lastOrNull()?.code == code) return@forEach
            out += ArtStop(code, LatLng(p.lat, p.lng), c)
        }
    }
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyDetailScreen(
    journey: ApiJourney?, error: String?, trips: List<ApiTrip>, rentals: List<ApiRentalCar>,
    now: LocalDateTime, actions: JourneyDetailActions,
) {
    val scroll = rememberScrollState()
    var renaming by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var reordering by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<JourneyLeg?>(null) }
    var menu by remember { mutableStateOf(false) }
    val art = remember(journey) { journey?.let(::journeyArt).orEmpty() }
    Box(Modifier.fillMaxSize().background(TColor.canvas)) {
        RouteArt(art, Modifier.fillMaxWidth().height(340.dp), topInset = 60.dp, bottomInset = 90.dp)
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Spacer(Modifier.height(280.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(TColor.surfaceSheet)
                    .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 5.dp).background(TColor.surfaceHigh, CircleShape))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("JOURNEY • ${journey?.dateText?.uppercase() ?: ""}", style = TType.label, color = TColor.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(journey?.title ?: "Journey", style = TType.title2, color = TColor.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        actions.headerButtons()
                        if (journey?.isOwner == true) Box {
                            CircleIcon(Icons.Filled.MoreHoriz, "More", onClick = { menu = true })
                            DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = TColor.surfaceRaised) {
                                DropdownMenuItem({ Text("Rename") }, onClick = { menu = false; renaming = true }, leadingIcon = { Icon(Icons.Filled.Edit, null) })
                                DropdownMenuItem({ Text("Add trips…") }, onClick = { menu = false; adding = true }, leadingIcon = { Icon(Icons.Filled.Add, null) })
                                DropdownMenuItem({ Text("Reorder…") }, onClick = { menu = false; reordering = true }, leadingIcon = { Icon(Icons.Filled.SwapVert, null) })
                            }
                        }
                        CircleIcon(Icons.Filled.Close, "Close", onClick = actions.close)
                    }
                }
                when {
                    journey == null && error != null -> Text(error, style = TType.callout, color = TColor.red)
                    journey == null -> Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { CircularProgressIndicator(color = TColor.coral) }
                    else -> {
                        HeroStats(journey)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Itinerary", style = TType.title3, color = TColor.textPrimary, modifier = Modifier.weight(1f))
                                if (journey.isOwner) Row(Modifier.plainClick { adding = true }, verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Add, null, tint = TColor.blue, modifier = Modifier.size(16.dp))
                                    Text(" Add trip", style = TType.footnoteStrong, color = TColor.blue)
                                }
                            }
                            journey.legs.forEach { leg ->
                                LegBlock(leg, journey, trips.firstOrNull { it.id == leg.tripId }, rentals, now, actions, onRemove = { removing = leg }, onReorder = { reordering = true })
                            }
                        }
                        journey.costText?.let { cost ->
                            Column {
                                Text("Money", style = TType.title3, color = TColor.textPrimary)
                                Row(Modifier.padding(vertical = 10.dp)) {
                                    Text("Total across trips", style = TType.callout, color = TColor.textSecondary, modifier = Modifier.weight(1f))
                                    Text(cost, style = TType.calloutStrong, color = TColor.textPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (scroll.value > 400) Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(TColor.surfaceSheet))
    }
    if (renaming && journey != null) RenameDialog(journey.title, onDismiss = { renaming = false }) { renaming = false; actions.rename(it) }
    removing?.let { leg ->
        AlertDialog(
            onDismissRequest = { removing = null }, containerColor = TColor.surfaceRaised,
            title = { Text("Remove “${leg.title}”?") },
            text = { Text("The trip is kept as it is; it just stops being part of this journey.") },
            confirmButton = { TextButton({ removing = null; actions.removeTrip(leg.tripId) }) { Text("Remove", color = TColor.red) } },
            dismissButton = { TextButton({ removing = null }) { Text("Cancel") } },
        )
    }
    if (adding && journey != null) {
        val candidates = trips.filter { it.journeyId == null && it.id !in journey.tripIds }
            .sortedByDescending { it.startDate.orEmpty() }
        TripPickerSheet("Add trips", candidates, "Add", onDismiss = { adding = false }) { adding = false; actions.addTrips(it) }
    }
    if (reordering && journey != null) ReorderSheet(journey, onDismiss = { reordering = false }) { reordering = false; actions.reorder(it) }
}

@Composable
private fun HeroStats(j: ApiJourney) {
    val s = j.stats
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TColor.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LegStripe(maxOf(j.tripCount, j.tripIds.size))
        Row {
            listOf("${s.days}" to "days", "${s.flights}" to "flights", "${s.nights}" to "nights", Fmt.number(s.miles) to "miles").forEach { (v, u) ->
                Column(Modifier.weight(if (u == "miles") 1.6f else 1f)) {
                    Text(v, style = if (v.length > 5) TType.title else TType.heroSmall, color = TColor.textPrimary, maxLines = 1, softWrap = false)
                    MicroLabel(u)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GrayChip("${maxOf(j.tripCount, j.tripIds.size)} trips linked", Icons.Filled.Link)
            if (s.places > 0) GrayChip("${s.places} places", Icons.Filled.Place)
            if (s.countries > 1) GrayChip("${s.countries} countries", Icons.Filled.Language)
            j.costText?.let { GrayChip(it, Icons.Filled.CreditCard) }
        }
    }
}

@Composable
private fun LegBlock(
    leg: JourneyLeg, j: ApiJourney, trip: ApiTrip?, rentals: List<ApiRentalCar>, now: LocalDateTime,
    actions: JourneyDetailActions, onRemove: () -> Unit, onReorder: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val color = JourneyPalette.color(leg.leg)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(color), Alignment.Center) { Text("${leg.leg}", style = TType.numberSmall, color = Color.Black) }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp).plainClick { actions.openTrip(leg.tripId) }) {
                Text(leg.title, style = TType.headline, color = TColor.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(leg.subtitle(), style = TType.footnote, color = TColor.textSecondary, maxLines = 1)
            }
            Box {
                Box(Modifier.size(32.dp).clip(CircleShape).background(TColor.surfaceHigh).plainClick { menu = true }, Alignment.Center) {
                    Icon(Icons.Filled.MoreHoriz, "Leg options", tint = TColor.textSecondary, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = TColor.surfaceRaised) {
                    DropdownMenuItem({ Text("Open trip") }, onClick = { menu = false; actions.openTrip(leg.tripId) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) })
                    if (j.isOwner) {
                        DropdownMenuItem({ Text("Reorder…") }, onClick = { menu = false; onReorder() }, leadingIcon = { Icon(Icons.Filled.SwapVert, null) })
                        DropdownMenuItem({ Text("Remove from journey", color = TColor.red) }, onClick = { menu = false; onRemove() },
                            leadingIcon = { Icon(Icons.Filled.RemoveCircleOutline, null, tint = TColor.red) })
                    }
                }
            }
        }
        if (trip != null) {
            val snap = remember(trip, rentals, now.toLocalDate()) { TripSnapshot(trip, rentals, now) }
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(color.copy(alpha = 0.35f)))
                Box(Modifier.padding(start = 8.dp).weight(1f)) {
                    Timeline(snap.events, now, TripDetailActions(close = {}, openEvent = { actions.openEvent(trip.id, it) }))
                }
            }
        } else Text("${leg.itemCount} items", style = TType.footnote, color = TColor.textTertiary)
    }
}

@Composable
fun RenameDialog(current: String, title: String = "Rename journey", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = TColor.surfaceRaised,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true) },
        confirmButton = { TextButton({ onSave(text) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripPickerSheet(title: String, trips: List<ApiTrip>, confirm: String, onDismiss: () -> Unit, onPick: (List<Int>) -> Unit) {
    val picked = remember { mutableStateListOf<Int>() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = TColor.surfaceSheet) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = TType.title3, color = TColor.textPrimary, modifier = Modifier.weight(1f))
                TextButton({ onPick(picked.toList()) }, enabled = picked.isNotEmpty()) { Text(if (picked.isEmpty()) confirm else "$confirm ${picked.size}", color = if (picked.isEmpty()) TColor.textTertiary else TColor.blue) }
            }
            if (trips.isEmpty()) Text("Every trip is already in a journey.", style = TType.callout, color = TColor.textSecondary, modifier = Modifier.padding(vertical = 24.dp))
            LazyColumn(Modifier.height(420.dp)) {
                items(trips, key = { it.id }) { t ->
                    val on = t.id in picked
                    Row(Modifier.fillMaxWidth().plainClick { if (on) picked.remove(t.id) else picked.add(t.id) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (on) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null, tint = if (on) TColor.blue else TColor.textTertiary)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(t.displayTitle, style = TType.headline, color = TColor.textPrimary, maxLines = 1)
                            Text(listOfNotNull(Fmt.parse(t.startDate)?.let(Fmt::monthDayYear)).joinToString(), style = TType.footnote, color = TColor.textSecondary)
                        }
                    }
                    HorizontalDivider(color = TColor.divider)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReorderSheet(j: ApiJourney, onDismiss: () -> Unit, onSave: (List<Int>) -> Unit) {
    val order = remember { mutableStateListOf<JourneyLeg>().apply { addAll(j.legs) } }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = TColor.surfaceSheet) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reorder legs", style = TType.title3, color = TColor.textPrimary, modifier = Modifier.weight(1f))
                TextButton({ onSave(order.map { it.tripId }) }) { Text("Save", color = TColor.blue) }
            }
            order.forEachIndexed { i, leg ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(JourneyPalette.color(i + 1)), Alignment.Center) { Text("${i + 1}", style = TType.footnoteStrong, color = Color.Black) }
                    Text(leg.title, style = TType.headline, color = TColor.textPrimary, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    TextButton({ if (i > 0) { order.add(i - 1, order.removeAt(i)) } }, enabled = i > 0) { Text("↑") }
                    TextButton({ if (i < order.lastIndex) { order.add(i + 1, order.removeAt(i)) } }, enabled = i < order.lastIndex) { Text("↓") }
                }
                HorizontalDivider(color = TColor.divider)
            }
        }
    }
}

/** From a trip: pick an existing journey to join, or start a new one with other trips. */
@Composable
fun JourneyChooserDialog(journeys: List<ApiJourney>, onDismiss: () -> Unit, onPick: (Int) -> Unit, onNew: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = TColor.surfaceRaised,
        title = { Text("Add to a journey") },
        text = {
            Column {
                journeys.filter { it.isOwner }.forEach { j ->
                    Row(Modifier.fillMaxWidth().plainClick { onPick(j.id) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Route, null, tint = TColor.blue)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(j.title, style = TType.headline, color = TColor.textPrimary)
                            Text(j.dateText, style = TType.footnote, color = TColor.textSecondary)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().plainClick(onNew).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, null, tint = TColor.blue)
                    Text("  New journey with other trips…", style = TType.headline, color = TColor.blue)
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
