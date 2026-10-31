package samalabs.trippy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import samalabs.trippy.data.ApiExpense
import samalabs.trippy.data.ApiTrip
import samalabs.trippy.data.ExpenseBody
import samalabs.trippy.data.LedgerDebt
import samalabs.trippy.data.LedgerSettlement
import samalabs.trippy.data.MoneyRepository
import samalabs.trippy.data.SplitValueBody
import samalabs.trippy.data.TripLedger
import samalabs.trippy.domain.Cents
import samalabs.trippy.domain.Fmt
import samalabs.trippy.domain.LedgerMode
import samalabs.trippy.domain.MoneyPerson
import samalabs.trippy.domain.SplitDraft
import samalabs.trippy.domain.SplitMode
import samalabs.trippy.domain.cents
import samalabs.trippy.domain.displayTitle
import samalabs.trippy.domain.initialSplitValues
import samalabs.trippy.domain.moneyPeople
import samalabs.trippy.domain.splitMemberIds
import samalabs.trippy.ui.LocalContainer
import samalabs.trippy.ui.components.AuthField
import samalabs.trippy.ui.components.FormBanner
import samalabs.trippy.ui.components.PrimaryButton
import samalabs.trippy.ui.components.SectionLabel
import samalabs.trippy.ui.components.trippyCard
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType

private sealed interface ExpenseTarget {
    data object New : ExpenseTarget
    data class Edit(val expense: ApiExpense) : ExpenseTarget
}

private fun problem(e: Exception): String = e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong"

@Composable
fun ExpensesScreen(tripId: Int, onBack: () -> Unit) {
    val c = LocalContainer.current
    val repo = remember { MoneyRepository(c.api) }
    val session by c.sessions.session.collectAsState()
    val me = session?.profile
    var trip by remember { mutableStateOf<ApiTrip?>(c.trips.trip(tripId)) }
    var ledger by remember { mutableStateOf<TripLedger?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var mode by remember { mutableStateOf(LedgerMode.Simplified) }
    var target by remember { mutableStateOf<ExpenseTarget?>(null) }
    var settleAsk by remember { mutableStateOf<LedgerDebt?>(null) }
    var settleAllAsk by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        try {
            coroutineScope {
                val t = async { repo.trip(tripId) }
                val l = async { repo.ledger(tripId) }
                trip = t.await()
                ledger = l.await()
            }
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = problem(e)
        }
        loading = false
    }

    fun mutate(block: suspend () -> Unit) {
        scope.launch {
            try { block() } catch (e: Exception) { error = problem(e); return@launch }
            reload()
            c.trips.refresh()
        }
    }

    LaunchedEffect(tripId) { reload() }

    val people = moneyPeople(trip, ledger, me)
    val name: (Int) -> String = { id -> people.firstOrNull { it.id == id }?.name ?: "User $id" }
    val first: (Int) -> String = { id -> people.firstOrNull { it.id == id }?.first ?: "User $id" }
    val expenses = trip?.expenses.orEmpty().sortedByDescending { it.date ?: "" }
    val currency = expenses.firstOrNull()?.currency ?: "USD"
    val total = ledger?.totalCents ?: expenses.sumOf { it.cents }

    Column(Modifier.fillMaxSize().background(TColor.canvas).safeDrawingPadding()) {
        BackBar("Expenses", onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(Modifier.trippyCard().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("TOTAL SPENT", style = TType.label, color = TColor.textSecondary)
                        Text(Cents.format(total, currency), style = TType.heroSmall, color = TColor.textPrimary, maxLines = 1)
                        Text(trip?.displayTitle ?: "", style = TType.footnote, color = TColor.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    MoneyCapsule("Split", Icons.Filled.Add, TColor.coral) { target = ExpenseTarget.New }
                }
            }
            error?.let { msg -> item { FormBanner(msg) } }
            item { SectionLabel("Expenses", trailing = if (expenses.isEmpty()) null else "${expenses.size}") }
            item {
                Column(Modifier.trippyCard()) {
                    if (expenses.isEmpty()) {
                        Text(
                            if (loading) "Loading…" else "No expenses yet. Tap Split to add what someone paid.",
                            style = TType.callout, color = TColor.textSecondary, modifier = Modifier.padding(18.dp),
                        )
                    }
                    expenses.forEachIndexed { i, e ->
                        if (i > 0) HorizontalDivider(color = TColor.divider, modifier = Modifier.padding(start = 62.dp))
                        ExpenseLine(e, name, first) { target = ExpenseTarget.Edit(e) }
                    }
                }
            }
            item { SectionLabel("Balances") }
            item {
                BalancesPanel(
                    ledger = ledger, loading = loading, mode = mode, onMode = { mode = it }, currency = currency,
                    name = name, first = first,
                    onSettle = { settleAsk = it }, onSettleAll = { settleAllAsk = true },
                    onUndo = { s -> mutate { repo.undoSettlement(s.id) } },
                )
            }
        }
    }

    target?.let { t ->
        val editing = (t as? ExpenseTarget.Edit)?.expense
        val members = splitMemberIds(trip).map { id -> MoneyPerson(id, name(id)) }
        ExpenseEditorSheet(
            editing = editing, members = members, meId = me?.id, currency = editing?.currency ?: currency,
            onDismiss = { target = null },
            onSave = { body ->
                repo.saveExpense(tripId, editing?.id, body)
                target = null
                // Closing the sheet cancels its scope, so refresh on the screen's scope.
                scope.launch { reload(); c.trips.refresh() }
            },
            onDelete = {
                editing?.let { repo.deleteExpense(it.id) }
                target = null
                // Closing the sheet cancels its scope, so refresh on the screen's scope.
                scope.launch { reload(); c.trips.refresh() }
            },
        )
    }

    settleAsk?.let { d ->
        AlertDialog(
            onDismissRequest = { settleAsk = null },
            containerColor = TColor.surfaceRaised,
            title = { Text("Record payment?", style = TType.title3, color = TColor.textPrimary) },
            text = { Text("${name(d.fromUserId)} paid ${name(d.toUserId)} ${Cents.format(d.amountCents, currency)}.", style = TType.body, color = TColor.textSecondary) },
            confirmButton = { TextButton(onClick = { settleAsk = null; mutate { repo.settle(tripId, d, mode.raw) } }) { Text("Record", color = TColor.green) } },
            dismissButton = { TextButton(onClick = { settleAsk = null }) { Text("Cancel", color = TColor.textSecondary) } },
        )
    }
    if (settleAllAsk) {
        AlertDialog(
            onDismissRequest = { settleAllAsk = false },
            containerColor = TColor.surfaceRaised,
            title = { Text("Settle everything?", style = TType.title3, color = TColor.textPrimary) },
            text = { Text("Records a payment for every open balance on this trip.", style = TType.body, color = TColor.textSecondary) },
            confirmButton = { TextButton(onClick = { settleAllAsk = false; mutate { repo.settleAll(tripId) } }) { Text("Settle all", color = TColor.green) } },
            dismissButton = { TextButton(onClick = { settleAllAsk = false }) { Text("Cancel", color = TColor.textSecondary) } },
        )
    }
}

