package samalabs.trippy.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import samalabs.trippy.data.ApiException
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

/** Trip menu > Invite travelers: pick a traveler who isn't on Trippy yet (or add one by name), then show their code. */
@Composable
fun InviteTravelersFlow(trip: ApiTrip, onDismiss: () -> Unit) {
    val placeholders = trip.participants.filter { it.isPlaceholder }
    var person by remember { mutableStateOf<ApiParticipant?>(null) }
    var addingNew by remember { mutableStateOf(placeholders.isEmpty()) }
    val chosen = person
    if (chosen != null) {
        InviteCodeSheet(trip.id, trip.displayTitle, chosen, onDismiss)
        return
    }
    if (addingNew) {
        AddTravelerSheet(
            trip = trip,
            onDismiss = onDismiss,
            onInvitePerson = { created ->
                addingNew = false
                person = created
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = TColor.surfaceRaised,
        title = { Text("Invite travelers") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Pick someone on this trip who isn't on Trippy yet to generate a single-use invite code, or add a new traveler.", style = TType.footnote, color = TColor.textSecondary)
                placeholders.forEach { p ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).plainClick { person = p }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(avatarColor(p.id)), Alignment.Center) { Text(p.initials, style = TType.footnoteStrong, color = Color.White) }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(p.name, style = TType.body, color = TColor.textPrimary)
                            Text("Tap to generate invite code", style = TType.caption, color = TColor.coral)
                        }
                        Icon(Icons.Filled.Key, null, tint = TColor.coral, modifier = Modifier.size(16.dp))
                    }
                }
                Row(
                    Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TColor.surface)
                        .plainClick { addingNew = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Add, null, tint = TColor.blue, modifier = Modifier.size(18.dp))
                    Text("  Add another traveler…", style = TType.calloutStrong, color = TColor.blue)
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** Add a traveler by name (+ optional email) or from previously-added people, and optionally generate their invite code right away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTravelerSheet(
    trip: ApiTrip,
    onDismiss: () -> Unit,
    onInvitePerson: ((ApiParticipant) -> Unit)? = null,
) {
    val c = LocalContainer.current
    val repo = remember { InviteRepository(c.api) }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var known by remember { mutableStateOf<List<ApiParticipant>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(trip.id) {
        runCatching { repo.knownUsers() }.onSuccess { known = it }
    }

    val existingIds = remember(trip.participants, trip.ownerId) {
        trip.participants.map { it.id }.toSet() + listOfNotNull(trip.ownerId)
    }
    val suggestions = remember(known, existingIds) {
        known.filter { it.id !in existingIds }
    }
    val trimmedName = name.trim()
    val trimmedEmail = email.trim()
    val valid = trimmedName.isNotEmpty() && (trimmedEmail.isEmpty() || (trimmedEmail.contains("@") && trimmedEmail.contains(".")))

    fun submit(generateCode: Boolean) {
        if (!valid || busy) return
        busy = true
        error = null
        val beforeIds = trip.participants.map { it.id }.toSet()
        scope.launch {
            runCatching { c.trips.addTraveler(trip.id, trimmedName, trimmedEmail.ifEmpty { null }) }
                .onSuccess { updated ->
                    busy = false
                    val added = updated.participants.firstOrNull { it.id !in beforeIds }
                        ?: updated.participants.firstOrNull { it.name.equals(trimmedName, ignoreCase = true) }
                    if (generateCode && added != null && added.isPlaceholder && onInvitePerson != null) {
                        onInvitePerson(added)
                    } else {
                        onDismiss()
                    }
                }
                .onFailure {
                    busy = false
                    error = it.message ?: "Couldn't add traveler."
                }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TColor.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Add traveler", style = TType.title2, color = TColor.textPrimary)
            Text(
                "Add someone by name to split costs on ${trip.displayTitle}, or generate a single-use invite code so they can join the trip on their phone.",
                style = TType.callout,
                color = TColor.textSecondary,
            )
            error?.let { FormBanner(it) }
            AuthField("Name", name, { name = it }, placeholder = "e.g. Maya Chen", capitalization = KeyboardCapitalization.Words)
            AuthField("Email (optional)", email, { email = it }, placeholder = "maya@example.com")

            if (onInvitePerson != null) {
                PrimaryButton("Add & get invite code", busy = busy, enabled = valid, onClick = { submit(generateCode = true) })
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(TColor.surfaceRaised)
                        .border(1.dp, TColor.hairline, RoundedCornerShape(14.dp))
                        .plainClick { submit(generateCode = false) }
                        .padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("Just add to trip", style = TType.calloutStrong, color = if (valid) TColor.textPrimary else TColor.textTertiary)
                }
            } else {
                PrimaryButton("Add to trip", busy = busy, enabled = valid, onClick = { submit(generateCode = false) })
            }

            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("PEOPLE YOU'VE ADDED BEFORE", style = TType.label, color = TColor.textSecondary)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.take(6).forEach { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(TColor.surfaceRaised)
                                .plainClick {
                                    if (busy) return@plainClick
                                    busy = true
                                    error = null
                                    scope.launch {
                                        runCatching { c.trips.addKnownParticipant(trip.id, p.id) }
                                            .onSuccess {
                                                busy = false
                                                if (p.isPlaceholder && onInvitePerson != null) onInvitePerson(p) else onDismiss()
                                            }
                                            .onFailure {
                                                busy = false
                                                error = it.message ?: "Couldn't add ${p.name}."
                                            }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(34.dp).clip(CircleShape).background(avatarColor(p.id)), Alignment.Center) {
                                Text(p.initials, style = TType.footnoteStrong, color = Color.White)
                            }
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(p.name, style = TType.body, color = TColor.textPrimary)
                                if (p.isPlaceholder) {
                                    Text("Not signed up yet · tap to add", style = TType.caption, color = TColor.textSecondary)
                                }
                            }
                            Icon(Icons.Filled.Add, null, tint = TColor.blue, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Tap a traveler's avatar in TripDetailScreen: share their invite code (if placeholder) or remove them from the trip. */
@Composable
fun TravelerOptionsDialog(
    trip: ApiTrip,
    person: ApiParticipant,
    onDismiss: () -> Unit,
    onShareInvite: () -> Unit,
) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var conflictMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    if (conflictMessage != null) {
        AlertDialog(
            onDismissRequest = { conflictMessage = null; onDismiss() },
            containerColor = TColor.surfaceRaised,
            title = { Text("Unsettled expenses") },
            text = { Text(conflictMessage.orEmpty()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching { c.trips.removeTraveler(trip.id, person.id, force = true) }
                                .onSuccess { conflictMessage = null; onDismiss() }
                                .onFailure { error = it.message; conflictMessage = null }
                            busy = false
                        }
                    },
                ) { Text("Remove anyway", color = TColor.red) }
            },
            dismissButton = {
                TextButton(onClick = { conflictMessage = null; onDismiss() }) { Text("Cancel") }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TColor.surfaceRaised,
        title = { Text(person.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (person.isPlaceholder) "Not on Trippy yet. Generate an invite code so they can sign up (or join from Profile) and take over their part of this trip."
                    else if (person.id == trip.ownerId) "Trip organizer."
                    else "Joined on Trippy. Removing them keeps them in your contacts so they can be added again.",
                    style = TType.footnote,
                    color = TColor.textSecondary,
                )
                error?.let { FormBanner(it) }
                if (person.isPlaceholder) {
                    PrimaryButton("Share invite code", onClick = onShareInvite)
                }
                if (person.id != trip.ownerId) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(TColor.red.copy(alpha = 0.14f))
                            .plainClick {
                                if (busy) return@plainClick
                                busy = true
                                scope.launch {
                                    runCatching { c.trips.removeTraveler(trip.id, person.id, force = false) }
                                        .onSuccess { onDismiss() }
                                        .onFailure { e ->
                                            if (e is ApiException && e.code == 409) {
                                                conflictMessage = e.message ?: "${person.name} still has unsettled expenses on this trip."
                                            } else {
                                                error = e.message ?: "Couldn't remove ${person.name}."
                                            }
                                        }
                                    busy = false
                                }
                            }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text("Remove from trip", style = TType.calloutStrong, color = TColor.red)
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
    )
}

/** Pick another trip to merge this trip into (`POST /trips/{target_id}/merge`), then confirm. */
@Composable
fun MergeTripDialog(
    source: ApiTrip,
    candidates: List<ApiTrip>,
    onDismiss: () -> Unit,
    onMergedInto: (Int) -> Unit,
) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<ApiTrip?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val chosen = target
    if (chosen != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) target = null },
            containerColor = TColor.surfaceRaised,
            title = { Text("Merge into ${chosen.displayTitle}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Flights, stays, rental cars, drives, photos, expenses, and travelers from ${source.displayTitle} will move into ${chosen.displayTitle} (duplicates skipped). ${source.displayTitle} will be moved to Recently Deleted.",
                        style = TType.footnote,
                        color = TColor.textSecondary,
                    )
                    error?.let { FormBanner(it) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            runCatching { c.trips.mergeTrip(source.id, chosen.id) }
                                .onSuccess {
                                    busy = false
                                    onMergedInto(chosen.id)
                                }
                                .onFailure {
                                    busy = false
                                    error = it.message ?: "Couldn't merge trips."
                                }
                        }
                    },
                ) { Text(if (busy) "Merging…" else "Merge", color = TColor.coral) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { target = null }) { Text("Back") }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TColor.surfaceRaised,
        title = { Text("Merge ${source.displayTitle} into…") },
        text = {
            if (candidates.isEmpty()) {
                Text("No other trips to merge into.", style = TType.footnote, color = TColor.textSecondary)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Combine two copies of the same trip. Flights, stays, expenses, and travelers from ${source.displayTitle} move into the trip you pick.",
                        style = TType.footnote,
                        color = TColor.textSecondary,
                    )
                    candidates.take(8).forEach { t ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(TColor.surface)
                                .plainClick { target = t }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(t.displayTitle, style = TType.body, color = TColor.textPrimary)
                                val dates = listOfNotNull(t.startDate, t.endDate).joinToString(" → ")
                                if (dates.isNotEmpty()) {
                                    Text(dates, style = TType.caption, color = TColor.textSecondary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cancel") } },
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
