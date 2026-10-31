package samalabs.trippy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import samalabs.trippy.data.ApiFlight
import samalabs.trippy.data.ApiLodging
import samalabs.trippy.data.ApiRentalCar
import samalabs.trippy.domain.*
import samalabs.trippy.ui.components.BrandTile
import samalabs.trippy.ui.components.Brands
import samalabs.trippy.ui.components.CircleIcon
import samalabs.trippy.ui.components.Fact
import samalabs.trippy.ui.components.FactKind
import samalabs.trippy.ui.components.FactList
import samalabs.trippy.ui.components.HighlightBlock
import samalabs.trippy.ui.components.MicroLabel
import samalabs.trippy.ui.components.StatusBanner
import samalabs.trippy.ui.components.TimesCard
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Slots later features fill: check-in, quick actions, live status. */
class BookingExtras(
    val afterTimes: @Composable ColumnScope.() -> Unit = {},
    val quickActions: @Composable ColumnScope.() -> Unit = {},
    val afterActions: @Composable ColumnScope.() -> Unit = {},
)

@Composable
private fun DetailPage(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(TColor.canvas).safeDrawingPadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp), content = content,
        )
    }
}

@Composable
private fun DetailHeader(tile: @Composable () -> Unit, title: String, subtitle: String?, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        tile()
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = TType.title2, color = TColor.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = TType.callout, color = TColor.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        CircleIcon(Icons.Filled.Close, "Close", onClick = onClose, size = 34)
    }
}

// Flight

fun flightStatusColor(f: ApiFlight): Color = when (f.statusTone) {
    StatusTone.GREEN -> TColor.green; StatusTone.AMBER -> TColor.amber; StatusTone.RED -> TColor.red; StatusTone.GRAY -> TColor.textSecondary
}

fun flightFacts(f: ApiFlight): List<Fact> = buildList {
    f.confirmationNumber?.takeIf { it.isNotBlank() }?.let { add(Fact("Confirmation", it, mono = true, kind = FactKind.COPY)) }
    f.seat?.let { add(Fact("Seat", it, mono = true)) }
    DisplayName.cabin(f.cabinClass)?.let { add(Fact("Cabin", it)) }
    f.durationText?.let { add(Fact("Duration", it)) }
    f.distanceMiles?.let { add(Fact("Distance", "${Fmt.number(it)} mi")) }
    listOfNotNull(f.aircraftManufacturer, f.aircraftModel ?: f.aircraftType).joinToString(" ").takeIf { it.isNotBlank() }?.let { add(Fact("Aircraft", it)) }
    f.cost?.let { add(Fact("Fare", Fmt.money(it, f.currency))) }
    f.bookingSource?.takeIf { it.isNotBlank() }?.let { add(Fact("Booked via", it.replaceFirstChar(Char::uppercase))) }
    add(Fact("Flight", f.number, mono = true, kind = FactKind.COPY))
}

@Composable
fun FlightDetailScreen(f: ApiFlight, now: LocalDateTime, onClose: () -> Unit, extras: BookingExtras = BookingExtras()) {
    DetailPage(onClose) {
        DetailHeader({ BrandTile(Brands.carrierCode(f), Brands.flight(f)) }, f.number.ifBlank { "Flight" }, f.airlineName, onClose)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column { Text(f.fromCode.ifEmpty { "···" }, style = TType.display.copy(fontSize = TType.hero.fontSize), color = TColor.textPrimary); MicroLabel("Depart") }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(TColor.textSecondary))
                    HorizontalDivider(Modifier.weight(1f), color = TColor.textTertiary)
                    Icon(Icons.Filled.Flight, null, tint = TColor.textSecondary, modifier = Modifier.size(20.dp).padding(horizontal = 2.dp))
                    HorizontalDivider(Modifier.weight(1f), color = TColor.textTertiary)
                    Box(Modifier.size(6.dp).clip(CircleShape).background(TColor.textSecondary))
                }
                f.durationText?.let { Text(it, style = TType.caption, color = TColor.textSecondary) }
            }
            Column(horizontalAlignment = Alignment.End) { Text(f.toCode.ifEmpty { "···" }, style = TType.display.copy(fontSize = TType.hero.fontSize), color = TColor.textPrimary); MicroLabel("Arrive") }
        }
        f.countdown(now)?.let { StatusBanner(it, DisplayName.flightStatus(f.status) ?: "Scheduled", flightStatusColor(f)) }
        TimesCard("Departs", f.departureDate, null, "Arrives", f.arrivalDate, null)
        extras.afterTimes(this)
        extras.quickActions(this)
        extras.afterActions(this)
        FactList("Flight details", flightFacts(f))
    }
}

// Stay

enum class StayPhase { UPCOMING, IN_PROGRESS, COMPLETED, CANCELLED }

