package samalabs.trippy.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import samalabs.trippy.data.ApiJourney
import samalabs.trippy.data.ApiTrip
import samalabs.trippy.domain.TripSnapshot
import samalabs.trippy.ui.components.PrimaryButton
import samalabs.trippy.ui.components.plainClick
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class SwipeAction(val title: String, val icon: ImageVector, val tint: Color, val action: () -> Unit)

/** One row swiped open at a time, like Mail. */
object SwipeCoordinator { var open by mutableStateOf<String?>(null) }

/**
 * Mail-style swipe actions for rows in a LazyColumn. Leading actions read left→right with the first
 * as the full swipe; trailing read left→right with the last as the full swipe (same as iOS SwipeRow).
 */
@Composable
fun SwipeRow(key: String, leading: List<SwipeAction>, trailing: List<SwipeAction>, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val buttonPx = with(density) { 76.dp.toPx() }
    val minFull = with(density) { 200.dp.toPx() }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(1) }
    var armed by remember { mutableStateOf(false) }
    val fullSwipe = max(width * 0.62f, minFull)
    fun settle(to: Float) = scope.launch { offset.animateTo(to, tween(220)) }
    fun close() { settle(0f); if (SwipeCoordinator.open == key) SwipeCoordinator.open = null }
    fun fire(a: SwipeAction) { close(); a.action() }
    LaunchedEffect(SwipeCoordinator.open) { if (SwipeCoordinator.open != key && offset.value != 0f) offset.animateTo(0f, tween(220)) }

    Box(Modifier.fillMaxWidth().onSizeChanged { width = it.width }.clipToBounds()) {
        val x = offset.value
        if (x > 0f) SwipeButtons(leading, x, armed, fullIndex = 0, alignEnd = false, Modifier.matchParentSize()) { fire(it) }
        if (x < 0f) SwipeButtons(trailing, -x, armed, fullIndex = trailing.lastIndex, alignEnd = true, Modifier.matchParentSize()) { fire(it) }
        Box(
            Modifier.offset { IntOffset(x.roundToInt(), 0) }.pointerInput(leading.size, trailing.size, fullSwipe) {
                detectHorizontalDragGestures(
                    onDragStart = { SwipeCoordinator.open = key },
                    onDragEnd = {
                        val v = offset.value
                        when {
                            armed && v < 0 && trailing.isNotEmpty() -> fire(trailing.last())
                            armed && v > 0 && leading.isNotEmpty() -> fire(leading.first())
                            v < -buttonPx * 0.6f -> settle(-buttonPx * trailing.size)
                            v > buttonPx * 0.6f -> settle(buttonPx * leading.size)
                            else -> close()
                        }
                        armed = false
                    },
                    onDragCancel = { close(); armed = false },
                ) { change, dx ->
                    change.consume()
                    var nx = offset.value + dx
                    if (leading.isEmpty()) nx = minOf(0f, nx)
                    if (trailing.isEmpty()) nx = maxOf(0f, nx)
                    scope.launch { offset.snapTo(nx) }
                    val past = abs(nx) > fullSwipe
                    if (past != armed) { armed = past; haptics.performHapticFeedback(if (past) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove) }
                }
            },
        ) {
            content()
            if (x != 0f) Box(Modifier.matchParentSize().plainClick { close() })
        }
    }
}

@Composable
private fun SwipeButtons(actions: List<SwipeAction>, revealed: Float, armed: Boolean, fullIndex: Int, alignEnd: Boolean, modifier: Modifier, fire: (SwipeAction) -> Unit) {
    val density = LocalDensity.current
    Row(modifier.padding(vertical = 4.dp), horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start) {
        Row(Modifier.width(with(density) { revealed.toDp() }).fillMaxHeight().clip(RoundedCornerShape(12.dp))) {
            actions.forEachIndexed { i, a ->
                val w = when { !armed -> revealed / actions.size; i == fullIndex -> revealed; else -> 0f }
                if (w > 0f) Column(
                    Modifier.width(with(density) { w.toDp() }).fillMaxHeight().background(a.tint).plainClick { fire(a) }
                        .semantics { contentDescription = a.title },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(a.icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text(a.title.uppercase(), style = TType.micro, color = Color.White, maxLines = 1, overflow = TextOverflow.Clip)
                }
            }
        }
    }
}

/** Long press (that didn't turn into a scroll) without stealing the row's own tap. */
fun Modifier.onLongPress(onLong: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop
        val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                val ch = e.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                if (!ch.pressed || ch.isConsumed || (ch.position - down.position).getDistance() > slop) return@withTimeoutOrNull true
            }
            @Suppress("UNREACHABLE_CODE") true
        }
        if (ended == null) {
            onLong()
            // Swallow the rest of this touch so the row doesn't also open.
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                e.changes.forEach { it.consume() }
                if (e.changes.none { it.pressed }) break
            }
        }
    }
}

/** List-level state for swipes, the long-press menu, multi-select, linking and deleting. */
class TripListUi {
    var menuFor by mutableStateOf<Int?>(null)
    var selecting by mutableStateOf(false)
    val selected = mutableStateListOf<Int>()
    var linking by mutableStateOf<List<Int>?>(null)
    fun toggle(id: Int) { if (id in selected) selected.remove(id) else selected.add(id) }
    fun endSelect() { selecting = false; selected.clear() }
}

