package samalabs.trippy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import samalabs.trippy.BuildConfig
import samalabs.trippy.data.ApiClient
import samalabs.trippy.data.ServerHealth
import samalabs.trippy.data.ServerUrl
import samalabs.trippy.data.SessionStore
import samalabs.trippy.ui.LocalContainer
import samalabs.trippy.ui.LocalNetworkAccess
import samalabs.trippy.data.LocalNetwork
import samalabs.trippy.ui.components.AuthField
import samalabs.trippy.ui.components.PrimaryButton
import samalabs.trippy.ui.components.ServerCheck
import samalabs.trippy.ui.components.ServerStatusRow
import samalabs.trippy.ui.components.TextAction
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType

/** Checks the current server's /health and remembers the answer (sign-up uses its mode). */
@Stable
class ServerStatus(private val api: ApiClient, private val sessions: SessionStore) {
    var check by mutableStateOf(ServerCheck.Checking); private set
    var health by mutableStateOf<ServerHealth?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var blocked by mutableStateOf(false); private set
    val baseUrl: String get() = sessions.current.server

    suspend fun refresh(localNetworkBlocked: Boolean = blocked) {
        blocked = localNetworkBlocked
        check = ServerCheck.Checking
        try {
            health = api.health(baseUrl); error = null; check = ServerCheck.Ok
        } catch (e: Exception) {
            health = null; error = e.message; check = ServerCheck.Failed
        }
    }

    val detail: String
        get() = when (check) {
            ServerCheck.Checking -> "Checking…"
            ServerCheck.Ok -> health?.summary.orEmpty()
            ServerCheck.Failed -> if (blocked) "Allow Nearby devices to reach this server" else "Not reachable · tap to change"
        }
}

@Composable
fun ServerRow(status: ServerStatus, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val access = LocalNetworkAccess.current
    val tap = if (status.check == ServerCheck.Failed && access.blocked) access.request else onClick
    ServerStatusRow(ServerUrl.displayHost(status.baseUrl), status.check, status.detail, tap, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsSheet(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf(container.sessions.current.server) }
    var status by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var testing by remember { mutableStateOf(false) }
    val access = LocalNetworkAccess.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = TColor.surface) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Server", style = TType.title2, color = TColor.textPrimary)
            AuthField("Trippy server", text, { text = it }, placeholder = "https://trippy.example.com", keyboardType = KeyboardType.Uri, mono = true)
            Text(
                "The address of a Trippy server you or a friend runs, e.g. https://trippy.example.com, " +
                    "http://192.168.1.20:8000 or http://my-server:8000 over Tailscale.",
                style = TType.footnote, color = TColor.textSecondary,
            )
            status?.let { (ok, msg) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning, null, tint = if (ok) TColor.green else TColor.orange, modifier = Modifier.size(16.dp))
                    Text(msg, style = TType.footnote, color = if (ok) TColor.green else TColor.orange)
                }
            }
            PrimaryButton("Test connection", busy = testing, color = TColor.surfaceRaised, onClick = {
                val url = ServerUrl.normalize(text)
                if (url == null) { status = false to "That isn't a valid URL. Try https://trippy.example.com or http://192.168.1.20:8000."; return@PrimaryButton }
                testing = true
                scope.launch {
                    status = if (LocalNetwork.needsPermission(url) && access.blocked && url == container.sessions.current.server) {
                        access.request()
                        false to "Android needs the Nearby devices permission to reach servers on your local network."
                    } else try {
                        true to "Connected · " + container.api.health(url).summary
                    } catch (e: Exception) {
                        false to listOfNotNull(e.message ?: "Can't reach the server.", e.cause?.message).joinToString("\n")
                    }
                    testing = false
                }
            })
            PrimaryButton("Save", onClick = {
                val url = ServerUrl.normalize(text)
                if (url == null) { status = false to "Enter a URL like https://trippy.example.com"; return@PrimaryButton }
                scope.launch { container.sessions.setServer(url); onSaved(); sheet.hide(); onDismiss() }
            })
            Spacer(Modifier.size(2.dp))
            TextAction("Reset to default", color = TColor.red, modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {
                text = BuildConfig.DEFAULT_SERVER
                scope.launch { container.sessions.setServer(BuildConfig.DEFAULT_SERVER); onSaved() }
                status = null
            })
        }
    }
}

