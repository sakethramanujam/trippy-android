package samalabs.trippy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import samalabs.trippy.domain.Fmt
import samalabs.trippy.domain.SearchHit
import samalabs.trippy.domain.TripSearch
import samalabs.trippy.ui.LocalContainer
import samalabs.trippy.ui.components.plainClick
import samalabs.trippy.ui.components.trippyCard
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType

/** Search everything on the phone. Works offline because it reads the loaded (or cached) lists. */
@Composable
fun SearchScreen(onBack: () -> Unit, open: (SearchHit.Target) -> Unit) {
    val state by LocalContainer.current.trips.state.collectAsState()
    var query by rememberSaveable { mutableStateOfString() }
    val results = remember(query, state.trips, state.rentals) { TripSearch.search(query, state.trips, state.rentals) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxSize().background(TColor.canvas).safeDrawingPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TColor.textPrimary, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(TColor.surfaceRaised).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = TColor.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Trips, flights, hotels, PNRs…", style = TType.body, color = TColor.textTertiary)
                    BasicTextField(
                        query, { query = it }, singleLine = true,
                        textStyle = TType.body.copy(color = TColor.textPrimary), cursorBrush = SolidColor(TColor.coral),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) Icon(Icons.Filled.Cancel, "Clear", tint = TColor.textSecondary, modifier = Modifier.size(18.dp).plainClick { query = "" })
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            when {
                query.trim().length < 2 -> item {
                    Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Search trips, cities, airports, airlines, flight numbers, hotels, rental companies and confirmation numbers.",
                            style = TType.footnote, color = TColor.textSecondary)
                        state.lastSynced?.let { Text("Synced ${Fmt.ago(it, System.currentTimeMillis())}", style = TType.caption, color = TColor.textTertiary) }
                    }
                }
                results.isEmpty() -> item {
                    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Search, null, tint = TColor.textTertiary, modifier = Modifier.size(44.dp))
                        Text("No results for “${query.trim()}”", style = TType.title3, color = TColor.textPrimary, modifier = Modifier.padding(top = 12.dp))
                        Text("Check the spelling or try a new search.", style = TType.footnote, color = TColor.textSecondary)
                    }
                }
                else -> results.forEach { (kind, hits) ->
                    item(key = "h-$kind") {
                        Text("${kind.title} · ${hits.size}".uppercase(), style = TType.label, color = TColor.textSecondary,
                            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp))
                    }
                    items(hits.take(if (kind == SearchHit.Kind.TRIP) 30 else 50), key = { it.id }) { hit -> HitRow(hit) { open(hit.target) } }
                }
            }
        }
    }
}

private fun mutableStateOfString() = androidx.compose.runtime.mutableStateOf("")

private fun SearchHit.Kind.color(): Color = when (this) {
    SearchHit.Kind.CONFIRMATION -> TColor.stay
    SearchHit.Kind.TRIP, SearchHit.Kind.FLIGHT -> TColor.blue
    SearchHit.Kind.STAY -> TColor.purple
    SearchHit.Kind.RENTAL -> TColor.green
}

private fun SearchHit.icon() = when (target) {
    is SearchHit.Target.Trip -> if (kind == SearchHit.Kind.CONFIRMATION) Icons.Filled.Numbers else Icons.Filled.Luggage
    is SearchHit.Target.Flight -> if (kind == SearchHit.Kind.CONFIRMATION) Icons.Filled.Numbers else Icons.Filled.Flight
    is SearchHit.Target.Stay -> if (kind == SearchHit.Kind.CONFIRMATION) Icons.Filled.Numbers else Icons.Filled.Hotel
    is SearchHit.Target.Rental -> if (kind == SearchHit.Kind.CONFIRMATION) Icons.Filled.Numbers else Icons.Filled.DirectionsCar
}

@Composable
private fun HitRow(hit: SearchHit, onClick: () -> Unit) {
    val tint = hit.kind.color()
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp).trippyCard(12.dp).plainClick(onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.16f)), Alignment.Center) {
            Icon(hit.icon(), null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(hit.title, style = if (hit.kind == SearchHit.Kind.CONFIRMATION) TType.headline.copy(fontFamily = FontFamily.Monospace) else TType.headline,
                color = TColor.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(hit.subtitle, style = TType.footnote, color = TColor.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TColor.textTertiary, modifier = Modifier.size(20.dp))
    }
}
