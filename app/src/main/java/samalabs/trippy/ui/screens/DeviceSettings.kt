package samalabs.trippy.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import samalabs.trippy.ui.LocalContainer
import samalabs.trippy.ui.components.trippyCard
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType

/** On-device settings: trip reminders (and the home-screen widget). */
@Composable
fun DeviceSettings() {
    val c = LocalContainer.current
    var on by remember { mutableStateOf(c.reminders.enabled && c.reminders.permitted) }
    var note by remember { mutableStateOf<String?>(null) }
    fun enable() {
        val n = c.reminders.enable(c.trips.state.value.trips)
        on = true
        note = if (n == 0) "Nothing in the next 14 days yet." else "$n reminder${if (n == 1) "" else "s"} set for the next 14 days."
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) enable() else { on = false; note = "Notifications are off for Trippy in system settings." }
    }
    Column(Modifier.trippyCard()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NotificationsActive, null, tint = TColor.coral, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Trip reminders", style = TType.body, color = TColor.textPrimary)
                Text(note ?: "Check-in opening, 3 hours before flights, hotel check-in mornings.", style = TType.footnote, color = TColor.textSecondary)
            }
            Switch(
                checked = on,
                onCheckedChange = { want ->
                    if (!want) { c.reminders.disable(); on = false; note = null }
                    else if (Build.VERSION.SDK_INT >= 33 && !c.reminders.permitted) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else enable()
                },
                colors = SwitchDefaults.colors(checkedTrackColor = TColor.coral),
            )
        }
        val ctx = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.material3.HorizontalDivider(color = TColor.divider)
        AccountLink("Add home-screen widget", Icons.Filled.Widgets, TColor.blue) {
            if (!samalabs.trippy.widget.WidgetStore.requestPin(ctx))
                android.widget.Toast.makeText(ctx, "Long-press your home screen, tap Widgets, then find Trippy.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
