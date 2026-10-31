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
fun CheckInCard(f: ApiFlight, now: LocalDateTime) {
    val c = AirlineCheckIn.countdown(f, now) ?: return
    val info = AirlineCheckIn.info(f)
    val ctx = LocalContext.current
    val tint = if (c.open) TColor.green else TColor.amber
    Row(
        Modifier.fillMaxWidth().trippyCard().plainClick { DeviceActions.open(ctx, info.url) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)), Alignment.Center) {
            Icon(if (c.open) Icons.Filled.Verified else Icons.Filled.Schedule, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(c.text, style = TType.headline, color = TColor.textPrimary)
            Text(AirlineCheckIn.subtitle(f), style = TType.footnote, color = TColor.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(if (c.open) "Check in" else "Open", style = TType.calloutStrong, color = TColor.blue)
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TColor.blue, modifier = Modifier.padding(start = 4.dp).size(16.dp))
    }
}

