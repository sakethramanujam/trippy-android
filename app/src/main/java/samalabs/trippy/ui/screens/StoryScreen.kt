package samalabs.trippy.ui.screens

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import samalabs.trippy.data.StoryEditBody
import samalabs.trippy.data.StoryItem
import samalabs.trippy.data.StoryItemEdit
import samalabs.trippy.data.StoryRepository
import samalabs.trippy.data.TripStory
import samalabs.trippy.domain.StoryText
import samalabs.trippy.domain.StoryTheme
import samalabs.trippy.ui.LocalContainer
import samalabs.trippy.ui.components.AuthField
import samalabs.trippy.ui.components.FormBanner
import samalabs.trippy.ui.components.PrimaryButton
import samalabs.trippy.ui.components.SectionLabel
import samalabs.trippy.ui.components.TextAction
import samalabs.trippy.ui.components.trippyCard
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType

internal fun storyKindTint(kind: String): Color = when (kind) {
    "flight" -> TColor.route
    "rental" -> TColor.green
    "lodging" -> TColor.coral
    "drive" -> TColor.teal
    "expenses" -> TColor.teal
    "travelers" -> TColor.purple
    else -> TColor.textSecondary
}

internal fun StoryTheme.brush(): Brush = Brush.verticalGradient(palette.colors.map { Color(it) })

