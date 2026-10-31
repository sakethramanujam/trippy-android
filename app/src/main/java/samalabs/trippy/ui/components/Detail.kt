package samalabs.trippy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType
import java.time.LocalDateTime
import samalabs.trippy.domain.Fmt

/** Tinted banner with the one line that matters right now ("Departs in 1d 8h"). */
@Composable
fun StatusBanner(title: String, subtitle: String? = null, color: Color = TColor.green, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.10f))
            .border(0.5.dp, color.copy(alpha = 0.2f), RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = TType.headline, color = color)
        subtitle?.takeIf { it.isNotBlank() && it != title }?.let { Text(it, style = TType.footnote, color = color.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
fun CircleIcon(icon: ImageVector, label: String, onClick: () -> Unit, size: Int = 38) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(TColor.surfaceRaised).plainClick(onClick), Alignment.Center) {
        Icon(icon, label, tint = TColor.textPrimary, modifier = Modifier.size((size * 0.47).dp))
    }
}

/** Yellow pill for the one fact you look for at the counter: seat, plate, total. */
@Composable
fun HighlightBlock(text: String, icon: ImageVector? = null) {
    Row(
        Modifier.widthIn(max = 170.dp).background(TColor.highlight, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        icon?.let { Icon(it, null, tint = Color.Black, modifier = Modifier.size(15.dp)) }
        Text(text, style = TType.headline, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Letter tile standing in for the iOS brand logos. */
@Composable
fun BrandTile(text: String, color: Color, size: Int = 44) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size / 4).dp)).background(color), Alignment.Center) {
        Text(text.take(2).uppercase(), style = if (size >= 40) TType.headline else TType.micro, color = Color.White)
    }
}

@Composable
fun SectionTitle(text: String, trailing: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, style = TType.title3, color = TColor.textPrimary, modifier = Modifier.weight(1f))
        trailing?.let { Text(it.uppercase(), style = TType.micro, color = TColor.textSecondary) }
    }
}

@Composable
fun MicroLabel(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), style = TType.label, color = TColor.textSecondary, modifier = modifier)

/** Two-column "DEPARTS / ARRIVES" card with big times. */
@Composable
fun TimesCard(leftLabel: String, left: LocalDateTime?, leftText: String? = null, rightLabel: String, right: LocalDateTime?, rightText: String? = null) {
    Row(Modifier.fillMaxWidth().trippyCard().height(IntrinsicSize.Min).padding(vertical = 18.dp, horizontal = 20.dp)) {
        TimeBlock(leftLabel, left, leftText, Alignment.Start, Modifier.weight(1f))
        Box(Modifier.width(0.5.dp).fillMaxHeight().background(TColor.divider))
        TimeBlock(rightLabel, right, rightText, Alignment.End, Modifier.weight(1f))
    }
}

@Composable
private fun TimeBlock(label: String, d: LocalDateTime?, text: String?, align: Alignment.Horizontal, modifier: Modifier) {
    Column(modifier, horizontalAlignment = align) {
        MicroLabel(label)
        Text(text ?: d?.let { Fmt.time(it).replace(" ", "") } ?: "—", style = TType.title, color = TColor.textPrimary, modifier = Modifier.padding(top = 4.dp))
        Text(d?.let { Fmt.dayYear(it) } ?: "Not set", style = TType.footnote, color = TColor.textSecondary)
    }
}

@Composable
fun QuickAction(text: String, icon: ImageVector, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)).plainClick(onClick).padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text, style = TType.calloutStrong, color = tint, modifier = Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun QuickActions(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

enum class FactKind { TEXT, COPY, ADDRESS, LINK, PHONE }

data class Fact(val label: String, val value: String, val mono: Boolean = false, val kind: FactKind = FactKind.TEXT, val url: String? = null)

/** What tapping a fact does: copy, open maps, open a link. Provided by the screen host. */
fun interface FactHandler { fun handle(f: Fact, long: Boolean) }

val LocalFactHandler = compositionLocalOf { FactHandler { _, _ -> } }

@Composable
fun FactList(title: String, facts: List<Fact>) {
    if (facts.isEmpty()) return
    val handler = LocalFactHandler.current
    Column {
        MicroLabel(title, Modifier.padding(bottom = 6.dp))
        facts.forEachIndexed { i, f ->
            if (i > 0) HorizontalDivider(color = TColor.divider)
            FactRow(f, handler)
        }
    }
}

@Composable
private fun FactRow(f: Fact, handler: FactHandler) {
    val actionable = f.kind != FactKind.TEXT
    val color = when (f.kind) { FactKind.ADDRESS, FactKind.LINK, FactKind.PHONE -> TColor.blue; else -> TColor.textPrimary }
    Row(
        Modifier.fillMaxWidth().then(if (actionable) Modifier.longPlainClick(onClick = { handler.handle(f, false) }, onLong = { handler.handle(f, true) }) else Modifier)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(f.label, style = TType.body, color = TColor.textSecondary, modifier = Modifier.padding(end = 16.dp))
        Text(
            f.value, style = if (f.mono) TType.headline.copy(fontFamily = FontFamily.Monospace) else TType.headline, color = color,
            textAlign = TextAlign.End, modifier = Modifier.weight(1f),
        )
        factIcon(f.kind)?.let { Icon(it, null, tint = TColor.blue, modifier = Modifier.padding(start = 8.dp, top = 2.dp).size(17.dp)) }
    }
}

private fun factIcon(k: FactKind): ImageVector? = when (k) {
    FactKind.COPY -> Icons.Filled.ContentCopy
    FactKind.ADDRESS -> Icons.Filled.Map
    FactKind.LINK -> Icons.AutoMirrored.Filled.OpenInNew
    FactKind.PHONE -> Icons.Filled.Call
    FactKind.TEXT -> null
}
