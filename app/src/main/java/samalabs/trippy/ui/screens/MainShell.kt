package samalabs.trippy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import samalabs.trippy.ui.LocalContainer
import samalabs.trippy.ui.components.PrimaryButton
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType

@Composable
fun MainShell() {
    val container = LocalContainer.current
    val session by container.sessions.session.collectAsState()
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(TColor.canvas).safeDrawingPadding().padding(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("My Trips", style = TType.largeTitle, color = TColor.textPrimary)
            Text("Signed in as ${session?.profile?.displayName}", style = TType.callout, color = TColor.textSecondary)
            PrimaryButton("Log out", color = TColor.surfaceRaised, onClick = { scope.launch { container.auth.logOut() } })
        }
    }
}
