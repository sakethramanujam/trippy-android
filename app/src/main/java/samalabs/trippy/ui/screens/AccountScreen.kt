package samalabs.trippy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import samalabs.trippy.ui.LocalContainer
import samalabs.trippy.ui.components.PrimaryButton
import samalabs.trippy.ui.components.trippyCard
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType

/** Top bar shared by pushed screens: back arrow and a title. */
@Composable
fun BackBar(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TColor.textPrimary, modifier = Modifier.size(20.dp)) }
        Text(title, style = TType.headline, color = TColor.textPrimary, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1)
        trailing()
    }
}

@Composable
fun AccountRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(label, style = TType.body, color = TColor.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = TType.body, color = TColor.textPrimary)
    }
}

@Composable
fun AccountScreen(onBack: () -> Unit, extra: @Composable () -> Unit = {}) {
    val c = LocalContainer.current
    val session by c.sessions.session.collectAsState()
    val scope = rememberCoroutineScope()
    val p = session?.profile
    Column(Modifier.fillMaxSize().background(TColor.canvas).safeDrawingPadding()) {
        BackBar("Account", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(p?.displayName ?: "", style = TType.largeTitle, color = TColor.textPrimary)
            Column(Modifier.trippyCard().padding(horizontal = 16.dp)) {
                AccountRow("Email", p?.email ?: "—")
                HorizontalDivider(color = TColor.divider)
                AccountRow("Home airport", p?.homeAirport ?: "—")
                HorizontalDivider(color = TColor.divider)
                AccountRow("Server", session?.server?.removePrefix("http://")?.removePrefix("https://") ?: "—")
            }
            extra()
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Log out", color = TColor.surfaceRaised, onClick = { scope.launch { c.trips.clear(); c.auth.logOut() } })
        }
    }
}

@Composable
fun TripPlaceholder(id: Int, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(TColor.canvas).safeDrawingPadding()) { BackBar("Trip $id", onBack) }
}
