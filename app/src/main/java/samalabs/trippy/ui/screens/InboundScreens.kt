package samalabs.trippy.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import samalabs.trippy.data.ApiInboundEmail
import samalabs.trippy.data.ApiInboundSettings
import samalabs.trippy.ui.LocalContainer
import samalabs.trippy.ui.components.FormBanner
import samalabs.trippy.ui.components.plainClick
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType

/** Profile > Forwarded emails. */
data object InboxRoute : Route

private val ApiInboundEmail.statusColor: Color
    get() = when (status) {
        "imported" -> TColor.green
        "failed" -> TColor.red
        "duplicate", "nothing_found" -> TColor.textSecondary
        else -> TColor.orange
    }

@Composable
private fun GrayChip(text: String, icon: ImageVector? = null) {
    Row(Modifier.background(TColor.surfaceRaised, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(it, null, tint = TColor.textSecondary, modifier = Modifier.size(12.dp).padding(end = 2.dp)) }
        Text(text, style = TType.label, color = TColor.textSecondary)
    }
}

@Composable
private fun TintedButton(text: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.15f)).plainClick(onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Text("  $text", style = TType.footnoteStrong, color = tint)
    }
}

/** Profile card with the user's forwarding address (iOS `InboundAddressCard`). Shows the server's message when forwarding is off. */
@Composable
fun InboundAddressCard() {
    val c = LocalContainer.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<ApiInboundSettings?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { c.inbound.settings() }.onSuccess { settings = it }.onFailure { failed = it.message ?: "Couldn't load forwarding settings." } }
    Column(Modifier.fillMaxWidth().background(TColor.surface, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val s = settings
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MarkEmailUnread, null, tint = if (s?.enabled == true) TColor.blue else TColor.textTertiary, modifier = Modifier.size(20.dp))
            Text("  Forward bookings", style = TType.headline, color = Color.White, modifier = Modifier.weight(1f))
            if (s?.enabled == true) GrayChip(s.mode.uppercase())
        }
        when {
            s != null -> {
                val address = s.address
                if (s.enabled && address != null) {
                    Text(address, style = TType.mono, color = Color.White, maxLines = 2, modifier = Modifier.fillMaxWidth().background(TColor.surfaceHigh, RoundedCornerShape(10.dp)).padding(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TintedButton(if (copied) "Copied" else "Copy", if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy, TColor.blue) { clipboard.setText(AnnotatedString(address)); copied = true }
                        TintedButton("New address", Icons.Filled.Autorenew, TColor.textSecondary) { confirm = true }
                    }
                }
                if (s.message.isNotBlank()) Text(s.message, style = TType.footnote, color = TColor.textSecondary)
                val me = s.accountEmail
                if (s.enabled && s.senderMatch && me != null) Text("Mail sent from $me is also accepted.", style = TType.caption, color = TColor.textTertiary)
            }
            failed != null -> Text(failed!!, style = TType.footnote, color = TColor.textSecondary)
            else -> CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, containerColor = TColor.surfaceRaised,
        title = { Text("Make a new address?") },
        text = { Text("The old address stops working right away. Use this if it leaked or you're getting spam.") },
        confirmButton = {
            TextButton({
                confirm = false
                scope.launch { runCatching { c.inbound.settings(regenerate = true) }.onSuccess { settings = it; copied = false }.onFailure { failed = it.message } }
            }) { Text("New address", color = TColor.red) }
        },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
    )
}

