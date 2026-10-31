package samalabs.trippy.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlinx.coroutines.launch
import samalabs.trippy.data.ApiInviteCode
import samalabs.trippy.data.ApiParticipant
import samalabs.trippy.data.ApiTrip
import samalabs.trippy.data.InviteRepository
import samalabs.trippy.data.InviteText
import samalabs.trippy.domain.displayTitle
import samalabs.trippy.ui.LocalContainer
import samalabs.trippy.ui.components.AuthField
import samalabs.trippy.ui.components.FormBanner
import samalabs.trippy.ui.components.PrimaryButton
import samalabs.trippy.ui.components.plainClick
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType

/** Trip menu > Invite travelers: pick a traveler who isn't on Trippy yet, then show their code. */
@Composable
fun InviteTravelersFlow(trip: ApiTrip, onDismiss: () -> Unit) {
    val placeholders = trip.participants.filter { it.isPlaceholder }
    var person by remember { mutableStateOf(placeholders.singleOrNull()) }
    val chosen = person
    if (chosen != null) { InviteCodeSheet(trip.id, trip.displayTitle, chosen, onDismiss); return }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = TColor.surfaceRaised,
        title = { Text("Invite travelers") },
        text = {
            if (placeholders.isEmpty()) Text("Everyone on this trip already has a Trippy account. Add someone by name first, then send them an invite code.")
            else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Not on Trippy yet. An invite code lets them sign up and take over their part of this trip.", style = TType.footnote, color = TColor.textSecondary)
                placeholders.forEach { p ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).plainClick { person = p }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(avatarColor(p.id)), Alignment.Center) { Text(p.initials, style = TType.footnoteStrong, color = Color.White) }
                        Text(p.name, style = TType.body, color = TColor.textPrimary, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(if (placeholders.isEmpty()) "OK" else "Cancel") } },
    )
}

/** Creates (or replaces) a traveler's invite code and offers to copy or share it (iOS `InviteCodeSheet`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteCodeSheet(tripId: Int, tripTitle: String, person: ApiParticipant, onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val repo = remember { InviteRepository(c.api) }
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var invite by remember { mutableStateOf<ApiInviteCode?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(person.id) {
        runCatching { repo.issue(tripId, person.id) }.onSuccess { invite = it }.onFailure { error = it.message ?: "Couldn't create an invite code." }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = TColor.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Invite ${person.name.substringBefore(" ")}", style = TType.title2, color = TColor.textPrimary)
            Text(
                "Send this code to ${person.name}. They enter it when they create their account, or in Profile > Join with invite code if they already have one. Their trips, stays and expenses on this trip become theirs.",
                style = TType.callout, color = TColor.textSecondary, textAlign = TextAlign.Center,
            )
            val inv = invite
            when {
                inv != null -> {
                    Text(
                        inv.code, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TColor.textPrimary, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().background(TColor.surfaceRaised, RoundedCornerShape(14.dp)).padding(vertical = 14.dp),
                    )
                    Text("Single use. Expires ${inv.expiry}. Making a new code cancels this one.", style = TType.caption, color = TColor.textTertiary)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PrimaryButton(if (copied) "Copied" else "Copy code", { clipboard.setText(AnnotatedString(inv.code)); copied = true }, Modifier.weight(1f))
                        PrimaryButton("Share…", {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, InviteText.share(tripTitle, c.sessions.current.server, inv))
                            ctx.startActivity(Intent.createChooser(send, "Share invite code"))
                        }, Modifier.weight(1f))
                    }
                }
                error != null -> FormBanner(error!!)
                else -> CircularProgressIndicator(Modifier.padding(30.dp), color = TColor.textSecondary)
            }
            TextButton(onDismiss) { Text("Done", color = TColor.blue) }
        }
    }
}

/** Profile > Join with invite code: an existing account takes over a placeholder traveler (iOS `JoinInviteSheet`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinInviteSheet(onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val repo = remember { InviteRepository(c.api) }
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = TColor.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Join with invite code", style = TType.title2, color = TColor.textPrimary)
            Text("Someone added you to a trip before you had an account, or under another email? Enter the invite code they sent to join it.", style = TType.callout, color = TColor.textSecondary)
            done?.let { FormBanner(it, tint = TColor.green, icon = Icons.Filled.CheckCircle) }
            error?.let { FormBanner(it) }
            if (done == null) {
                AuthField("Invite code", code, { code = it.uppercase() }, placeholder = "XXXXX-XXXXX", capitalization = KeyboardCapitalization.Characters, mono = true)
                PrimaryButton("Join trip", busy = busy, enabled = code.trim().length >= 10, onClick = {
                    busy = true; error = null
                    scope.launch {
                        runCatching { repo.redeem(code) }
                            .onSuccess { done = InviteText.joined(it.joined); runCatching { c.trips.refresh() } }
                            .onFailure { error = it.message ?: "That code didn't work." }
                        busy = false
                    }
                })
            } else PrimaryButton("Done", onDismiss)
        }
    }
}