/** Trip story: a themed recap, the timeline of what happened, a slide player, and light editing. */
@Composable
fun StoryScreen(tripId: Int, onBack: () -> Unit, onShare: () -> Unit) {
    val c = LocalContainer.current
    val repo = remember { StoryRepository(c.api) }
    val scope = rememberCoroutineScope()
    var story by remember { mutableStateOf<TripStory?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var noteFor by remember { mutableStateOf<StoryItem?>(null) }
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var theme by remember { mutableStateOf(StoryTheme.Indigo) }
    val order = remember { mutableStateListOf<String>() }
    val hidden = remember { mutableStateMapOf<String, Boolean>() }
    val notes = remember { mutableStateMapOf<String, String>() }

    fun adopt(s: TripStory) {
        story = s
        title = s.title.orEmpty(); subtitle = s.subtitle.orEmpty(); theme = StoryTheme.from(s.theme)
        order.clear(); order.addAll(s.items.map { it.key })
        hidden.clear(); s.items.filter { it.hidden }.forEach { hidden[it.key] = true }
        notes.clear(); s.items.forEach { i -> i.note?.takeIf { it.isNotBlank() }?.let { notes[i.key] = it } }
    }

    fun run(block: suspend () -> TripStory) {
        busy = true; error = null
        scope.launch {
            try { adopt(block()); editing = false } catch (e: Exception) { error = e.message ?: "Couldn't update the story." }
            busy = false
        }
    }

    LaunchedEffect(tripId) {
        try { adopt(repo.story(tripId)) } catch (e: Exception) { error = e.message ?: "Couldn't load the story." }
    }

    BackHandler(enabled = editing && !playing) { story?.let { adopt(it) }; editing = false }

    val s = story
    Column(Modifier.fillMaxSize().background(TColor.canvas).safeDrawingPadding()) {
        BackBar("Story", onBack) {
            if (s != null) {
                if (editing) {
                    TextAction(if (busy) "Saving…" else "Save", onClick = {
                        if (busy) return@TextAction
                        val body = StoryEditBody(
                            title = title.trim(), subtitle = subtitle.trim(), theme = theme.id, order = order.toList(),
                            items = s.items.associate { it.key to StoryItemEdit(hidden[it.key] == true, notes[it.key]?.trim()?.ifBlank { null }, it.photos) },
                        )
                        run { repo.save(tripId, body) }
                    })
                } else {
                    TextAction("Edit", onClick = { editing = true })
                    Spacer(Modifier.width(4.dp))
                    RoundButton(onClick = onShare) { Icon(Icons.Filled.IosShare, "Share card", tint = TColor.textPrimary, modifier = Modifier.size(18.dp)) }
                }
            }
        }
        error?.let { FormBanner(it, Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
        if (s == null) {
            if (error == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = TColor.coral) }
            return@Column
        }
        val byKey = s.items.associateBy { it.key }
        val ordered = order.mapNotNull { byKey[it] }
        val visible = ordered.filter { hidden[it.key] != true }
        val shownTitle = title.ifBlank { s.generatedTitle.ifBlank { "Trip" } }
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { StoryHero(s, shownTitle, subtitle, theme) { playing = true } }
            if (editing) item {
                Column(Modifier.trippyCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AuthField("Title", title, { title = it }, placeholder = s.generatedTitle)
                    AuthField("Subtitle", subtitle, { subtitle = it }, placeholder = "A line under the title")
                    Text("THEME", style = TType.label, color = TColor.textSecondary)
                    ThemePicker(theme) { theme = it }
                    if (s.edited) TextAction("Reset to generated story", onClick = { confirmReset = true }, color = TColor.red)
                }
            }
            item { StatChips(s) }
            if (s.highlights.isNotEmpty() && !editing) {
                item { SectionLabel("Highlights") }
                item {
                    Column(Modifier.trippyCard()) {
                        s.highlights.forEach { h ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(h.label.uppercase(), style = TType.label, color = TColor.textSecondary)
                                    Text(h.value, style = TType.headline, color = TColor.textPrimary)
                                }
                                h.detail?.let { Text(it, style = TType.footnote, color = TColor.textSecondary) }
                            }
                        }
                    }
                }
            }
            item { SectionLabel("Timeline", trailing = if (editing) "${ordered.size - visible.size} hidden" else null) }
            val rows = if (editing) ordered else visible
            if (rows.isEmpty()) item { Text("Nothing on this trip yet.", style = TType.footnote, color = TColor.textSecondary) }
            items(rows, key = { it.key }) { item ->
                val idx = order.indexOf(item.key)
                TimelineRow(
                    item = item, note = notes[item.key], editing = editing, hidden = hidden[item.key] == true, last = item == rows.last(),
                    onHide = { if (hidden[item.key] == true) hidden.remove(item.key) else hidden[item.key] = true },
                    onUp = { if (idx > 0) { order.removeAt(idx); order.add(idx - 1, item.key) } },
                    onDown = { if (idx in 0 until order.lastIndex) { order.removeAt(idx); order.add(idx + 1, item.key) } },
                    onNote = { noteFor = item },
                )
            }
        }
        // A full-screen dialog: inside this Column the LazyColumn has already taken all the height.
        if (playing) Dialog({ playing = false }, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            StoryPlayer(s, shownTitle, subtitle, visible, notes, theme) { playing = false }
        }
    }

    noteFor?.let { item ->
        var text by remember(item.key) { mutableStateOf(notes[item.key].orEmpty()) }
        AlertDialog(
            onDismissRequest = { noteFor = null },
            title = { Text(item.title, style = TType.headline) },
            text = { AuthField("Note", text, { text = it.take(280) }, placeholder = "What made this part memorable?") },
            confirmButton = { TextButton({ if (text.isBlank()) notes.remove(item.key) else notes[item.key] = text; noteFor = null }) { Text("Done", color = TColor.coral) } },
            dismissButton = { TextButton({ noteFor = null }) { Text("Cancel", color = TColor.textSecondary) } },
            containerColor = TColor.surfaceSheet,
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset story?", style = TType.headline) },
            text = { Text("Your title, theme, order, notes and hidden items go back to the generated story.", style = TType.footnote, color = TColor.textSecondary) },
            confirmButton = { TextButton({ confirmReset = false; run { repo.reset(tripId) } }) { Text("Reset", color = TColor.red) } },
            dismissButton = { TextButton({ confirmReset = false }) { Text("Cancel", color = TColor.textSecondary) } },
            containerColor = TColor.surfaceSheet,
        )
    }
}