/** Log of forwarded emails with retry and delete (iOS `ForwardedEmailsView`). Marks everything seen on open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardedEmailsScreen(onBack: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val enabled by c.inbound.enabled.collectAsState()
    val emails = remember { mutableStateListOf<ApiInboundEmail>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<Int?>(null) }
    val expanded = remember { mutableStateListOf<Int>() }

    suspend fun load() {
        loading = true
        runCatching { c.inbound.emails() }
            .onSuccess { emails.clear(); emails.addAll(it); error = null }
            .onFailure { error = it.message ?: "Couldn't load forwarded emails." }
        loading = false
    }
    LaunchedEffect(Unit) {
        runCatching { c.inbound.settings() }
        load()
        c.inbound.markSeen()
    }
    fun retry(e: ApiInboundEmail) = scope.launch {
        busy = e.id
        runCatching { c.inbound.retry(e.id) }
            .onSuccess { u -> emails.indexOfFirst { it.id == e.id }.takeIf { it >= 0 }?.let { emails[it] = u }; runCatching { c.trips.refresh() } }
            .onFailure { error = it.message }
        busy = null
    }
    fun delete(e: ApiInboundEmail) = scope.launch {
        runCatching { c.inbound.delete(e.id) }.onSuccess { emails.removeAll { it.id == e.id } }.onFailure { error = it.message }
    }

    Column(Modifier.fillMaxSize().background(TColor.canvas).safeDrawingPadding()) {
        BackBar("Forwarded emails", onBack)
        PullToRefreshBox(isRefreshing = loading && emails.isNotEmpty(), onRefresh = { scope.launch { load() } }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!enabled && !loading) item {
                    FormBanner("Email forwarding isn't set up on this server. Older forwarded emails still show here.", tint = TColor.orange, icon = Icons.Filled.MarkEmailUnread)
                }
                error?.let { msg -> item { FormBanner(msg, icon = Icons.Filled.Report) } }
                if (emails.isEmpty() && !loading) item {
                    Column(Modifier.fillMaxWidth().background(TColor.surface, RoundedCornerShape(16.dp)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Inbox, null, tint = TColor.textSecondary, modifier = Modifier.size(36.dp))
                        Text("No forwarded emails", style = TType.headline, color = TColor.textPrimary)
                        Text("Forward a booking confirmation, calendar invite, boarding pass or PDF receipt to your Trippy address and it shows up here.", style = TType.footnote, color = TColor.textSecondary, textAlign = TextAlign.Center)
                    }
                }
                if (loading && emails.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { CircularProgressIndicator(color = Color.White) } }
                items(emails, key = { it.id }) { e ->
                    EmailRow(e, busy == e.id, e.id in expanded, toggle = { if (e.id in expanded) expanded.remove(e.id) else expanded.add(e.id) }, retry = { retry(e) }, delete = { delete(e) })
                }
                item { Spacer(Modifier.size(24.dp)) }
            }
        }
    }
}

@Composable
private fun EmailRow(e: ApiInboundEmail, busy: Boolean, open: Boolean, toggle: () -> Unit, retry: () -> Unit, delete: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(TColor.surface, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!e.seen) Box(Modifier.padding(end = 8.dp).size(8.dp).clip(CircleShape).background(TColor.blue))
            Text(e.subject?.takeIf { it.isNotEmpty() } ?: "(no subject)", style = TType.headline, color = Color.White, maxLines = 2, modifier = Modifier.weight(1f))
            e.receivedInstant?.let {
                Text(DateUtils.getRelativeTimeSpanString(it.toEpochMilli()).toString(), style = TType.caption, color = TColor.textTertiary, modifier = Modifier.padding(start = 4.dp))
            }
        }
        e.sender?.let { Text(it, style = TType.footnote, color = TColor.textSecondary, maxLines = 1) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(e.statusText, style = TType.label, color = e.statusColor, modifier = Modifier.background(e.statusColor.copy(alpha = 0.15f), RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 3.dp))
            if (e.newTripIds.isNotEmpty()) GrayChip("New trip", Icons.Filled.Luggage)
            if (e.attempts > 1) GrayChip("${e.attempts} tries")
            Spacer(Modifier.weight(1f))
            if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
        }
        if (e.status == "failed") e.error?.let { Text(it, style = TType.footnote, color = TColor.red.copy(alpha = 0.9f)) }
        if (open) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            e.created.forEach { DetailLine(Icons.Filled.AddCircle, it.label, TColor.green) }
            e.skipped.forEach { DetailLine(Icons.Filled.RemoveCircleOutline, it.skippedLabel, TColor.textSecondary) }
            e.parts.forEach { Text(it.label, style = TType.caption, color = TColor.textTertiary) }
            e.routedBy?.let { Text("Matched by $it", style = TType.caption, color = TColor.textTertiary) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(top = 2.dp)) {
            Text(if (open) "Hide details" else "Details", style = TType.footnoteStrong, color = TColor.blue, modifier = Modifier.plainClick(toggle))
            if (e.canRetry) Text("Retry", style = TType.footnoteStrong, color = TColor.blue, modifier = Modifier.plainClick(retry))
            Text("Delete", style = TType.footnoteStrong, color = TColor.red, modifier = Modifier.plainClick(delete))
        }
    }
}

@Composable
private fun DetailLine(icon: ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = TType.footnote, color = tint)
    }
}
