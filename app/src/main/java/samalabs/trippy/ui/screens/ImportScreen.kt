package samalabs.trippy.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import samalabs.trippy.data.ImportCommitResult
import samalabs.trippy.data.ImportItem
import samalabs.trippy.data.ImportPreview
import samalabs.trippy.data.ImportRepository
import samalabs.trippy.domain.CsvKind
import samalabs.trippy.domain.ImportSource
import samalabs.trippy.domain.Importing
import samalabs.trippy.domain.MAX_IMPORT_BYTES
import samalabs.trippy.domain.PickedFile
import samalabs.trippy.ui.LocalContainer
import samalabs.trippy.ui.components.AuthField
import samalabs.trippy.ui.components.FormBanner
import samalabs.trippy.ui.components.PrimaryButton
import samalabs.trippy.ui.components.SectionLabel
import samalabs.trippy.ui.components.TextAction
import samalabs.trippy.ui.components.trippyCard
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType

private enum class ImportStage { Pick, Input, Mapping, Preview, Done }

private fun ImportSource.tint(): Color = when (this) {
    ImportSource.Ics -> TColor.red
    ImportSource.IcsUrl -> TColor.teal
    ImportSource.Flighty -> TColor.blue
    ImportSource.Pkpass -> TColor.green
    ImportSource.Email -> TColor.orange
    ImportSource.Csv -> TColor.purple
}

private fun ImportSource.icon(): ImageVector = when (this) {
    ImportSource.Ics -> Icons.Filled.CalendarMonth
    ImportSource.IcsUrl -> Icons.Filled.Link
    ImportSource.Flighty -> Icons.Filled.FlightTakeoff
    ImportSource.Pkpass -> Icons.Filled.Badge
    ImportSource.Email -> Icons.Filled.Email
    ImportSource.Csv -> Icons.Filled.TableChart
}

private fun kindIcon(kind: String): ImageVector = when (kind) {
    "rental" -> Icons.Filled.DirectionsCar
    "lodging" -> Icons.Filled.Hotel
    else -> Icons.Filled.Flight
}

private fun kindTint(kind: String): Color = when (kind) {
    "rental" -> TColor.green
    "lodging" -> TColor.coral
    else -> TColor.route
}

/** Reads a SAF document, refusing anything over the server's 5 MB limit. */
private fun readPicked(ctx: Context, uri: Uri): PickedFile {
    val resolver = ctx.contentResolver
    var name = "import"
    var size = -1L
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cur ->
        if (cur.moveToFirst()) {
            cur.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { i -> cur.getString(i)?.let { name = it } }
            cur.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cur.isNull(it) }?.let { size = cur.getLong(it) }
        }
    }
    if (size > MAX_IMPORT_BYTES) throw IllegalArgumentException("That file is over 5 MB.")
    val bytes = resolver.openInputStream(uri)?.use { input ->
        val out = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            out.write(chunk, 0, n)
            if (out.size() > MAX_IMPORT_BYTES) throw IllegalArgumentException("That file is over 5 MB.")
        }
        out.toByteArray()
    } ?: throw IllegalArgumentException("Couldn't open that file.")
    return PickedFile(name, bytes)
}