@Composable
private fun StoryHero(s: TripStory, title: String, subtitle: String, theme: StoryTheme, onPlay: () -> Unit) {
    val (start, end) = StoryText.storyDates(s)
    val codes = StoryText.strip(StoryText.routeCodes(s.route)).let { (c, extra) -> c.joinToString(" – ") + if (extra > 0) " +$extra" else "" }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(theme.brush()).padding(22.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("TRIP STORY", style = TType.label, color = Color.White.copy(alpha = 0.6f))
            Text(title, style = TType.display, color = Color.White, maxLines = 3)
            if (subtitle.isNotBlank()) Text(subtitle, style = TType.callout, color = Color.White.copy(alpha = 0.8f))
            Text(StoryText.dateText(start, end).uppercase(), style = TType.footnoteStrong, color = Color.White.copy(alpha = 0.6f))
            if (codes.isNotBlank()) Text(codes, style = TType.monoLarge, color = Color(theme.palette.accent))
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).clickable(onClick = onPlay).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Play story", style = TType.calloutStrong, color = Color.White)
            }
        }
    }
}

@Composable
private fun StatChips(s: TripStory) {
    val cells = StoryText.tripStatCells(s, hideSpend = false)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.forEach { (label, value) ->
            Column(Modifier.clip(RoundedCornerShape(14.dp)).background(TColor.surface).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(label, style = TType.micro, color = TColor.textSecondary)
                Text(value, style = TType.numberSmall, color = TColor.textPrimary)
            }
        }
    }
}

@Composable
internal fun ThemePicker(selected: StoryTheme, onSelect: (StoryTheme) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StoryTheme.entries.forEach { t ->
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(t.brush())
                    .border(if (t == selected) 2.dp else 0.dp, if (t == selected) Color.White else Color.Transparent, CircleShape)
                    .clickable { onSelect(t) },
            )
        }
    }
}

@Composable
private fun TimelineRow(
    item: StoryItem, note: String?, editing: Boolean, hidden: Boolean, last: Boolean,
    onHide: () -> Unit, onUp: () -> Unit, onDown: () -> Unit, onNote: () -> Unit,
) {
    val tint = storyKindTint(item.kind)
    Row(Modifier.fillMaxWidth().alpha(if (hidden) 0.4f else 1f)) {
        Column(Modifier.width(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(4.dp))
            Box(Modifier.size(10.dp).clip(CircleShape).background(tint))
            if (!last) Box(Modifier.width(2.dp).height(64.dp).background(TColor.divider))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val day = item.day?.let { "DAY $it · " }.orEmpty()
            Text(day + StoryText.kindLabel(item.kind), style = TType.label, color = tint)
            Text(item.title, style = TType.calloutStrong, color = TColor.textPrimary)
            item.subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, style = TType.footnote, color = TColor.textSecondary, maxLines = 2) }
            item.metric?.let { Text(it, style = TType.footnoteStrong, color = TColor.textPrimary) }
            if (item.kind == "drive" && item.paths.isNotEmpty()) {
                samalabs.trippy.ui.components.DrivePathArt(
                    item.paths, Modifier.padding(top = 6.dp).fillMaxWidth().height(96.dp).clip(RoundedCornerShape(12.dp)),
                )
            }
            note?.let { Text("“$it”", style = TType.footnote, color = TColor.textSecondary) }
        }
        if (editing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallIcon(if (hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (hidden) "Show" else "Hide", onHide)
                SmallIcon(Icons.Filled.EditNote, "Note", onNote)
                Column {
                    SmallIcon(Icons.Filled.KeyboardArrowUp, "Move up", onUp)
                    SmallIcon(Icons.Filled.KeyboardArrowDown, "Move down", onDown)
                }
            }
        }
    }
}

@Composable
private fun SmallIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = TColor.textSecondary, modifier = Modifier.size(20.dp))
    }
}