class TripRowCallbacks(
    val owns: (ApiTrip) -> Boolean,
    val pin: (ApiTrip) -> Unit,
    val share: (Int) -> Unit,
    val story: (Int) -> Unit,
    val delete: (List<Int>) -> Unit,
)

/** Wraps each trip row with iOS's actions: swipe right Pin/Share, swipe left Link/Delete, long press for the menu. */
@Composable
fun TripActionRow(ui: TripListUi, s: TripSnapshot, cb: TripRowCallbacks, content: @Composable () -> Unit) {
    val t = s.trip
    val owns = cb.owns(t)
    val inJourney = t.journeyId != null
    if (ui.selecting) {
        val on = t.id in ui.selected
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (on) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, if (on) "Selected" else "Not selected",
                    tint = if (on) TColor.blue else TColor.textTertiary, modifier = Modifier.padding(start = 16.dp).size(24.dp),
                )
                Box(Modifier.weight(1f)) { content() }
            }
            Box(Modifier.matchParentSize().plainClick { ui.toggle(t.id) })
        }
        return
    }
    Box(Modifier.onLongPress { ui.menuFor = t.id }) {
        SwipeRow(
            key = "trip:${t.id}",
            leading = listOf(
                SwipeAction(if (t.pinned) "Unpin" else "Pin", Icons.Filled.PushPin, TColor.coral) { cb.pin(t) },
                SwipeAction("Share", Icons.Filled.IosShare, TColor.blue) { cb.share(t.id) },
            ),
            trailing = listOf(
                SwipeAction(if (inJourney) "Move" else "Link", Icons.Filled.Link, TColor.purple) { ui.linking = listOf(t.id) },
                SwipeAction(if (owns) "Delete" else "Leave", if (owns) Icons.Filled.Delete else Icons.AutoMirrored.Filled.ExitToApp, TColor.red) { cb.delete(listOf(t.id)) },
            ),
            content = content,
        )
        DropdownMenu(ui.menuFor == t.id, { ui.menuFor = null }, containerColor = TColor.surfaceRaised) {
            fun item(label: String, icon: ImageVector, tint: Color = TColor.textPrimary, run: () -> Unit) =
                @Composable { DropdownMenuItem({ Text(label, color = tint) }, leadingIcon = { Icon(icon, null, tint = tint) }, onClick = { ui.menuFor = null; run() }) }
            item(if (t.pinned) "Unpin" else "Pin to top", Icons.Filled.PushPin) { cb.pin(t) }()
            item("Share card", Icons.Filled.IosShare) { cb.share(t.id) }()
            item("Play story", Icons.Filled.AutoStories) { cb.story(t.id) }()
            HorizontalDivider(color = TColor.divider)
            item(if (inJourney) "Move to journey…" else "Link to journey…", Icons.Filled.Link) { ui.linking = listOf(t.id) }()
            item("Select", Icons.Filled.CheckCircle) { ui.selecting = true; ui.selected.clear(); ui.selected.add(t.id) }()
            HorizontalDivider(color = TColor.divider)
            if (owns) item("Delete trip…", Icons.Filled.Delete, TColor.red) { cb.delete(listOf(t.id)) }()
            else item("Leave trip…", Icons.AutoMirrored.Filled.ExitToApp, TColor.red) { cb.delete(listOf(t.id)) }()
        }
    }
}

/** Bottom bar while selecting: Cancel, Link N trips, Delete. */
@Composable
fun SelectionBar(ui: TripListUi, delete: (List<Int>) -> Unit, modifier: Modifier = Modifier) {
    val n = ui.selected.size
    Row(
        modifier.fillMaxWidth().background(TColor.surfaceSheet).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        TintedButton("Cancel", Icons.Filled.Close, TColor.textSecondary, Modifier.width(110.dp).height(52.dp)) { ui.endSelect() }
        PrimaryButton(if (n < 2) "Select trips" else "Link $n trips", enabled = n > 0, onClick = { ui.linking = ui.selected.toList() }, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(52.dp).background(TColor.red.copy(alpha = if (n == 0) 0.07f else 0.15f), RoundedCornerShape(14.dp))
                .plainClick { if (n > 0) delete(ui.selected.toList()) }
                .semantics { contentDescription = if (n == 1) "Delete trip" else "Delete $n trips" },
            Alignment.Center,
        ) { Icon(Icons.Filled.Delete, null, tint = TColor.red.copy(alpha = if (n == 0) 0.4f else 1f)) }
    }
}

/** Link one or more trips: into an existing journey, or a new one (asking for more trips when only one is picked). */
@Composable
fun LinkTripsFlow(
    ids: List<Int>, journeys: List<ApiJourney>, trips: List<ApiTrip>,
    onDismiss: () -> Unit,
    addTo: (journeyId: Int, ids: List<Int>) -> Unit,
    create: (ids: List<Int>) -> Unit,
) {
    var startingNew by remember { mutableStateOf(false) }
    if (!startingNew) JourneyChooserDialog(
        journeys.filter { j -> ids.any { it !in j.tripIds } }, onDismiss = onDismiss,
        onPick = { jid -> addTo(jid, ids) },
        onNew = { if (ids.size >= 2) create(ids) else startingNew = true },
    )
    else {
        val first = trips.firstOrNull { it.id == ids.first() }
        TripPickerSheet(
            "New journey with ${first?.title ?: "this trip"}",
            trips.filter { it.id !in ids && it.journeyId == null && journeys.none { j -> it.id in j.tripIds } },
            "Link", onDismiss = onDismiss,
        ) { more -> create(ids + more) }
    }
}
