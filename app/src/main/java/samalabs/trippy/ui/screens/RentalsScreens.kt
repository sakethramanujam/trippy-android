package samalabs.trippy.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import samalabs.trippy.data.ApiRentalCar
import samalabs.trippy.domain.DisplayName
import samalabs.trippy.domain.Fmt
import samalabs.trippy.domain.RentalBrand
import samalabs.trippy.domain.RentalPhase
import samalabs.trippy.domain.RentalSummary
import samalabs.trippy.domain.byPickupYear
import samalabs.trippy.domain.companyName
import samalabs.trippy.domain.confirmationText
import samalabs.trippy.domain.costText
import samalabs.trippy.domain.countdown
import samalabs.trippy.domain.dropoffDate
import samalabs.trippy.domain.dropoffDisplayCity
import samalabs.trippy.domain.dropoffDisplayCode
import samalabs.trippy.domain.isApproximate
import samalabs.trippy.domain.isOneWay
import samalabs.trippy.domain.isSimilarModel
import samalabs.trippy.domain.liveRentals
import samalabs.trippy.domain.milesDriven
import samalabs.trippy.domain.pastRentals
import samalabs.trippy.domain.phase
import samalabs.trippy.domain.pickupCoordinate
import samalabs.trippy.domain.pickupDate
import samalabs.trippy.domain.pickupDisplayCity
import samalabs.trippy.domain.pickupDisplayCode
import samalabs.trippy.domain.progress
import samalabs.trippy.domain.rentalDays
import samalabs.trippy.domain.vehicleName
import samalabs.trippy.domain.vehicleSubtitle
import samalabs.trippy.ui.LocalContainer
import samalabs.trippy.ui.components.CompanyBadge
import samalabs.trippy.ui.components.Projection
import samalabs.trippy.ui.components.QuickAction
import samalabs.trippy.ui.components.QuickActions
import samalabs.trippy.ui.components.RemoteImage
import samalabs.trippy.ui.components.RemoteImages
import samalabs.trippy.ui.components.plainClick
import samalabs.trippy.ui.components.trippyCard
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

fun rentalPhaseColor(p: RentalPhase): Color = when (p) {
    RentalPhase.UPCOMING -> TColor.blue
    RentalPhase.ACTIVE -> TColor.green
    RentalPhase.OVERDUE -> TColor.red
    RentalPhase.RETURNED -> TColor.textSecondary
}

@Composable
fun RentalPhasePill(p: RentalPhase) {
    val c = rentalPhaseColor(p)
    Text(p.label.uppercase(), style = TType.micro, color = c, modifier = Modifier.background(c.copy(alpha = 0.16f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp))
}

/** Tab root: live rentals as cards, past ones by year (iOS `RentalsView`, list instead of a map sheet). */
@Composable
fun RentalsScreen(onOpen: (Int) -> Unit) {
    val state by LocalContainer.current.trips.state.collectAsState()
    val now = rememberNow()
    val rentals = state.rentals
    val summary = remember(rentals) { RentalSummary(rentals) }
    val live = remember(rentals, now) { rentals.liveRentals(now) }
    val past = remember(rentals, now) { rentals.pastRentals(now).byPickupYear() }
    val stations = remember(rentals) { rentals.mapNotNull { r -> r.pickupCoordinate?.let { r.pickupDisplayCode to it } }.distinctBy { it.first } }

    Box(Modifier.fillMaxSize().background(TColor.canvas)) {
        StationsArt(stations, Modifier.fillMaxWidth().height(300.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            item { Spacer(Modifier.height(200.dp)) }
            item {
                Column(Modifier.sheet().padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("My Rentals", style = TType.largeTitle, color = TColor.textPrimary)
                            if (summary.count > 0) Text(
                                "${summary.count} rental${if (summary.count == 1) "" else "s"} · ${Fmt.number(summary.days)} days · ${Fmt.compactMoney(summary.spend)}",
                                style = TType.footnote, color = TColor.textSecondary,
                            )
                        }
                        Icon(Icons.Filled.DirectionsCar, null, tint = TColor.green, modifier = Modifier.size(26.dp))
                    }
                }
            }
            when {
                !state.loaded -> item { Box(Modifier.sheet().fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator(color = TColor.coral) } }
                rentals.isEmpty() && state.offline -> item { RentalsEmpty("Can't reach Trippy API", "Pull to refresh on Trips once your server is back.") }
                rentals.isEmpty() -> item { RentalsEmpty("No rentals yet", "Import a booking email or add a rental on the web, and it shows up here.") }
                else -> {
                    if (live.isEmpty()) item {
                        val last = rentals.pastRentals(now).firstOrNull()
                        Column(Modifier.sheet().padding(horizontal = 20.dp, vertical = 10.dp)) {
                            Text("No upcoming rentals", style = TType.headline, color = TColor.textPrimary)
                            last?.let { Text("Last returned ${it.countdown(now).removePrefix("Returned ").replaceFirstChar(Char::lowercase)} · ${it.companyName}", style = TType.footnote, color = TColor.textSecondary) }
                        }
                    }
                    items(live, key = { "live-${it.id}" }) { r ->
                        Box(Modifier.sheet().padding(horizontal = 16.dp, vertical = 6.dp)) { RentalCard(r, now) { onOpen(r.id) } }
                    }
                    past.forEach { (year, list) ->
                        item(key = "y-$year") {
                            val spend = list.filter { (it.currency ?: "USD") == "USD" }.sumOf { it.cost ?: 0.0 }
                            SectionHeader(if (year == 0) "Undated" else "$year", "${list.size} · ${Fmt.compactMoney(spend)}")
                        }
                        items(list, key = { "p-${it.id}" }) { r -> Box(Modifier.sheet()) { RentalRow(r, now) { onOpen(r.id) } } }
                    }
                }
            }
            item { Box(Modifier.sheet().fillMaxWidth().height(140.dp)) }
        }
    }
}

