package samalabs.trippy.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import samalabs.trippy.data.DeletePlan
import samalabs.trippy.data.TrashTrip
import samalabs.trippy.ui.LocalContainer
import samalabs.trippy.ui.components.FormBanner
import samalabs.trippy.ui.components.PrimaryButton
import samalabs.trippy.ui.components.plainClick
import samalabs.trippy.ui.components.trippyCard
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType
import java.time.LocalDateTime

/** "Delete trip" for owners (with what goes with it), "Leave trip" for travelers. [onDone] gets true once it happened. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteTripSheet(tripIds: List<Int>, onDismiss: () -> Unit, onDone: (Boolean) -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var plan by remember { mutableStateOf<DeletePlan?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    LaunchedEffect(tripIds) { val (p, e) = c.trash.preview(tripIds); plan = p; error = e }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = TColor.surfaceSheet, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val p = plan
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(TColor.red.copy(alpha = 0.15f), CircleShape), Alignment.Center) {
                    Icon(if (p?.isLeave == true) Icons.AutoMirrored.Filled.ExitToApp else Icons.Filled.Delete, null, tint = TColor.red)
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(p?.title ?: if (tripIds.size == 1) "Delete this trip?" else "Delete ${tripIds.size} trips?", style = TType.title3, color = TColor.textPrimary, maxLines = 2)
                    if (p != null) Text(
                        if (p.isLeave) "Only you leave. The trip stays for everyone else."
                        else "Everything in ${if (p.owned.size > 1) "them" else "it"} goes with ${if (p.owned.size > 1) "them" else "it"}.",
                        style = TType.footnote, color = TColor.textSecondary,
                    )
                }
            }
            if (p == null && error == null) Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), Alignment.Center) { CircularProgressIndicator(color = TColor.coral) }
            if (p != null && p.isLeave) {
                p.unsettledNote()?.let { FormBanner(it, tint = TColor.amber) }
                Text("You'll stop seeing it in your trips, stats and stories. The owner can add you again.", style = TType.callout, color = TColor.textSecondary)
            } else if (p != null) {
                CountsGrid(p)
                Text(p.counts.summary, style = TType.callout, color = TColor.textSecondary)
                p.unsettledNote()?.let { FormBanner(it, tint = TColor.amber) }
                p.journeys.forEach { FormBanner(p.journeyNote(it), tint = TColor.purple, icon = Icons.Filled.Route) }
                p.skippedNote?.let { FormBanner(it, tint = TColor.textSecondary, icon = Icons.Filled.Group) }
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.AccessTime, null, tint = TColor.textSecondary, modifier = Modifier.size(16.dp))
                    Text(
                        "Moves to Recently Deleted for ${p.keepDays} days. Restore it any time from Account → Recently Deleted.",
                        style = TType.footnote, color = TColor.textSecondary, modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            error?.let { FormBanner(it) }
            val enabled = p != null && (p.isLeave || p.owned.isNotEmpty())
            PrimaryButton(p?.button ?: "Delete trip", color = TColor.red, busy = working, enabled = enabled, onClick = {
                val pl = p ?: return@PrimaryButton
                working = true
                scope.launch {
                    val err = if (pl.isLeave) c.trash.leave(pl.previews[0].tripId, force = pl.unsettled.isNotEmpty())
                    else c.trash.delete(pl.owned.map { it.tripId })
                    working = false
                    if (err != null) error = err else onDone(true)
                }
            })
        }
    }
}

@Composable
private fun CountsGrid(p: DeletePlan) {
    val n = p.counts
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(Icons.Filled.Flight, n.flights, "Flights") to TColor.blue,
            Triple(Icons.Filled.Bed, n.stays, "Stays") to TColor.purple,
            Triple(Icons.Filled.DirectionsCar, n.rentals, "Rentals") to TColor.green,
            Triple(Icons.Filled.CreditCard, n.expenses, "Expenses") to TColor.amber,
        ).forEach { (t, color) ->
            Column(Modifier.weight(1f).trippyCard(14.dp, TColor.surfaceRaised).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(t.first, null, tint = color, modifier = Modifier.size(18.dp))
                Text("${t.second}", style = TType.number, color = if (t.second == 0) TColor.textTertiary else TColor.textPrimary)
                Text(t.third.uppercase(), style = TType.micro, color = TColor.textSecondary)
            }
        }
    }
}

/** "Deleted Lisbon · In Recently Deleted for 30 days · Undo", for six seconds after a delete. */
@Composable
fun UndoToastHost(modifier: Modifier = Modifier) {
    val c = LocalContainer.current
    val undo by c.trash.undo.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(undo?.token) { val u = undo ?: return@LaunchedEffect; delay(6_000); c.trash.dismissUndo(u.token) }
    AnimatedVisibility(undo != null, modifier, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        val u = undo ?: return@AnimatedVisibility
        Row(
            Modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth()
                .background(TColor.surfaceRaised, RoundedCornerShape(18.dp)).border(1.dp, TColor.divider, RoundedCornerShape(18.dp))
                .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Delete, null, tint = TColor.red)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(u.text, style = TType.calloutStrong, color = TColor.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("In Recently Deleted for 30 days", style = TType.caption, color = TColor.textSecondary)
            }
            Text(
                "Undo", style = TType.calloutStrong, color = TColor.amber,
                modifier = Modifier.background(TColor.amber.copy(alpha = 0.14f), CircleShape).plainClick {
                    if (!busy) { busy = true; scope.launch { c.trash.restore(u.ids); busy = false } }
                }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** Account → Recently Deleted: restore or delete for good. */
@Composable
fun RecentlyDeletedScreen(onBack: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<TrashTrip>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmPurge by remember { mutableStateOf<TrashTrip?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    val now = remember { LocalDateTime.now() }
    suspend fun load() { val (l, e) = c.trash.list(); l?.let { items = it.trips }; error = e }
    fun run(block: suspend () -> String?) { scope.launch { error = block(); load() } }
    LaunchedEffect(Unit) { load() }
    Column(Modifier.fillMaxSize().background(TColor.canvas).safeDrawingPadding()) {
        BackBar("Recently Deleted", onBack) {
            if (!items.isNullOrEmpty()) Box {
                RoundButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreHoriz, "More", tint = TColor.textPrimary, modifier = Modifier.size(20.dp)) }
                DropdownMenu(menu, { menu = false }, containerColor = TColor.surfaceRaised) {
                    DropdownMenuItem({ Text("Restore all") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, null) }, onClick = {
                        menu = false; run { c.trash.restore(items.orEmpty().map { it.id }) }
                    })
                    DropdownMenuItem({ Text("Delete all now", color = TColor.red) }, leadingIcon = { Icon(Icons.Filled.DeleteOutline, null, tint = TColor.red) }, onClick = {
                        menu = false; confirmEmpty = true
                    })
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            item {
                Text(
                    "Deleted trips stay here for 30 days, then they're removed for good with their flights, stays, rentals and expenses.",
                    style = TType.footnote, color = TColor.textSecondary, modifier = Modifier.padding(vertical = 12.dp),
                )
                error?.let { FormBanner(it, Modifier.padding(bottom = 8.dp)) }
            }
            val list = items
            if (list == null) item { Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { CircularProgressIndicator(color = TColor.coral) } }
            else if (list.isEmpty()) item {
                Column(Modifier.fillMaxWidth().trippyCard().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.DeleteOutline, null, tint = TColor.textTertiary, modifier = Modifier.size(34.dp))
                    Text("Nothing here", style = TType.title3, color = TColor.textPrimary, modifier = Modifier.padding(top = 8.dp))
                    Text("Trips you delete show up here so you can bring them back.", style = TType.callout, color = TColor.textSecondary, modifier = Modifier.padding(top = 4.dp))
                }
            }
            else items(list, key = { it.id }) { t ->
                Column(Modifier.padding(vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.title ?: "Trip", style = TType.headline, color = TColor.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        t.daysLeft(now)?.let { d -> Text(if (d == 0) "TODAY" else "$d DAY${if (d == 1) "" else "S"} LEFT", style = TType.label, color = if (d <= 3) TColor.red else TColor.textSecondary) }
                    }
                    Text(t.subtitle, style = TType.footnote, color = TColor.textSecondary, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TintedButton("Restore", Icons.AutoMirrored.Filled.Undo, TColor.blue, Modifier.weight(1f)) { run { c.trash.restore(listOf(t.id)) } }
                        TintedButton("Delete Now", Icons.Filled.Delete, TColor.red, Modifier.weight(1f)) { confirmPurge = t }
                    }
                }
                HorizontalDivider(color = TColor.divider)
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
    confirmPurge?.let { t ->
        AlertDialog(
            onDismissRequest = { confirmPurge = null }, containerColor = TColor.surfaceRaised,
            title = { Text("Delete “${t.title ?: "trip"}” now?") },
            text = { Text("${t.counts.summary} will be removed for good. This can't be undone.") },
            confirmButton = { TextButton({ confirmPurge = null; run { c.trash.purge(t.id) } }) { Text("Delete now", color = TColor.red) } },
            dismissButton = { TextButton({ confirmPurge = null }) { Text("Cancel") } },
        )
    }
    if (confirmEmpty) AlertDialog(
        onDismissRequest = { confirmEmpty = false }, containerColor = TColor.surfaceRaised,
        title = { Text("Delete ${items?.size ?: 0} trips now?") },
        text = { Text("This can't be undone.") },
        confirmButton = { TextButton({ confirmEmpty = false; run { c.trash.purge(null) } }) { Text("Delete all now", color = TColor.red) } },
        dismissButton = { TextButton({ confirmEmpty = false }) { Text("Cancel") } },
    )
}

@Composable
internal fun TintedButton(text: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(40.dp).background(tint.copy(alpha = 0.15f), RoundedCornerShape(12.dp)).plainClick(onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = TType.calloutStrong, color = tint)
    }
}
