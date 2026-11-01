package samalabs.trippy.ui.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import samalabs.trippy.data.ApiTrip
import samalabs.trippy.data.PhotoUpload
import samalabs.trippy.data.TripPhoto
import samalabs.trippy.domain.Fmt
import samalabs.trippy.domain.LatLng
import samalabs.trippy.domain.LocalPhoto
import samalabs.trippy.domain.PhotoSuggest
import samalabs.trippy.domain.PhotoSuggestion
import samalabs.trippy.domain.events
import samalabs.trippy.ui.LocalContainer
import samalabs.trippy.ui.components.SectionLabel
import samalabs.trippy.ui.components.TextAction
import samalabs.trippy.ui.components.trippyCard
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType
import java.time.format.DateTimeFormatter

private const val PICK_MAX = 30

/** Coordinates the trip touches: airports, stays, rental counters. */
fun ApiTrip.photoPlaces(): List<LatLng> = buildList {
    events(emptyList()).mapNotNull { it.coordinate }.forEach(::add)
    lodgings.forEach { l -> if (l.lat != null && l.lng != null) add(LatLng(l.lat, l.lng)) }
}.distinct()

/**
 * Photos on a trip: a strip of your thumbnails, "Add photos" (system Photo Picker, no permission),
 * and opt-in suggestions from the library by date and place that you accept or reject one by one.
 */
@Composable
fun TripPhotosSection(trip: ApiTrip, refreshKey: Any?) {
    val c = LocalContainer.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var photos by remember(trip.id) { mutableStateOf<List<TripPhoto>>(emptyList()) }
    var loaded by remember(trip.id) { mutableStateOf(false) }
    var suggestions by remember(trip.id) { mutableStateOf<List<PhotoSuggestion>?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var gallery by remember { mutableStateOf<Int?>(null) }
    var access by remember { mutableStateOf(PhotoAccess.level(ctx)) }

    suspend fun reload() { runCatching { c.photos.list(trip.id) }.onSuccess { photos = it; loaded = true } }
    LaunchedEffect(trip.id, refreshKey) { reload() }

    fun upload(items: List<LocalPhoto>) {
        if (items.isEmpty()) return
        scope.launch {
            var ok = 0; var failed: String? = null
            items.forEachIndexed { i, p ->
                busy = "Adding ${i + 1} of ${items.size}…"
                runCatching {
                    val b64 = PhotoLibrary.thumbnail(ctx, Uri.parse(p.uri)) ?: error("Couldn't read that photo.")
                    val taken = p.takenAt?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    c.photos.add(trip.id, PhotoUpload(c.photos.deviceId, p.assetId, taken, p.lat, p.lng, b64))
                }.onSuccess { ok++ }.onFailure { failed = it.message }
            }
            busy = null
            message = when {
                failed != null && ok == 0 -> failed
                failed != null -> "Added $ok. Some couldn't be added: $failed"
                else -> null
            }
            reload()
        }
    }

    fun findSuggestions() {
        scope.launch {
            busy = "Looking through your photos…"
            val window = PhotoSuggest.window(Fmt.parse(trip.startDate), Fmt.parse(trip.endDate))
            if (window == null) { busy = null; message = "Add dates to this trip to get photo suggestions."; return@launch }
            val found = runCatching { PhotoLibrary.scan(ctx, window.first, window.second) }.getOrElse { emptyList() }
            val skip = c.photos.rejected(trip.id) + photos.filter { it.deviceId == c.photos.deviceId }.map { it.assetId }
            suggestions = withContext(Dispatchers.Default) {
                PhotoSuggest.suggest(found, Fmt.parse(trip.startDate), Fmt.parse(trip.endDate), trip.photoPlaces(), skip)
            }
            busy = null
            message = if (suggestions.isNullOrEmpty()) "No new photos from these dates." else null
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(PICK_MAX)) { uris ->
        if (uris.isNotEmpty()) scope.launch { upload(PhotoLibrary.fromPicker(ctx, uris)) }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = PhotoAccess.level(ctx)
        if (access != LibraryAccess.None) findSuggestions() else message = "Without photo access Trippy can't suggest photos. You can still add them with Add photos."
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Photos", trailing = photos.size.takeIf { it > 0 }?.toString())
        if (photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos, key = { it.id }) { p ->
                    RemoteThumb(p, Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).clickable { gallery = photos.indexOf(p) })
                }
            }
        } else if (loaded) {
            Text("Add photos from this trip. They stay on your phone; your server keeps a small preview.", style = TType.footnote, color = TColor.textSecondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextAction("Add photos", onClick = {
                message = null
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
            TextAction("Suggest from trip dates", onClick = {
                message = null
                if (access == LibraryAccess.None) permissions.launch(PhotoAccess.request()) else findSuggestions()
            }, color = TColor.teal)
            if (photos.isNotEmpty()) TextAction("See all", onClick = { gallery = -1 }, color = TColor.textSecondary)
        }
        if (access == LibraryAccess.Partial) {
            Text("Trippy can see only the photos you picked.", style = TType.footnote, color = TColor.textTertiary)
            TextAction("Choose more photos", onClick = { permissions.launch(PhotoAccess.request()) }, color = TColor.teal, style = TType.footnoteStrong)
        }
        busy?.let { b ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), color = TColor.teal, strokeWidth = 2.dp)
                Text(b, style = TType.footnote, color = TColor.textSecondary)
            }
        }
        message?.let { Text(it, style = TType.footnote, color = TColor.textSecondary) }
        suggestions?.takeIf { it.isNotEmpty() }?.let { list ->
            SuggestionsCard(
                list,
                onAccept = { s -> suggestions = list - s; upload(listOf(s.photo)) },
                onReject = { s -> suggestions = list - s; c.photos.reject(trip.id, listOf(s.photo.assetId)) },
                onAcceptAll = { suggestions = null; upload(list.map { it.photo }) },
                onRejectAll = { suggestions = null; c.photos.reject(trip.id, list.map { it.photo.assetId }) },
            )
        }
    }

    gallery?.let { start ->
        PhotoGallery(photos, start, onClose = { gallery = null }, onDelete = { p ->
            scope.launch { runCatching { c.photos.delete(p) }.onFailure { message = it.message }; reload(); if (photos.isEmpty()) gallery = null }
        })
    }
}