@Composable
private fun RentalsEmpty(title: String, body: String) {
    Column(Modifier.sheet().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.DirectionsCar, null, tint = TColor.textTertiary, modifier = Modifier.size(34.dp))
        Text(title, style = TType.title3, color = TColor.textPrimary, modifier = Modifier.padding(top = 8.dp))
        Text(body, style = TType.callout, color = TColor.textSecondary, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Pickup stations as dots on the dark map art (no tile map on Android). */
@Composable
private fun StationsArt(stations: List<Pair<String, samalabs.trippy.domain.LatLng>>, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val d = LocalDensity.current
        val w = with(d) { maxWidth.toPx() }; val h = with(d) { maxHeight.toPx() }
        val top = with(d) { 60.dp.toPx() }; val bottom = with(d) { 110.dp.toPx() }
        val proj = remember(stations, w, h) { Projection.fit(stations.map { it.second }, w, h, top, bottom) }
        Canvas(Modifier.matchParentSize()) {
            drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF0B1A2E), Color(0xFF071222), Color(0xFF02060C))))
            stations.forEach { (_, p) ->
                val o = Offset(proj.x(p.lng), proj.y(p.lat))
                drawCircle(TColor.green.copy(alpha = 0.25f), 16f, o)
                drawCircle(Color.White, 7f, o); drawCircle(TColor.green, 5f, o)
            }
        }
    }
}