private sealed interface Slide {
    data object Cover : Slide
    data class Moment(val item: StoryItem) : Slide
    data object Highlights : Slide
    data object Numbers : Slide
}

/** Full-screen slides: tap the right side to advance, the left third to go back. */
@Composable
private fun StoryPlayer(
    s: TripStory, title: String, subtitle: String, items: List<StoryItem>, notes: Map<String, String>, theme: StoryTheme, onClose: () -> Unit,
) {
    val slides = remember(items) {
        buildList {
            add(Slide.Cover); items.forEach { add(Slide.Moment(it)) }
            if (s.highlights.isNotEmpty()) add(Slide.Highlights)
            add(Slide.Numbers)
        }
    }
    var index by remember { mutableIntStateOf(0) }
    BackHandler { onClose() }
    val accent = Color(theme.palette.accent)
    Box(
        Modifier.fillMaxSize().background(theme.brush()).pointerInput(slides.size) {
            detectTapGestures { off ->
                if (off.x < size.width / 3f) index = (index - 1).coerceAtLeast(0)
                else if (index < slides.lastIndex) index++ else onClose()
            }
        }.safeDrawingPadding(),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                slides.indices.forEach { i ->
                    Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i <= index) 0.9f else 0.25f)))
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, "Close", tint = Color.White)
                }
            }
            AnimatedContent(slides[index.coerceIn(0, slides.lastIndex)], Modifier.weight(1f).fillMaxWidth(), label = "slide") { slide ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    when (slide) {
                        Slide.Cover -> {
                            val (start, end) = StoryText.storyDates(s)
                            Text(StoryText.dateText(start, end).uppercase(), style = TType.label, color = Color.White.copy(alpha = 0.7f))
                            Text(title, style = TType.hero, color = Color.White)
                            if (subtitle.isNotBlank()) Text(subtitle, style = TType.title3, color = Color.White.copy(alpha = 0.85f))
                            Spacer(Modifier.height(12.dp))
                            Text(StoryText.routeCodes(s.route).joinToString(" → "), style = TType.monoLarge, color = accent)
                        }
                        is Slide.Moment -> {
                            val it = slide.item
                            Text((it.day?.let { d -> "DAY $d · " }.orEmpty()) + StoryText.kindLabel(it.kind), style = TType.label, color = accent)
                            Spacer(Modifier.height(8.dp))
                            Text(it.title, style = TType.display, color = Color.White)
                            it.subtitle?.takeIf { t -> t.isNotBlank() }?.let { t -> Text(t, style = TType.title3, color = Color.White.copy(alpha = 0.8f)) }
                            it.metric?.let { m -> Text(m, style = TType.largeTitle, color = accent, modifier = Modifier.padding(top = 16.dp)) }
                            if (it.kind == "drive" && it.paths.isNotEmpty()) {
                                samalabs.trippy.ui.components.DrivePathArt(
                                    it.paths, Modifier.padding(top = 20.dp).fillMaxWidth().height(220.dp), color = accent, background = false,
                                )
                            }
                            notes[it.key]?.let { n -> Text("“$n”", style = TType.body, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(top = 16.dp)) }
                        }
                        Slide.Highlights -> {
                            Text("HIGHLIGHTS", style = TType.label, color = accent)
                            s.highlights.forEach { h ->
                                Column(Modifier.padding(top = 18.dp)) {
                                    Text(h.label.uppercase(), style = TType.label, color = Color.White.copy(alpha = 0.6f))
                                    Text(h.value, style = TType.title, color = Color.White)
                                    h.detail?.let { d -> Text(d, style = TType.callout, color = Color.White.copy(alpha = 0.75f)) }
                                }
                            }
                        }
                        Slide.Numbers -> {
                            Text("BY THE NUMBERS", style = TType.label, color = accent)
                            StoryText.tripStatCells(s, hideSpend = false).forEach { (l, v) ->
                                Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.Bottom) {
                                    Text(l, style = TType.label, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                                    Text(v, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
