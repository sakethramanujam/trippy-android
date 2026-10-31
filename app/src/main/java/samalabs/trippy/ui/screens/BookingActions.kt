package samalabs.trippy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import samalabs.trippy.data.ApiException
import samalabs.trippy.data.ApiFlight
import samalabs.trippy.data.ApiLodging
import samalabs.trippy.data.ApiRentalCar
import samalabs.trippy.data.OfflineException
import samalabs.trippy.domain.CalendarDraft
import samalabs.trippy.domain.Fmt
import samalabs.trippy.domain.Links
import samalabs.trippy.domain.fromCode
import samalabs.trippy.ui.DeviceActions
import samalabs.trippy.ui.LocalContainer
import samalabs.trippy.ui.components.MicroLabel
import samalabs.trippy.ui.components.QuickAction
import samalabs.trippy.ui.components.QuickActions
import samalabs.trippy.ui.components.plainClick
import samalabs.trippy.ui.components.trippyCard
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType
import java.time.LocalDateTime

/** Flight: check-in card, Directions + Add to Calendar, live status. */
fun flightExtras(f: ApiFlight, now: LocalDateTime) = BookingExtras(
    quickActions = {
        val ctx = LocalContext.current
        QuickActions {
            QuickAction("Directions to ${f.fromCode}", Icons.Filled.Directions, TColor.blue,
                { DeviceActions.open(ctx, Links.directions(Links.airportQuery(f.fromCode))) }, Modifier.weight(1f))
            CalendarDraft.flight(f)?.let { d ->
                QuickAction("Add to Calendar", Icons.Filled.CalendarMonth, TColor.coral, { DeviceActions.addToCalendar(ctx, d) }, Modifier.weight(1f))
            }
        }
    },
)

fun stayExtras(h: ApiLodging) = BookingExtras(
    quickActions = {
        val ctx = LocalContext.current
        val where = h.address?.takeIf { it.isNotBlank() } ?: listOfNotNull(h.name, h.city).joinToString(", ")
        QuickActions {
            if (where.isNotBlank()) QuickAction("Directions", Icons.Filled.Directions, TColor.blue, { DeviceActions.open(ctx, Links.directions(where)) }, Modifier.weight(1f))
            CalendarDraft.stay(h)?.let { d -> QuickAction("Add to Calendar", Icons.Filled.CalendarMonth, TColor.coral, { DeviceActions.addToCalendar(ctx, d) }, Modifier.weight(1f)) }
        }
    },
)

fun rentalExtras(r: ApiRentalCar) = BookingExtras(
    quickActions = {
        val ctx = LocalContext.current
        val where = r.pickupLocation?.takeIf { it.isNotBlank() } ?: r.pickupCode?.let { Links.airportQuery(it) }
        QuickActions {
            where?.let { QuickAction("Directions to pickup", Icons.Filled.Directions, TColor.blue, { DeviceActions.open(ctx, Links.directions(it)) }, Modifier.weight(1f)) }
            CalendarDraft.rental(r)?.let { d -> QuickAction("Add to Calendar", Icons.Filled.CalendarMonth, TColor.coral, { DeviceActions.addToCalendar(ctx, d) }, Modifier.weight(1f)) }
        }
    },
)

