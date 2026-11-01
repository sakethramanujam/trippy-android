package samalabs.trippy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import samalabs.trippy.domain.DriveDay
import samalabs.trippy.domain.Fmt
import samalabs.trippy.domain.PathProjection
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType

/** The drive's own GPS path, drawn as a glowing line with start and end dots (no map tiles). */
@Composable
fun DrivePathArt(paths: List<List<List<Double>>>, modifier: Modifier = Modifier, color: Color = TColor.teal, background: Boolean = true) {
    Canvas(modifier) {
        if (background) drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1A2E), Color(0xFF02060C))))
        val fitted = PathProjection.fit(paths, size.width, size.height, 14.dp.toPx())
        fitted.forEach { pts ->
            if (pts.size < 2) return@forEach
            val p = Path().apply { moveTo(pts[0].first, pts[0].second); pts.drop(1).forEach { lineTo(it.first, it.second) } }
            drawPath(p, color.copy(alpha = 0.25f), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(p, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        fitted.firstOrNull()?.firstOrNull()?.let { drawCircle(Color.White, 4.dp.toPx(), Offset(it.first, it.second)) }
        fitted.lastOrNull()?.lastOrNull()?.let {
            drawCircle(color, 5.dp.toPx(), Offset(it.first, it.second))
            drawCircle(Color.White, 2.dp.toPx(), Offset(it.first, it.second))
        }
    }
}

/** One driving day on a trip or journey: path art, from → to, miles and time. */
@Composable
fun DriveDayCard(day: DriveDay, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().trippyCard()) {
        if (day.paths.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))) {
                DrivePathArt(day.paths, Modifier.fillMaxSize())
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(TColor.teal.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.DirectionsCar, null, tint = TColor.teal, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(Fmt.dayYear(day.date.atStartOfDay()).uppercase(), style = TType.caption, color = TColor.textTertiary)
                Text(day.title, style = TType.calloutStrong, color = TColor.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (day.subtitle.isNotBlank()) Text(day.subtitle, style = TType.footnote, color = TColor.textSecondary)
            }
            Text(day.metric, style = TType.title3, color = TColor.teal)
        }
    }
}