@Composable
fun ImportScreen(tripId: Int?, onBack: () -> Unit) {
    val c = LocalContainer.current
    val ctx = LocalContext.current
    val repo = remember { ImportRepository(c.api) }
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(ImportStage.Pick) }
    var source by remember { mutableStateOf(ImportSource.Ics) }
    var text by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var file by remember { mutableStateOf<PickedFile?>(null) }
    var csvKind by remember { mutableStateOf(CsvKind.Flight) }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var mapped by remember { mutableStateOf(false) }
    val mapping = remember { mutableStateMapOf<String, String>() }
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    var result by remember { mutableStateOf<ImportCommitResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun runPreview(withMapping: Boolean) {
        val req = Importing.previewRequest(source, text, url, file, csvKind, if (withMapping) mapping.toMap() else null) ?: return
        scope.launch {
            busy = true
            error = null
            try {
                val p = repo.preview(req)
                preview = p
                if (p.needsMapping) {
                    mapping.clear()
                    p.suggestedMapping.forEach { (k, v) -> if (!v.isNullOrBlank()) mapping[k] = v }
                    stage = ImportStage.Mapping
                } else {
                    mapped = withMapping
                    selected = Importing.initialSelection(p.items)
                    stage = ImportStage.Preview
                }
            } catch (e: Exception) {
                error = e.message ?: "Couldn't read that"
            }
            busy = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                file = withContext(Dispatchers.IO) { readPicked(ctx, uri) }
                error = null
                runPreview(withMapping = false)
            } catch (e: Exception) {
                error = e.message ?: "Couldn't open that file"
            }
        }
    }

    fun stepBack() {
        error = null
        stage = when (stage) {
            ImportStage.Pick, ImportStage.Done -> { onBack(); return }
            ImportStage.Input -> ImportStage.Pick
            ImportStage.Mapping -> ImportStage.Input
            ImportStage.Preview -> if (mapped) ImportStage.Mapping else ImportStage.Input
        }
    }
    BackHandler(enabled = stage != ImportStage.Pick) { stepBack() }

    Column(Modifier.fillMaxSize().background(TColor.canvas).safeDrawingPadding().imePadding()) {
        BackBar(if (tripId == null) "Import" else "Import into trip", { stepBack() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            error?.let { FormBanner(it) }
            when (stage) {
                ImportStage.Pick -> {
                    Text("Bring bookings in", style = TType.title, color = TColor.textPrimary)
                    Text("Trippy reads the file, shows what it found and skips anything you already have.", style = TType.callout, color = TColor.textSecondary)
                    Column(Modifier.trippyCard()) {
                        ImportSource.entries.forEachIndexed { i, s ->
                            if (i > 0) HorizontalDivider(color = TColor.divider, modifier = Modifier.padding(start = 64.dp))
                            SourceRow(s) {
                                source = s; file = null; text = ""; url = ""; preview = null; mapped = false
                                stage = ImportStage.Input
                            }
                        }
                    }
                }
                ImportStage.Input -> InputStage(
                    source = source, text = text, onText = { text = it }, url = url, onUrl = { url = it },
                    file = file, csvKind = csvKind, onCsvKind = { csvKind = it }, busy = busy,
                    onPick = { picker.launch(arrayOf("*/*")) },
                    onPreview = { runPreview(withMapping = false) },
                )
                ImportStage.Mapping -> preview?.let { p ->
                    MappingStage(p, csvKind, mapping, busy,
                        onKind = { csvKind = it; runPreview(withMapping = false) },
                        onSet = { target, col -> if (col == null) mapping.remove(target) else mapping[target] = col },
                        onContinue = { runPreview(withMapping = true) },
                    )
                }
                ImportStage.Preview -> preview?.let { p ->
                    PreviewStage(p.items, selected, busy,
                        onToggle = { i -> selected = if (i in selected) selected - i else selected + i },
                        onAll = { all -> selected = if (all) p.items.indices.toSet() else emptySet() },
                        onCommit = {
                            scope.launch {
                                busy = true
                                error = null
                                try {
                                    result = repo.commit(Importing.commitRequest(p.items, selected, tripId))
                                    stage = ImportStage.Done
                                    c.trips.refresh()
                                } catch (e: Exception) {
                                    error = e.message ?: "Import failed"
                                }
                                busy = false
                            }
                        },
                    )
                }
                ImportStage.Done -> {
                    val r = result
                    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.CheckCircle, null, tint = TColor.green, modifier = Modifier.size(56.dp))
                        Text("Imported ${r?.created?.size ?: 0}", style = TType.title, color = TColor.textPrimary)
                        val skipped = r?.skipped?.size ?: 0
                        if (skipped > 0) Text("Skipped $skipped already in Trippy", style = TType.callout, color = TColor.textSecondary)
                        Text("New bookings land on the trip whose dates cover them.", style = TType.footnote, color = TColor.textTertiary)
                    }
                    PrimaryButton("Done", onClick = onBack, color = TColor.coral)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextAction("Import something else", onClick = { stage = ImportStage.Pick; result = null }, color = TColor.coral)
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(s: ImportSource, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(s.tint().copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(s.icon(), null, tint = s.tint(), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, style = TType.calloutStrong, color = TColor.textPrimary)
            Text(s.detail, style = TType.footnote, color = TColor.textSecondary)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TColor.textTertiary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun InputStage(
    source: ImportSource, text: String, onText: (String) -> Unit, url: String, onUrl: (String) -> Unit,
    file: PickedFile?, csvKind: CsvKind, onCsvKind: (CsvKind) -> Unit, busy: Boolean,
    onPick: () -> Unit, onPreview: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(source.icon(), null, tint = source.tint(), modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(source.title, style = TType.title2, color = TColor.textPrimary)
    }
    Text(source.detail, style = TType.callout, color = TColor.textSecondary)
    if (source == ImportSource.Csv) {
        SectionLabel("The rows are")
        MoneySegmented(CsvKind.entries.map { it.label }, csvKind.ordinal) { onCsvKind(CsvKind.entries[it]) }
    }
    if (source.url) {
        AuthField("Feed link", url, onUrl, placeholder = "webcal://…", keyboardType = KeyboardType.Uri, mono = true)
        PrimaryButton("Preview", onClick = onPreview, busy = busy, enabled = url.isNotBlank(), color = TColor.coral)
    }
    if (source.file) {
        PrimaryButton(if (file == null) "Choose file" else "Choose another file", onClick = onPick, busy = busy && file != null, color = TColor.coral)
        file?.let { Text("${it.name} · ${it.bytes.size / 1024 + 1} KB", style = TType.footnote, color = TColor.textTertiary) }
    }
    if (source.paste) {
        SectionLabel(if (source == ImportSource.Email) "Or paste the email" else "Or paste the calendar text")
        Box(
            Modifier.fillMaxWidth().heightIn(min = 140.dp).clip(RoundedCornerShape(14.dp)).background(TColor.surface)
                .border(1.dp, TColor.hairline, RoundedCornerShape(14.dp)).padding(14.dp),
        ) {
            if (text.isEmpty()) Text(if (source == ImportSource.Email) "Paste the confirmation here" else "BEGIN:VCALENDAR…", style = TType.callout, color = TColor.textTertiary)
            BasicTextField(text, onText, textStyle = TType.callout.copy(color = TColor.textPrimary), cursorBrush = SolidColor(TColor.coral), modifier = Modifier.fillMaxWidth())
        }
        PrimaryButton("Preview pasted text", onClick = onPreview, busy = busy && file == null, enabled = text.isNotBlank(), color = TColor.surfaceRaised)
    }
}

@Composable
private fun MappingStage(
    p: ImportPreview, csvKind: CsvKind, mapping: Map<String, String>, busy: Boolean,
    onKind: (CsvKind) -> Unit, onSet: (String, String?) -> Unit, onContinue: () -> Unit,
) {
    val targets = p.targets[csvKind.id]
    Text("Match the columns", style = TType.title2, color = TColor.textPrimary)
    Text("${p.rowCount ?: 0} rows, ${p.columns.size} columns. Pick which column holds each detail.", style = TType.callout, color = TColor.textSecondary)
    MoneySegmented(CsvKind.entries.map { it.label }, csvKind.ordinal) { onKind(CsvKind.entries[it]) }
    if (targets == null) return
    Column(Modifier.trippyCard()) {
        targets.all.forEachIndexed { i, t ->
            if (i > 0) HorizontalDivider(color = TColor.divider)
            MappingRow(t, t in targets.required, mapping[t], p.columns, mapping[t]?.let { p.sample(it) }) { onSet(t, it) }
        }
    }
    val missing = Importing.missingRequired(targets.required, mapping)
    if (missing.isNotEmpty()) Text("Still needed: " + missing.joinToString { Importing.fieldLabel(it) }, style = TType.footnoteStrong, color = TColor.amber)
    PrimaryButton("Preview rows", onClick = onContinue, busy = busy, enabled = missing.isEmpty(), color = TColor.coral)
}

@Composable
private fun MappingRow(target: String, required: Boolean, value: String?, columns: List<String>, sample: String?, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(Importing.fieldLabel(target) + if (required) " *" else "", style = TType.callout, color = TColor.textPrimary)
            sample?.let { Text("e.g. $it", style = TType.footnote, color = TColor.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Box {
            Text(value ?: "Not used", style = TType.calloutStrong, color = if (value == null) TColor.textTertiary else TColor.coral, maxLines = 1)
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = TColor.surfaceRaised) {
                DropdownMenuItem(text = { Text("Not used", color = TColor.textSecondary) }, onClick = { open = false; onPick(null) })
                columns.forEach { col -> DropdownMenuItem(text = { Text(col, color = TColor.textPrimary) }, onClick = { open = false; onPick(col) }) }
            }
        }
    }
}

@Composable
private fun PreviewStage(items: List<ImportItem>, selected: Set<Int>, busy: Boolean, onToggle: (Int) -> Unit, onAll: (Boolean) -> Unit, onCommit: () -> Unit) {
    val fresh = items.count { it.isNew }
    Text(if (items.isEmpty()) "Nothing found" else "Found ${items.size}", style = TType.title2, color = TColor.textPrimary)
    Text(
        if (items.isEmpty()) "Trippy couldn't find flights, stays or rentals in that. Try another source." else "$fresh new · ${items.size - fresh} already in Trippy",
        style = TType.callout, color = TColor.textSecondary,
    )
    if (items.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextAction(if (selected.size == items.size) "Select none" else "Select all", onClick = { onAll(selected.size != items.size) }, color = TColor.coral)
    }
    Column(Modifier.trippyCard()) {
        items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(color = TColor.divider, modifier = Modifier.padding(start = 56.dp))
            val on = i in selected
            Row(Modifier.fillMaxWidth().clickable { onToggle(i) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.padding(top = 2.dp).size(24.dp).clip(CircleShape).background(if (on) TColor.coral else Color.Transparent)
                        .border(1.5.dp, if (on) TColor.coral else TColor.textTertiary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp)) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(kindIcon(item.kind), null, tint = kindTint(item.kind), modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(item.summary.ifBlank { item.kind }, style = TType.callout, color = TColor.textPrimary, modifier = Modifier.weight(1f))
                    }
                    Importing.duplicateNote(item)?.let { Text(it, style = TType.footnoteStrong, color = TColor.textTertiary) }
                    if (item.confidence == "low") Text("Low confidence: check it after import", style = TType.footnote, color = TColor.amber)
                    item.warnings.forEach { w ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, null, tint = TColor.amber, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(w, style = TType.footnote, color = TColor.amber)
                        }
                    }
                }
            }
        }
    }
    PrimaryButton(if (selected.isEmpty()) "Pick something to import" else "Import ${selected.size}", onClick = onCommit, busy = busy, enabled = selected.isNotEmpty(), color = TColor.coral)
}

/** Account-screen entry rows for the importer (and later the year card). */
@Composable
fun AccountExtras(open: (Route) -> Unit) {
    Column(Modifier.trippyCard()) {
        AccountLink("Import bookings", Icons.Filled.FileOpen, TColor.blue) { open(ImportRoute(null)) }
    }
}

@Composable
internal fun AccountLink(title: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, style = TType.body, color = TColor.textPrimary, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TColor.textTertiary, modifier = Modifier.size(16.dp))
    }
}
