package samalabs.trippy.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import samalabs.trippy.data.StoryRepository
import samalabs.trippy.data.TripStory
import samalabs.trippy.data.YearStory
import samalabs.trippy.domain.CardFormat
import samalabs.trippy.domain.StoryTheme
import samalabs.trippy.ui.LocalContainer
import samalabs.trippy.ui.components.FormBanner
import samalabs.trippy.ui.components.PrimaryButton
import samalabs.trippy.ui.share.ShareCards
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType
import java.time.LocalDate

/** Trip recap card when [tripId] is set, else the year-in-travel card. Preview, tweak, share. */
@Composable
fun ShareCardScreen(tripId: Int?, initialYear: Int?, onBack: () -> Unit) {
    val c = LocalContainer.current
    val ctx = LocalContext.current
    val repo = remember { StoryRepository(c.api) }
    var story by remember { mutableStateOf<TripStory?>(null) }
    var yearStory by remember { mutableStateOf<YearStory?>(null) }
    var year by remember { mutableIntStateOf(initialYear ?: LocalDate.now().year) }
    var format by remember { mutableStateOf(CardFormat.Story) }
    var theme by remember { mutableStateOf(StoryTheme.Indigo) }
    var hideSpend by remember { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var usePhoto by remember { mutableStateOf(false) }
    var photoIndex by remember { mutableIntStateOf(0) }
    var photoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tripId, year) {
        error = null
        try {
            if (tripId != null) {
                if (story == null) repo.story(tripId).also { story = it; theme = StoryTheme.from(it.theme) }
            } else {
                yearStory = null; bitmap = null
                yearStory = repo.year(year)
            }
        } catch (e: Exception) { error = e.message ?: "Couldn't load the card." }
    }
    // The background photo: this phone's full photo when it's here and readable, else the server thumbnail.
    LaunchedEffect(usePhoto, photoIndex, story) {
        val p = story?.tripPhotos?.getOrNull(photoIndex)
        photoBitmap = if (!usePhoto || p == null) null else {
            (if (p.deviceId == c.photos.deviceId) samalabs.trippy.ui.photos.PhotoLibrary.large(ctx, p.assetId) else null)
                ?: runCatching { c.photos.thumb(p) }.getOrNull()?.let { withContext(Dispatchers.Default) { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) } }
        }
    }
    LaunchedEffect(story, yearStory, format, theme, hideSpend, photoBitmap) {
        val s = story; val y = yearStory
        bitmap = withContext(Dispatchers.Default) {
            when {
                s != null -> ShareCards.trip(s, format, theme, hideSpend, photoBitmap)
                y != null -> ShareCards.year(y, format)
                else -> null
            }
        }
    }

    Column(Modifier.fillMaxSize().background(TColor.canvas).safeDrawingPadding()) {
        BackBar(if (tripId != null) "Share card" else "Year in travel", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            error?.let { FormBanner(it) }
            if (tripId == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoundButton(onClick = { year-- }) { Icon(Icons.Filled.KeyboardArrowLeft, "Previous year", tint = TColor.textPrimary, modifier = Modifier.size(20.dp)) }
                    Text("$year", style = TType.title2, color = TColor.textPrimary, modifier = Modifier.padding(horizontal = 20.dp))
                    RoundButton(onClick = { if (year < LocalDate.now().year) year++ }) {
                        Icon(Icons.Filled.KeyboardArrowRight, "Next year", tint = if (year < LocalDate.now().year) TColor.textPrimary else TColor.textTertiary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth(if (format == CardFormat.Story) 0.72f else 0.92f)
                    .aspectRatio(format.width.toFloat() / format.height)
                    .clip(RoundedCornerShape(18.dp)).background(TColor.surface),
                contentAlignment = Alignment.Center,
            ) {
                val b = bitmap
                if (b != null) Image(b.asImageBitmap(), "Card preview", Modifier.fillMaxSize())
                else if (error == null) CircularProgressIndicator(color = TColor.coral)
            }
            Box(Modifier.fillMaxWidth()) {
                MoneySegmented(CardFormat.entries.map { it.label }, format.ordinal) { format = CardFormat.entries[it] }
            }
            if (tripId != null) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("THEME", style = TType.label, color = TColor.textSecondary)
                    ThemePicker(theme) { theme = it }
                }
                val tripPhotos = story?.tripPhotos.orEmpty()
                if (tripPhotos.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Photo background", style = TType.body, color = TColor.textPrimary, modifier = Modifier.weight(1f))
                        Switch(usePhoto, { usePhoto = it }, colors = SwitchDefaults.colors(checkedTrackColor = TColor.coral))
                    }
                    if (usePhoto) {
                        Box(Modifier.fillMaxWidth()) {
                            samalabs.trippy.ui.photos.PhotoStrip(tripPhotos, 64.dp) { photoIndex = it }
                        }
                    }
                }
                if (story?.stats?.spendText != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Hide spending", style = TType.body, color = TColor.textPrimary, modifier = Modifier.weight(1f))
                        Switch(hideSpend, { hideSpend = it }, colors = SwitchDefaults.colors(checkedTrackColor = TColor.coral))
                    }
                }
            }
            Spacer(Modifier.size(4.dp))
        }
        PrimaryButton(
            "Share", onClick = {
                val b = bitmap ?: return@PrimaryButton
                val name = if (tripId != null) "trippy-trip-$tripId" else "trippy-$year"
                try { ShareCards.share(ctx, b, name) } catch (e: Exception) { error = e.message ?: "Couldn't share." }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), enabled = bitmap != null,
        )
    }
}
