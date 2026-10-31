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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import samalabs.trippy.BuildConfig
import samalabs.trippy.data.AuthValidation
import samalabs.trippy.ui.LocalContainer
import samalabs.trippy.ui.components.AuthField
import samalabs.trippy.ui.components.FormBanner
import samalabs.trippy.ui.components.PrimaryButton
import samalabs.trippy.ui.components.plainClick
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType

/** Account and app settings: profile, server, import, appearance, password, log out, delete account (iOS `ProfileView`). */
@Composable
fun ProfileScreen(nav: Navigator, settingsRows: @Composable ColumnScope.() -> Unit = {}, cards: @Composable ColumnScope.() -> Unit = {}) {
    val c = LocalContainer.current
    val session by c.sessions.session.collectAsState()
    val p = session?.profile
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(p?.name.orEmpty()) }
    var airport by rememberSaveable { mutableStateOf(p?.homeAirport.orEmpty()) }
    LaunchedEffect(p) { name = p?.name.orEmpty(); airport = p?.homeAirport.orEmpty() }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var showServer by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    val dirty = p != null && (name.trim() != p.name.orEmpty() || airport.uppercase() != p.homeAirport.orEmpty())
    val server = session?.server ?: c.sessions.current.server

    Column(Modifier.fillMaxSize().background(TColor.canvas).safeDrawingPadding()) {
        BackBar("Profile", onBack = { nav.pop() })
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialsAvatar(p?.initialsOrGuess ?: "?", 64)
                Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(p?.displayName.orEmpty(), style = TType.title2, color = TColor.textPrimary)
                    Text(p?.email.orEmpty(), style = TType.callout, color = TColor.textSecondary)
                    p?.homeAirport?.takeIf { it.isNotBlank() }?.let { home ->
                        Row(Modifier.padding(top = 2.dp).background(TColor.surfaceRaised, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Home, null, tint = TColor.textSecondary, modifier = Modifier.size(12.dp))
                            Text(" Home $home", style = TType.footnoteStrong, color = TColor.textSecondary)
                        }
                    }
                }
            }
            message?.let { (text, ok) -> FormBanner(text, tint = if (ok) TColor.green else TColor.red, icon = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Report) }
            if (p != null) Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AuthField("Name", name, { name = it }, placeholder = "Your name", capitalization = KeyboardCapitalization.Words)
                AuthField("Home airport", airport, { airport = it.take(3) }, placeholder = "SFO", capitalization = KeyboardCapitalization.Characters)
                if (dirty) PrimaryButton("Save changes", busy = saving, onClick = {
                    if (!AuthValidation.airport(airport.uppercase())) { message = "Home airport is a 3-letter code, like SFO." to false; return@PrimaryButton }
                    saving = true
                    scope.launch {
                        message = runCatching { c.auth.updateProfile(name.trim(), airport) }.fold({ "Saved" to true }, { (it.message ?: "Couldn't save.") to false })
                        saving = false
                    }
                })
            }
            cards()
            Column {
                HorizontalDivider(color = TColor.divider)
                ProfileTile(Icons.Filled.Download, "Import trips", TColor.green) { nav.push(ImportRoute(null)) }
                TileDivider()
                ProfileTile(Icons.Filled.PersonAdd, "Join with invite code", TColor.blue) { showJoin = true }
                settingsRows()
                TileDivider()
                ProfileTile(Icons.Filled.Delete, "Recently Deleted", TColor.red) { nav.push(Route.Trash) }
                TileDivider()
                ProfileTile(Icons.Filled.DarkMode, "Appearance", TColor.purple, trailing = "Dark", onClick = null)
                TileDivider()
                ProfileTile(Icons.Filled.Dns, "Server · " + server.removePrefix("http://"), TColor.blue) { showServer = true }
                HorizontalDivider(color = TColor.divider)
            }
            if (p != null) Column {
                HorizontalDivider(color = TColor.divider)
                ProfileTile(Icons.Filled.Key, "Change password", TColor.amber) { showPassword = true }
                TileDivider()
                ProfileTile(Icons.AutoMirrored.Filled.Logout, "Log out", TColor.orange) { confirmLogout = true }
                TileDivider()
                ProfileTile(Icons.Filled.Delete, "Delete account", TColor.red) { showDelete = true }
                HorizontalDivider(color = TColor.divider)
            }
            Text("Trippy ${BuildConfig.VERSION_NAME} · $server", style = TType.caption, color = TColor.textTertiary, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.size(12.dp))
        }
    }

    if (showServer) ServerSettingsSheet(onDismiss = { showServer = false }, onSaved = { showServer = false; scope.launch { c.trips.refresh() } })
    if (showJoin) JoinInviteSheet { showJoin = false }
    if (showPassword) ChangePasswordSheet { showPassword = false }
    if (showDelete) DeleteAccountSheet { showDelete = false }
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false }, containerColor = TColor.surfaceRaised,
        title = { Text("Log out of Trippy?") },
        text = { Text("This signs you out on every device.") },
        confirmButton = { TextButton({ confirmLogout = false; scope.launch { c.trips.clear(); c.auth.logOut() } }) { Text("Log out", color = TColor.red) } },
        dismissButton = { TextButton({ confirmLogout = false }) { Text("Cancel") } },
    )
}

@Composable internal fun TileDivider() = HorizontalDivider(Modifier.padding(start = 44.dp), color = TColor.divider)

/** Hairline-row action with a colored icon (iOS `QuickActionTile`). No [onClick] means an info row. */
@Composable
internal fun ProfileTile(icon: ImageVector, title: String, color: Color, trailing: String? = null, badge: Int = 0, onClick: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.plainClick(onClick) else Modifier).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(32.dp), Alignment.CenterStart) { Icon(icon, null, tint = color, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(title, style = TType.body, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
        if (badge > 0) Text("$badge", style = TType.label, color = Color.White, modifier = Modifier.padding(end = 8.dp).background(TColor.red, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 2.dp))
        trailing?.let { Text(it, style = TType.callout, color = TColor.textSecondary) }
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TColor.textTertiary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangePasswordSheet(onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = TColor.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Change password", style = TType.title2, color = TColor.textPrimary)
            error?.let { FormBanner(it) }
            AuthField("Current password", current, { current = it }, secure = true)
            AuthField("New password", new, { new = it }, placeholder = "At least 8 characters", secure = true)
            Text("Other devices will be signed out.", style = TType.footnote, color = TColor.textSecondary)
            PrimaryButton("Change password", busy = busy, enabled = current.isNotEmpty(), onClick = {
                AuthValidation.passwordProblem(new)?.let { error = it; return@PrimaryButton }
                busy = true
                scope.launch {
                    runCatching { c.auth.changePassword(current, new) }.onSuccess { onDismiss() }.onFailure { error = it.message ?: "Couldn't change the password." }
                    busy = false
                }
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteAccountSheet(onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = TColor.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Delete account", style = TType.title2, color = TColor.textPrimary)
            FormBanner(
                "This deletes your account, the trips you own, and bookings that aren't on a trip. Shared expenses stay on other people's ledgers as \"Former member\". This can't be undone.",
                tint = TColor.red, icon = Icons.Filled.Report,
            )
            error?.let { FormBanner(it) }
            AuthField("Password", password, { password = it }, secure = true)
            PrimaryButton("Delete my account", busy = busy, enabled = password.isNotEmpty(), color = TColor.red, onClick = {
                busy = true
                scope.launch {
                    runCatching { c.auth.deleteAccount(password); c.trips.clear() }.onFailure { error = it.message ?: "Couldn't delete the account." }
                    busy = false
                }
            })
        }
    }
}