/** Big Flighty-style card for an upcoming or active rental. */
@Composable
fun RentalCard(r: ApiRentalCar, now: LocalDateTime, onClick: () -> Unit) {
    val phase = r.phase(now)
    val p = r.pickupDate; val d = r.dropoffDate
    Column(Modifier.fillMaxWidth().trippyCard().plainClick(onClick).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompanyBadge(r.company, 36)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(r.vehicleName, style = TType.headline, color = TColor.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(RentalBrand.forCompany(r.company).name, r.vehicleYear?.toString(), if (r.isSimilarModel) "or similar" else null).joinToString(" · ")
                Text(sub, style = TType.footnote, color = TColor.textSecondary, maxLines = 1)
            }
            RentalPhasePill(phase)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Endpoint(r.pickupDisplayCode, r.pickupDisplayCity, p, r.isApproximate, Alignment.Start, Modifier.weight(1f))
            Column(Modifier.widthIn(min = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dashed(Modifier.width(22.dp))
                    Icon(if (r.isOneWay) Icons.AutoMirrored.Filled.ArrowForward else Icons.Filled.Loop, null, tint = TColor.textSecondary, modifier = Modifier.padding(horizontal = 4.dp).size(16.dp))
                    Dashed(Modifier.width(22.dp))
                }
                Text(if (r.isApproximate) "date approx." else r.rentalDays?.let { "$it day${if (it == 1) "" else "s"}" } ?: "", style = TType.caption, color = TColor.textSecondary)
            }
            Endpoint(r.dropoffDisplayCode, r.dropoffDisplayCity, d, r.isApproximate, Alignment.End, Modifier.weight(1f))
        }
        if (phase == RentalPhase.ACTIVE) LinearProgressIndicator(
            progress = { r.progress(now).toFloat() }, color = TColor.green, trackColor = TColor.surfaceRaised,
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
        )
        if (phase == RentalPhase.ACTIVE || phase == RentalPhase.UPCOMING) Text(r.countdown(now), style = TType.calloutStrong, color = rentalPhaseColor(phase))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DisplayName.vehicleClass(r.vehicleClass)?.let { InfoTag("Class", it) }
            r.confirmationText?.let { InfoTag("Conf", it) }
            r.milesDriven?.let { InfoTag("Mi", Fmt.number(it)) }
            r.costText?.let { InfoTag("Total", it, TColor.green) }
        }
    }
}

@Composable
private fun Dashed(modifier: Modifier) {
    Canvas(modifier.height(1.dp)) {
        drawLine(TColor.textTertiary, Offset(0f, 0f), Offset(size.width, 0f), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))
    }
}