@Composable
private fun ExpenseLine(e: ApiExpense, name: (Int) -> String, first: (Int) -> String, onClick: () -> Unit) {
    val payer = e.payerId
    val n = e.splits.size
    val sub = buildString {
        append(if (payer != null) "${first(payer)} paid" else "Paid")
        when {
            n > 1 -> append(" · split $n ways")
            n == 1 -> e.splits.first().userId?.takeIf { it != payer }?.let { append(" · for ${first(it)}") }
        }
        Fmt.parse(e.date)?.let { append(" · ").append(Fmt.monthDay(it)) }
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoneyAvatar(payer?.let(name) ?: "?", payer ?: 0, 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.description?.ifBlank { null } ?: "Expense", style = TType.body, color = TColor.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = TType.footnote, color = TColor.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(Cents.format(e.cents, e.currency), style = TType.calloutStrong, color = TColor.textPrimary)
    }
}

@Composable
private fun BalancesPanel(
    ledger: TripLedger?, loading: Boolean, mode: LedgerMode, onMode: (LedgerMode) -> Unit, currency: String,
    name: (Int) -> String, first: (Int) -> String,
    onSettle: (LedgerDebt) -> Unit, onSettleAll: () -> Unit, onUndo: (LedgerSettlement) -> Unit,
) {
    Column(Modifier.trippyCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (ledger == null) {
            if (loading) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = TColor.textSecondary, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Working out balances…", style = TType.callout, color = TColor.textSecondary)
            } else Text("Balances are unavailable right now.", style = TType.callout, color = TColor.textSecondary)
            return@Column
        }
        if (ledger.totalCents == 0L && ledger.settlements.isEmpty()) {
            Text("Nothing to split yet", style = TType.callout, color = TColor.textSecondary)
            return@Column
        }
        ledger.people.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoneyAvatar(name(p.userId), p.userId, 28.dp)
                Spacer(Modifier.width(10.dp))
                Text(name(p.userId), style = TType.callout, color = TColor.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                when {
                    p.netCents > 0 -> Text("gets back ${Cents.format(p.netCents, currency)}", style = TType.calloutStrong, color = TColor.green)
                    p.netCents < 0 -> Text("owes ${Cents.format(-p.netCents, currency)}", style = TType.calloutStrong, color = TColor.red)
                    else -> Text("settled", style = TType.callout, color = TColor.textTertiary)
                }
            }
        }
        HorizontalDivider(color = TColor.divider)
        MoneySegmented(
            listOf("${LedgerMode.Simplified.label} ${ledger.simplified.size}", "${LedgerMode.Pairwise.label} ${ledger.pairwise.size}"),
            selected = mode.ordinal,
        ) { onMode(LedgerMode.entries[it]) }
        val debts = if (mode == LedgerMode.Simplified) ledger.simplified else ledger.pairwise
        if (debts.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = TColor.green, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("All settled up", style = TType.calloutStrong, color = TColor.textPrimary)
            }
        }
        debts.forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${first(d.fromUserId)} → ${first(d.toUserId)}", style = TType.callout, color = TColor.textPrimary, maxLines = 1)
                    Text(Cents.format(d.amountCents, currency), style = TType.calloutStrong, color = TColor.textPrimary)
                }
                MoneyCapsule("Settle", Icons.Filled.Check, TColor.green, small = true) { onSettle(d) }
            }
        }
        if (debts.size > 1) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                MoneyCapsule("Settle all", null, TColor.surfaceHigh) { onSettleAll() }
            }
        }
        if (ledger.settlements.isNotEmpty()) {
            HorizontalDivider(color = TColor.divider)
            Text("PAYMENTS RECORDED", style = TType.label, color = TColor.textSecondary)
            ledger.settlements.sortedByDescending { it.createdAt ?: "" }.take(4).forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${first(s.fromUserId)} paid ${first(s.toUserId)} ${Cents.format(s.amountCents, currency)}",
                        style = TType.footnote, color = TColor.textSecondary, modifier = Modifier.weight(1f), maxLines = 1,
                    )
                    Text("Undo", style = TType.footnoteStrong, color = TColor.coral, modifier = Modifier.clickable { onUndo(s) }.padding(6.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseEditorSheet(
    editing: ApiExpense?, members: List<MoneyPerson>, meId: Int?, currency: String,
    onDismiss: () -> Unit, onSave: suspend (ExpenseBody) -> Unit, onDelete: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var amountText by remember { mutableStateOf(editing?.let { Cents.plain(it.cents) } ?: "") }
    var desc by remember { mutableStateOf(editing?.description ?: "") }
    var payer by remember { mutableStateOf(editing?.payerId ?: meId?.takeIf { id -> members.any { it.id == id } } ?: members.firstOrNull()?.id) }
    var mode by remember { mutableStateOf(SplitMode.from(editing?.splitMode)) }
    val included = remember {
        mutableStateListOf<Int>().apply { addAll(editing?.splits?.mapNotNull { it.userId }?.takeIf { it.isNotEmpty() } ?: members.map { it.id }) }
    }
    val values = remember { mutableStateMapOf<Int, String>().apply { editing?.let { putAll(it.initialSplitValues()) } } }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val draft = SplitDraft(
        totalCents = Cents.parse(amountText)?.takeIf { it > 0 },
        ordered = SplitDraft.payerFirst(members.map { it.id }.filter { it in included }, payer),
        mode = mode,
        values = values.toMap(),
    )
    val status = draft.status
    val preview = draft.preview
    val canSave = status?.second == true && desc.isNotBlank() && payer != null && !busy

    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            try { block() } catch (e: Exception) { error = problem(e) }
            busy = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TColor.surfaceSheet,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(if (editing == null) "New expense" else "Edit expense", style = TType.title2, color = TColor.textPrimary)
            AuthField("Amount", amountText, { amountText = it }, placeholder = "0.00", keyboardType = KeyboardType.Decimal)
            AuthField("What for", desc, { desc = it }, placeholder = "Dinner, gas, tickets…", capitalization = KeyboardCapitalization.Sentences)

            Text("PAID BY", style = TType.label, color = TColor.textSecondary)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                members.forEach { p ->
                    val on = p.id == payer
                    Text(
                        p.first, style = TType.footnoteStrong, color = if (on) Color.White else TColor.textPrimary,
                        modifier = Modifier.clip(CircleShape).background(if (on) TColor.coral else TColor.surfaceRaised)
                            .clickable { payer = p.id }.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            Text("SPLIT", style = TType.label, color = TColor.textSecondary)
            MoneySegmented(SplitMode.entries.map { it.label }, mode.ordinal) { mode = SplitMode.entries[it] }

            Column(Modifier.clip(RoundedCornerShape(16.dp)).background(TColor.surface)) {
                members.forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider(color = TColor.divider, modifier = Modifier.padding(start = 52.dp))
                    val on = p.id in included
                    Row(
                        Modifier.fillMaxWidth().clickable { if (on) included.remove(p.id) else included.add(p.id) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(24.dp).clip(CircleShape).background(if (on) TColor.coral else Color.Transparent)
                                .border(1.5.dp, if (on) TColor.coral else TColor.textTertiary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Text(p.name, style = TType.callout, color = if (on) TColor.textPrimary else TColor.textTertiary, modifier = Modifier.weight(1f), maxLines = 1)
                        if (on && mode != SplitMode.Equal) {
                            SplitValueField(values[p.id].orEmpty(), { values[p.id] = it }, mode)
                            Spacer(Modifier.width(10.dp))
                        }
                        if (on) Text(preview[p.id]?.let { Cents.format(it, currency) } ?: "—", style = TType.footnoteStrong, color = TColor.textSecondary, modifier = Modifier.width(72.dp), textAlign = TextAlign.End)
                    }
                }
            }
            status?.let { (msg, ok) -> Text(msg, style = TType.footnoteStrong, color = if (ok) TColor.green else TColor.amber) }
            error?.let { FormBanner(it) }
            PrimaryButton(
                if (editing == null) "Add expense" else "Save changes", busy = busy, enabled = canSave, color = TColor.coral,
                onClick = {
                    val cents = draft.totalCents ?: return@PrimaryButton
                    val who = payer ?: return@PrimaryButton
                    run {
                        onSave(
                            ExpenseBody(
                                amount = cents / 100.0, description = desc.trim(), payerId = who, splitMode = mode.raw,
                                splitAmong = draft.ordered, splits = draft.splitValues()?.map { SplitValueBody(it.first, it.second) },
                                currency = currency,
                            ),
                        )
                    }
                },
            )
            if (editing != null) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete expense", color = TColor.red) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = TColor.surfaceRaised,
            title = { Text("Delete this expense?", style = TType.title3, color = TColor.textPrimary) },
            text = { Text("Balances will be recalculated without it.", style = TType.body, color = TColor.textSecondary) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; run { onDelete() } }) { Text("Delete", color = TColor.red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = TColor.textSecondary) } },
        )
    }
}

@Composable
private fun SplitValueField(value: String, onChange: (String) -> Unit, mode: SplitMode) {
    Row(
        Modifier.width(84.dp).clip(RoundedCornerShape(10.dp)).background(TColor.surfaceRaised).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mode == SplitMode.Exact) Text("$", style = TType.footnote, color = TColor.textTertiary)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TType.callout.copy(color = TColor.textPrimary, textAlign = TextAlign.End),
            cursorBrush = SolidColor(TColor.coral),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        when (mode) {
            SplitMode.Percent -> Text("%", style = TType.footnote, color = TColor.textTertiary)
            SplitMode.Shares -> Text("×", style = TType.footnote, color = TColor.textTertiary)
            else -> Unit
        }
    }
}

private val avatarPalette = listOf(TColor.coral, TColor.blue, TColor.green, TColor.purple, TColor.teal, TColor.amber, TColor.orange)

@Composable
fun MoneyAvatar(name: String, id: Int, size: Dp = 32.dp) {
    val color = avatarPalette[Math.floorMod(id, avatarPalette.size)]
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
        Text(MoneyPerson(id, name).initials, style = TType.footnoteStrong, color = color)
    }
}

@Composable
internal fun MoneyCapsule(text: String, icon: ImageVector?, color: Color, small: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(color).clickable(onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 16.dp, vertical = if (small) 7.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(if (small) 14.dp else 17.dp))
        Text(text, style = if (small) TType.footnoteStrong else TType.calloutStrong, color = Color.White)
    }
}

@Composable
internal fun MoneySegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TColor.surfaceRaised).padding(3.dp)) {
        options.forEachIndexed { i, s ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (on) TColor.surfaceHigh else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(s, style = TType.footnoteStrong, color = if (on) TColor.textPrimary else TColor.textSecondary, maxLines = 1) }
        }
    }
}