fun ApiLodging.phase(now: LocalDateTime): StayPhase {
    if (isCancelled) return StayPhase.CANCELLED
    val a = checkInDate ?: return StayPhase.UPCOMING
    val b = checkOutDate
    return when {
        now.isBefore(a) -> StayPhase.UPCOMING
        b == null || now.isBefore(b) -> StayPhase.IN_PROGRESS
        else -> StayPhase.COMPLETED
    }
}

fun stayStatusTitle(h: ApiLodging, now: LocalDateTime): String = when (h.phase(now)) {
    StayPhase.CANCELLED -> "Cancelled"
    StayPhase.IN_PROGRESS -> h.checkOutDate?.let {
        when (val d = ChronoUnit.DAYS.between(now.toLocalDate(), it.toLocalDate()).toInt()) { in Int.MIN_VALUE..0 -> "Check out today"; 1 -> "Check out tomorrow"; else -> "$d nights left" }
    } ?: "Checked in"
    StayPhase.COMPLETED -> "Stayed"
    StayPhase.UPCOMING -> h.checkInDate?.let {
        when (val d = ChronoUnit.DAYS.between(now.toLocalDate(), it.toLocalDate()).toInt()) { 0 -> "Check in today"; 1 -> "Check in tomorrow"; else -> "Check in in $d days" }
    } ?: "Dates not set"
}

private val PHONE = Regex("(?:\\+?\\d[\\d\\s().-]{7,}\\d)")
fun phonesIn(text: String): List<String> = PHONE.findAll(text).map { it.value.trim() }.filter { it.count(Char::isDigit) >= 8 }.distinct().take(2).toList()

fun stayFacts(h: ApiLodging): List<Fact> = buildList {
    h.confirmationNumber?.takeIf { it.isNotBlank() }?.let { add(Fact("Confirmation", it, mono = true, kind = FactKind.COPY)) }
    h.address?.takeIf { it.isNotBlank() }?.let { add(Fact("Address", it, kind = FactKind.ADDRESS)) }
    phonesIn(listOfNotNull(h.notes, h.address).joinToString("\n")).forEach { add(Fact("Phone", it, kind = FactKind.PHONE)) }
    h.roomType?.takeIf { it.isNotBlank() }?.let { add(Fact("Room", it)) }
    h.guests?.let { add(Fact("Guests", "$it")) }
    h.cost?.let { add(Fact("Total", Fmt.money(it, h.currency))) }
    h.taxesFees?.let { add(Fact("Taxes & fees", Fmt.money(it, h.currency))) }
    h.bookingSource?.takeIf { it.isNotBlank() }?.let { add(Fact("Booked via", it.replaceFirstChar(Char::uppercase))) }
    h.loyaltyProgram?.takeIf { it.isNotBlank() }?.let { p -> add(Fact("Loyalty", p + (h.loyaltyPoints?.let { " · ${Fmt.number(it.toDouble())} pts" } ?: ""))) }
    h.notes?.takeIf { it.isNotBlank() }?.let { add(Fact("Notes", it)) }
}

@Composable
fun StayDetailScreen(h: ApiLodging, now: LocalDateTime, onClose: () -> Unit, extras: BookingExtras = BookingExtras()) {
    val phase = h.phase(now)
    val color = when (phase) { StayPhase.CANCELLED -> TColor.red; StayPhase.IN_PROGRESS -> TColor.green; StayPhase.COMPLETED -> Color(0xFFD1D1D6); StayPhase.UPCOMING -> TColor.coral }
    DetailPage(onClose) {
        DetailHeader({ BrandTile(Brands.hotelLetter(h), Brands.hotel(h)) }, h.displayName,
            listOfNotNull(h.brand ?: if (h.isAirbnb) "Airbnb" else DisplayName.propertyType(h.propertyType), h.place).joinToString(" · "), onClose)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${h.nightTotal}", style = TType.hero, color = TColor.textPrimary)
                Text(if (h.nightTotal == 1) " night" else " nights", style = TType.title2, color = TColor.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
                Spacer(Modifier.weight(1f))
                h.costText?.let { c ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text(c, style = TType.number, color = TColor.textPrimary)
                        if (h.nightTotal > 0 && h.cost != null) Text("${Fmt.money(h.cost / h.nightTotal, h.currency, decimals = false)} / night", style = TType.caption, color = TColor.textSecondary)
                    }
                }
            }
            val sub = listOfNotNull(h.roomType, h.guests?.let { if (it == 1) "1 guest" else "$it guests" }, h.rooms?.takeIf { it > 1 }?.let { "$it rooms" }).joinToString(" · ")
            StatusBanner(stayStatusTitle(h, now), sub, color)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TimesCard("Check in", h.checkInDate, h.timeText(h.checkInDate), "Check out", h.checkOutDate, h.timeText(h.checkOutDate))
            if (h.timeApprox) Text("The booking only had dates, so these are the hotel's usual times.", style = TType.caption, color = TColor.textSecondary)
        }
        extras.afterTimes(this)
        extras.quickActions(this)
        FactList(if (h.isAirbnb) "Home details" else "Stay details", stayFacts(h))
        extras.afterActions(this)
    }
}