@Composable
private fun Endpoint(code: String, city: String, at: LocalDateTime?, approx: Boolean, align: Alignment.Horizontal, modifier: Modifier) {
    Column(modifier, horizontalAlignment = align) {
        Text(code, style = TType.code, color = TColor.textPrimary, maxLines = 1)
        Text(city, style = TType.footnote, color = TColor.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        at?.let {
            Text(if (approx) Fmt.monthDayYear(it) else Fmt.time(it), style = TType.timeSmall, color = TColor.textPrimary)
            if (!approx) Text(Fmt.day(it), style = TType.caption, color = TColor.textSecondary)
        }
    }
}

@Composable
private fun InfoTag(label: String, value: String, color: Color = TColor.textPrimary) {
    Row(Modifier.background(TColor.surfaceRaised, RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 4.dp)) {
        Text("$label ", style = TType.micro, color = TColor.textSecondary)
        Text(value, style = TType.micro, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Compact row for past rentals: a countdown/day column, then brand, car and stations. */
@Composable
fun RentalRow(r: ApiRentalCar, now: LocalDateTime, onClick: () -> Unit) {
    val phase = r.phase(now)
    val p = r.pickupDate
    val (big, small) = when (phase) {
        RentalPhase.UPCOMING, RentalPhase.ACTIVE, RentalPhase.OVERDUE -> {
            val target = if (phase == RentalPhase.UPCOMING) p else r.dropoffDate
            val n = target?.let { maxOf(0, ChronoUnit.DAYS.between(now.toLocalDate(), it.toLocalDate())) } ?: 0
            "$n" to if (phase == RentalPhase.UPCOMING) "DAYS" else "LEFT"
        }
        RentalPhase.RETURNED -> r.rentalDays?.let { "$it" to if (it == 1) "DAY" else "DAYS" }
            ?: p?.let { "${it.dayOfMonth}" to it.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()).uppercase() }
            ?: ("–" to "")
    }
    Column(Modifier.background(TColor.surfaceSheet).plainClick(onClick)) {
        HorizontalDivider(color = TColor.divider, modifier = Modifier.padding(horizontal = 20.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(big, style = TType.title, color = if (phase.isLive) rentalPhaseColor(phase) else TColor.textPrimary)
                Text(small, style = TType.micro, color = TColor.textSecondary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CompanyBadge(r.company, 18)
                    Text(
                        "  " + listOfNotNull(r.companyName, r.confirmationText).joinToString(" · "),
                        style = TType.footnote, color = TColor.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    p?.let { Text(if (r.isApproximate) Fmt.monthDayYear(it) else Fmt.day(it), style = TType.footnote, color = TColor.textSecondary) }
                }
                Text(r.vehicleName, style = TType.headline, color = TColor.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val times = listOfNotNull(r.pickupDisplayCode + (p?.takeIf { !r.isApproximate }?.let { " " + Fmt.time(it) } ?: ""),
                    r.dropoffDisplayCode + (r.dropoffDate?.takeIf { !r.isApproximate }?.let { " " + Fmt.time(it) } ?: ""))
                Text(times.joinToString(" → "), style = TType.caption, color = TColor.textTertiary, maxLines = 1)
            }
        }
    }
}

/** iOS rental detail extras on top of the shared rental screen: actions, progress and the vehicle card. */
@Composable
fun rentalDetailExtras(r: ApiRentalCar, now: LocalDateTime): BookingExtras {
    val ctx = LocalContext.current
    val server = LocalContainer.current.sessions.current.server
    return BookingExtras(
        afterTimes = {
            val phase = r.phase(now)
            if (phase == RentalPhase.ACTIVE || phase == RentalPhase.OVERDUE) LinearProgressIndicator(
                progress = { r.progress(now).toFloat() }, color = rentalPhaseColor(phase), trackColor = TColor.surfaceRaised,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            )
            VehicleCard(r, RemoteImages.resolve(r.photoUrl, server))
        },
        quickActions = {
            QuickActions {
                QuickAction("Directions", Icons.Filled.DirectionsCar, TColor.blue, { openDirections(ctx, r) }, Modifier.weight(1f))
                if (r.pickupDate != null) QuickAction("Add to Calendar", Icons.Filled.CalendarMonth, TColor.blue, { addToCalendar(ctx, r) }, Modifier.weight(1f))
            }
        },
    )
}

@Composable
private fun ColumnScope.VehicleCard(r: ApiRentalCar, photo: String?) {
    Row(Modifier.fillMaxWidth().trippyCard().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("VEHICLE", style = TType.label, color = TColor.textSecondary)
            Text(r.vehicleName, style = TType.title2, color = TColor.textPrimary, maxLines = 2)
            if (r.vehicleSubtitle.isNotEmpty()) Text(r.vehicleSubtitle, style = TType.footnote, color = TColor.textSecondary, maxLines = 2)
            r.licensePlate?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = TType.mono, color = Color.Black, modifier = Modifier.padding(top = 2.dp).background(Color.White, RoundedCornerShape(5.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        val brand = Color(RentalBrand.forCompany(r.company).color)
        RemoteImage(photo, Modifier.size(124.dp, 88.dp).clip(RoundedCornerShape(12.dp)).background(TColor.surfaceRaised)) {
            Icon(Icons.Filled.DirectionsCar, null, tint = brand.copy(alpha = 0.9f), modifier = Modifier.size(40.dp))
        }
    }
}

private fun pickupQuery(r: ApiRentalCar): String {
    val code = r.pickupCode?.takeIf { it.isNotBlank() }
    return if (code != null) "${r.companyName} $code airport" else listOfNotNull(r.companyName, r.pickupLocation).joinToString(", ")
}

private fun openDirections(ctx: Context, r: ApiRentalCar) {
    val c = r.pickupCoordinate
    val uri = if (c != null) "geo:${c.lat},${c.lng}?q=${Uri.encode(pickupQuery(r))}" else "geo:0,0?q=${Uri.encode(pickupQuery(r))}"
    start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
}

private fun addToCalendar(ctx: Context, r: ApiRentalCar) {
    val a = r.pickupDate ?: return
    val begin = a.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val notes = buildList {
        add(r.companyName + (r.carModel?.let { " · $it" } ?: ""))
        r.confirmationText?.let { add("Confirmation: $it") }
        r.dropoffLocation?.let { add("Return: $it") }
    }.joinToString("\n")
    start(ctx, Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, "🚗 ${r.companyName} pickup")
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 30 * 60_000)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, r.pickupLocation.orEmpty())
        .putExtra(CalendarContract.Events.DESCRIPTION, notes))
}

private fun start(ctx: Context, i: Intent) {
    try { ctx.startActivity(i) } catch (_: ActivityNotFoundException) {
        android.widget.Toast.makeText(ctx, "No app can open that.", android.widget.Toast.LENGTH_SHORT).show()
    }
}