@Composable
private fun SuggestionsCard(
    list: List<PhotoSuggestion>,
    onAccept: (PhotoSuggestion) -> Unit,
    onReject: (PhotoSuggestion) -> Unit,
    onAcceptAll: () -> Unit,
    onRejectAll: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().trippyCard().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("${list.size} photo${if (list.size == 1) "" else "s"} from this trip?", style = TType.calloutStrong, color = TColor.textPrimary)
        Text("Only the ones you keep get a small preview on your server.", style = TType.footnote, color = TColor.textSecondary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.photo.assetId }) { s ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LocalThumb(s.photo.uri, Modifier.size(104.dp).clip(RoundedCornerShape(12.dp)))
                    Text(s.photo.takenAt?.let { Fmt.monthDay(it) + " · " + Fmt.time(it) } ?: "", style = TType.caption, color = TColor.textTertiary)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RoundIcon(Icons.Filled.Close, "Not from this trip", TColor.textSecondary) { onReject(s) }
                        RoundIcon(Icons.Filled.Check, "Add to trip", TColor.teal) { onAccept(s) }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextAction("Add all", onClick = onAcceptAll, color = TColor.teal)
            TextAction("None of these", onClick = onRejectAll, color = TColor.textSecondary)
        }
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).background(TColor.surfaceRaised).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Full-screen grid of a trip's photos ([start] -1 opens the grid); tap one to see it bigger and remove it from the trip. */
@Composable
fun PhotoGallery(photos: List<TripPhoto>, start: Int, onClose: () -> Unit, onDelete: ((TripPhoto) -> Unit)? = null) {
    var open by remember { mutableStateOf<TripPhoto?>(photos.getOrNull(start)) }
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(TColor.canvas).safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Photos", style = TType.title3, color = TColor.textPrimary, modifier = Modifier.weight(1f))
                RoundIcon(Icons.Filled.Close, "Close", TColor.textPrimary, onClose)
            }
            val sel = open
            if (sel != null) {
                RemoteThumb(sel, Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)), ContentScale.Fit)
                Text(sel.takenAt?.let { Fmt.parse(it) }?.let { Fmt.dayYear(it) + " · " + Fmt.time(it) } ?: "", style = TType.footnote, color = TColor.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextAction("Back to all", onClick = { open = null })
                    if (onDelete != null) TextAction("Remove from trip", onClick = { onDelete(sel); open = null }, color = TColor.red)
                }
                Text("Removing only deletes the preview on your server, never the photo on your phone.", style = TType.caption, color = TColor.textTertiary)
            } else {
                LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(photos, key = { it.id }) { p ->
                        RemoteThumb(p, Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable { open = p })
                    }
                }
            }
        }
    }
}

/** A server thumbnail (cached in memory). */
@Composable
fun RemoteThumb(photo: TripPhoto, modifier: Modifier, scale: ContentScale = ContentScale.Crop) {
    val c = LocalContainer.current
    val bmp by produceState<Bitmap?>(null, photo.id) {
        value = runCatching { c.photos.thumb(photo) }.getOrNull()?.let { withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(it, 0, it.size) } }
    }
    ThumbBox(bmp, modifier, scale)
}

/** A photo still on this phone, not uploaded. */
@Composable
fun LocalThumb(uri: String, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, uri) { value = PhotoLibrary.bitmap(ctx, Uri.parse(uri), 256) }
    ThumbBox(bmp, modifier, ContentScale.Crop)
}

@Composable
private fun ThumbBox(bmp: Bitmap?, modifier: Modifier, scale: ContentScale) {
    Box(modifier.background(TColor.surfaceRaised), contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = scale)
        else Icon(Icons.Filled.Photo, null, tint = TColor.textTertiary, modifier = Modifier.size(22.dp))
    }
}

/** A row of story photos (no actions). */
@Composable
fun PhotoStrip(photos: List<TripPhoto>, size: Dp = 72.dp, onOpen: ((Int) -> Unit)? = null) {
    if (photos.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(photos, key = { it.id }) { p ->
            val m = Modifier.size(size).clip(RoundedCornerShape(10.dp))
            RemoteThumb(p, if (onOpen != null) m.clickable { onOpen(photos.indexOf(p)) } else m)
        }
    }
}
