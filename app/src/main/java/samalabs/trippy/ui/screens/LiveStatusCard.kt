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
import samalabs.trippy.data.LiveStatus
import samalabs.trippy.data.OfflineException
import samalabs.trippy.data.liveStatus
import samalabs.trippy.domain.AirlineCheckIn
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

@Composable
fun LiveStatusCard(f: ApiFlight) {
    val api = LocalContainer.current.api
    var result by remember(f.id) { mutableStateOf<LiveStatus?>(null) }
    var error by remember(f.id) { mutableStateOf<String?>(null) }
    var loading by remember(f.id) { mutableStateOf(false) }
    var tick by remember(f.id) { mutableIntStateOf(0) }
    LaunchedEffect(f.id, tick) {
        loading = true
        try { result = api.liveStatus(f.id); error = null }
        catch (e: OfflineException) { error = "Live status needs a connection." }
        catch (e: ApiException) { error = e.message ?: "Live status is unavailable." }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error = "Live status needs a connection." }
        loading = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Live status", style = TType.title3, color = TColor.textPrimary, modifier = Modifier.weight(1f))
            if (loading) CircularProgressIndicator(Modifier.size(16.dp), color = TColor.textSecondary, strokeWidth = 2.dp)
            else if (result?.configured == true) Icon(Icons.Filled.Refresh, "Refresh live status", tint = TColor.blue, modifier = Modifier.size(22.dp).plainClick { tick++ })
        }
        val s = result?.status
        val r = result
        when {
            s != null -> LiveStatusBody(s)
            r != null && !r.configured -> Row(Modifier.fillMaxWidth().trippyCard().padding(20.dp)) {
                Icon(Icons.Filled.SignalWifiOff, null, tint = TColor.textTertiary, modifier = Modifier.size(20.dp))
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Live status isn't configured", style = TType.headline, color = TColor.textPrimary)
                    Text("Your Trippy server can show gates, delays and baggage belts once its admin adds a flight data key (see SELF_HOSTING.md).",
                        style = TType.footnote, color = TColor.textSecondary)
                }
            }
            (error ?: r?.message) != null -> Text(error ?: r?.message.orEmpty(), style = TType.footnote, color = TColor.textSecondary,
                modifier = Modifier.fillMaxWidth().trippyCard().padding(20.dp))
        }
    }
}

@Composable
private fun LiveStatusBody(s: LiveStatus.Status) {
    val color = when (s.tone) { LiveStatus.Tone.RED -> TColor.red; LiveStatus.Tone.AMBER -> TColor.amber; LiveStatus.Tone.GRAY -> TColor.textSecondary; LiveStatus.Tone.GREEN -> TColor.green }
    Column(Modifier.fillMaxWidth().trippyCard().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(color))
            Text(s.label, style = TType.headline, color = TColor.textPrimary, modifier = Modifier.weight(1f).padding(start = 8.dp))
            s.fetchedInstant?.let { Text("Updated " + Fmt.ago(it.toEpochMilli(), System.currentTimeMillis()), style = TType.caption, color = TColor.textTertiary) }
        }
        val cells = buildList {
            add("Departure gate" to s.departureGateText); add("Arrival gate" to s.arrivalGateText)
            add("Expected departure" to s.expectedDeparture); add("Expected arrival" to s.expectedArrival)
            s.baggageClaim?.let { add("Baggage belt" to it) }
        }
        cells.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        MicroLabel(label)
                        Text(value?.takeIf { it.isNotBlank() } ?: "—", style = TType.headline, color = TColor.textPrimary)
                    }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
        Text("From ${s.providerName}", style = TType.caption, color = TColor.textTertiary)
    }
}

