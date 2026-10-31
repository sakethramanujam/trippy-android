package samalabs.trippy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import samalabs.trippy.domain.LatLng
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

data class ArtStop(val code: String, val at: LatLng)

/**
 * Stand-in for the iOS satellite map: a dark ocean with a faint graticule and glowing great-arc
 * style curves between the stops. Longitudes are unwrapped so a Pacific crossing stays one arc.
 */
@Composable
fun RouteArt(stops: List<ArtStop>, modifier: Modifier = Modifier, topInset: androidx.compose.ui.unit.Dp = 0.dp, bottomInset: androidx.compose.ui.unit.Dp = 0.dp) {
    val pts = remember(stops) { unwrap(stops) }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val top = with(density) { topInset.toPx() }
        val bottom = with(density) { bottomInset.toPx() }
        val proj = remember(pts, w, h, top, bottom) { Projection.fit(pts.map { it.second }, w, h, top, bottom) }
        Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1A2E), Color(0xFF071222), Color(0xFF02060C))))
            // Graticule every 10°.
            val grid = Color.White.copy(alpha = 0.045f)
            var lng = (proj.minLng / 10).toInt() * 10.0 - 10
            while (lng <= proj.maxLng + 10) {
                val x = proj.x(lng); drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f); lng += 10
            }
            var lat = (proj.minLat / 10).toInt() * 10.0 - 10
            while (lat <= proj.maxLat + 10) {
                val y = proj.y(lat); drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f); lat += 10
            }
            val xy = pts.map { Offset(proj.x(it.second.lng), proj.y(it.second.lat)) }
            for (i in 0 until xy.size - 1) {
                val a = xy[i]; val b = xy[i + 1]
                if (hypot(b.x - a.x, b.y - a.y) < 2f) continue
                val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
                // Bow the curve sideways from the chord, toward the top of the screen.
                val len = hypot(b.x - a.x, b.y - a.y)
                var nx = -(b.y - a.y) / len; var ny = (b.x - a.x) / len
                if (ny > 0 || (ny == 0f && nx > 0)) { nx = -nx; ny = -ny }
                val ctrl = Offset(mid.x + nx * len * 0.22f, mid.y + ny * len * 0.22f)
                val path = Path().apply { moveTo(a.x, a.y); quadraticTo(ctrl.x, ctrl.y, b.x, b.y) }
                drawPath(path, TColor.route.copy(alpha = 0.18f), style = Stroke(width = 18f, cap = StrokeCap.Round))
                drawPath(path, TColor.route.copy(alpha = 0.35f), style = Stroke(width = 9f, cap = StrokeCap.Round))
                drawPath(path, Color(0xFF8FC2FF), style = Stroke(width = 4f, cap = StrokeCap.Round))
            }
            xy.forEach { drawCircle(Color.White, 6f, it); drawCircle(TColor.route, 4f, it) }
        }
        val seen = HashSet<String>()
        pts.forEach { (code, p) ->
            if (!seen.add(code)) return@forEach
            val x = proj.x(p.lng); val y = proj.y(p.lat)
            Text(
                code, style = TType.footnoteStrong, color = Color.White,
                modifier = Modifier
                    .offset { IntOffset((x - 36f).roundToInt().coerceIn(0, max(0, (w - 120).toInt())), (y + 14f).roundToInt()) }
                    .background(Color(0xFF0A64D6), RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

private fun unwrap(stops: List<ArtStop>): List<Pair<String, LatLng>> {
    if (stops.isEmpty()) return emptyList()
    val out = mutableListOf(stops[0].code to stops[0].at)
    for (s in stops.drop(1)) {
        var lng = s.at.lng
        val prev = out.last().second.lng
        while (lng - prev > 180) lng -= 360
        while (prev - lng > 180) lng += 360
        out += s.code to LatLng(s.at.lat, lng)
    }
    return out
}

internal class Projection(val minLat: Double, val maxLat: Double, val minLng: Double, val maxLng: Double, private val w: Float, private val h: Float, private val top: Float, private val bottom: Float) {
    private val kx: Double
    private val scale: Double
    private val ox: Double
    private val oy: Double
    init {
        kx = cos(Math.toRadians((minLat + maxLat) / 2)).coerceAtLeast(0.3)
        val spanX = max(1e-6, (maxLng - minLng) * kx)
        val spanY = max(1e-6, maxLat - minLat)
        val availH = h - top - bottom
        scale = minOf(w / spanX, availH / spanY)
        ox = (w - spanX * scale) / 2 - minLng * kx * scale
        oy = top + (availH - spanY * scale) / 2 + maxLat * scale
    }
    fun x(lng: Double): Float = (lng * kx * scale + ox).toFloat()
    fun y(lat: Double): Float = (oy - lat * scale).toFloat()

    companion object {
        fun fit(points: List<LatLng>, w: Float, h: Float, top: Float, bottom: Float): Projection {
            if (points.isEmpty()) return Projection(20.0, 50.0, -125.0, -70.0, w, h, top, bottom)
            var minLat = points.minOf { it.lat }; var maxLat = points.maxOf { it.lat }
            var minLng = points.minOf { it.lng }; var maxLng = points.maxOf { it.lng }
            // Pad, and never zoom in past a ~6° window so a single city still reads as a map.
            val padLat = max(3.0, (maxLat - minLat) * 0.35); val padLng = max(4.0, (maxLng - minLng) * 0.18)
            minLat -= padLat; maxLat += padLat; minLng -= padLng; maxLng += padLng
            if (abs(maxLat - minLat) > 160) { minLat = -80.0; maxLat = 80.0 }
            return Projection(minLat, maxLat, minLng, maxLng, w, h, top, bottom)
        }
    }
}