// Rental

fun rentalFacts(r: ApiRentalCar): List<Fact> = buildList {
    r.confirmationText?.let { add(Fact("Confirmation", it, mono = true, kind = FactKind.COPY)) }
    r.licensePlate?.takeIf { it.isNotBlank() }?.let { add(Fact("Plate", it, mono = true, kind = FactKind.COPY)) }
    add(Fact("Vehicle", listOfNotNull(r.vehicleYear?.toString(), r.vehicleName).joinToString(" ")))
    r.vehicleColor?.takeIf { it.isNotBlank() }?.let { add(Fact("Color", it.replaceFirstChar(Char::uppercase))) }
    DisplayName.vehicleClass(r.vehicleClass)?.let { add(Fact("Class", it)) }
    r.fuelType?.takeIf { it.isNotBlank() }?.let { add(Fact("Fuel", it.replaceFirstChar(Char::uppercase))) }
    r.pickupLocation?.takeIf { it.isNotBlank() }?.let { add(Fact("Pickup", it, kind = FactKind.ADDRESS)) }
    r.dropoffLocation?.takeIf { it.isNotBlank() && it != r.pickupLocation }?.let { add(Fact("Return", it, kind = FactKind.ADDRESS)) }
    r.milesDriven?.let { add(Fact("Driven", "${Fmt.number(it)} mi")) }
    if (r.pickupOdometer != null) add(Fact("Odometer", "${Fmt.number(r.pickupOdometer)} → ${r.dropoffOdometer?.let(Fmt::number) ?: "—"} mi"))
    r.costText?.let { add(Fact("Total", it)) }
    r.bookingSource?.takeIf { it.isNotBlank() }?.let { add(Fact("Booked via", it.replaceFirstChar(Char::uppercase))) }
    r.notes?.takeIf { it.isNotBlank() }?.let { add(Fact("Notes", it)) }
}

@Composable
fun RentalDetailScreen(r: ApiRentalCar, now: LocalDateTime, onClose: () -> Unit, extras: BookingExtras = BookingExtras()) {
    val p = r.pickupDate; val d = r.dropoffDate
    val (title, color) = when {
        p != null && now.isBefore(p) -> "Pick up in ${Fmt.duration(now, p)}" to TColor.blue
        d != null && now.isBefore(d) -> "Return in ${Fmt.duration(now, d)}" to TColor.green
        else -> ("Returned" + (r.rentalDays?.let { if (it == 1) " · 1 day" else " · $it days" } ?: "")) to TColor.green
    }
    DetailPage(onClose) {
        val micro = listOfNotNull(r.companyName.uppercase(), r.confirmationText, p?.let { Fmt.day(it).uppercase() }).joinToString(" • ")
        DetailHeader({ BrandTile(r.companyName, Brands.rental(r)) }, r.vehicleName, micro, onClose)
        StatusBanner(title, r.costText, color)
        Endpoint(Icons.Filled.NorthEast, r.pickupDisplayCode, r.pickupDisplayCity, p, now, r.licensePlate?.let { it to "Plate" }, Icons.Filled.DirectionsCar)
        r.rentalDays?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timer, null, tint = TColor.textSecondary, modifier = Modifier.size(15.dp))
                Text("  Rental · ${if (it == 1) "1 day" else "$it days"}", style = TType.footnote, color = TColor.textSecondary)
                HorizontalDivider(Modifier.padding(start = 8.dp), color = TColor.divider)
            }
        }
        Endpoint(Icons.Filled.SouthEast, r.dropoffDisplayCode, r.dropoffDisplayCity, d, now, r.costText?.let { it to "Total" }, Icons.Filled.CreditCard)
        extras.afterTimes(this)
        extras.quickActions(this)
        FactList("Rental details", rentalFacts(r))
        extras.afterActions(this)
    }
}

@Composable
private fun Endpoint(icon: androidx.compose.ui.graphics.vector.ImageVector, code: String, city: String, at: LocalDateTime?, now: LocalDateTime,
                     highlight: Pair<String, String>?, hIcon: androidx.compose.ui.graphics.vector.ImageVector) {
    val done = at != null && !at.isAfter(now)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = TColor.textPrimary, modifier = Modifier.size(16.dp))
                Text("  $code • $city", style = TType.headline, color = TColor.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (at != null) {
                Text(Fmt.time(at), style = TType.display, color = if (done) TColor.green else TColor.textPrimary)
                Text(Fmt.dayYear(at), style = TType.footnoteStrong, color = if (done) TColor.green else TColor.textSecondary)
            } else {
                Text("Time not recorded", style = TType.title, color = TColor.textTertiary)
                Text("Not in the booking email", style = TType.footnote, color = TColor.textTertiary)
            }
        }
        highlight?.let { (v, label) ->
            Column(horizontalAlignment = Alignment.End) {
                HighlightBlock(v, hIcon)
                Text(label, style = TType.caption, color = TColor.textSecondary, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
