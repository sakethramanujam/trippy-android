package samalabs.trippy.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import samalabs.trippy.data.TripStory
import samalabs.trippy.data.YearStory
import samalabs.trippy.domain.CardFormat
import samalabs.trippy.domain.CardPalette
import samalabs.trippy.domain.StoryText
import samalabs.trippy.domain.StoryTheme
import java.io.File
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Passport-style recap cards drawn straight onto a bitmap, so they look the same on every device. */
object ShareCards {
    private const val SCALE = 3f
    private val bold: Typeface by lazy { Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val medium: Typeface by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    private val monoBold: Typeface by lazy { Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }

    fun trip(story: TripStory, format: CardFormat, theme: StoryTheme, hideSpend: Boolean): Bitmap =
        draw(format) { c ->
            background(c, format, theme.palette)
            if (format == CardFormat.Story) tripStory(c, story, theme.palette, hideSpend) else tripSquare(c, story, theme.palette, hideSpend)
        }

    fun year(y: YearStory, format: CardFormat): Bitmap =
        draw(format) { c ->
            background(c, format, StoryTheme.Year)
            if (format == CardFormat.Story) yearStory(c, y) else yearSquare(c, y)
        }

    /** Writes the PNG to cache/share and opens the system share sheet. */
    fun share(ctx: Context, bitmap: Bitmap, name: String) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".shares", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Share card").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private inline fun draw(f: CardFormat, block: (Canvas) -> Unit): Bitmap {
        val b = Bitmap.createBitmap((f.width * SCALE).roundToInt(), (f.height * SCALE).roundToInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.scale(SCALE, SCALE)
        block(c)
        return b
    }

    // --- Layouts ---------------------------------------------------------------------------------

    private fun tripStory(c: Canvas, s: TripStory, pal: CardPalette, hideSpend: Boolean) {
        val w = 360f; val h = 640f; val pad = 28f; val cw = w - 2 * pad
        header(c, "TRIP RECAP", pad, 44f, w - pad)
        val titleH = wrapped(c, s.displayTitle, text(40f, white(1f), bold, -0.6f), pad, 58f, cw, 2)
        var y = 58f + titleH + 22f
        val (start, end) = StoryText.storyDates(s)
        c.drawText(StoryText.dateText(start, end).uppercase(), pad, y, text(13f, white(0.6f), medium, 1.5f))
        y += 28f
        val (codes, extra) = StoryText.strip(StoryText.routeCodes(s.route))
        if (codes.isNotEmpty()) { routeStrip(c, codes, extra, pad, y, cw, argb(pal.accent)); y += 22f }
        val cells = StoryText.tripStatCells(s, hideSpend).take(6)
        val rowH = 64f
        val rows = (cells.size + 2) / 3
        val statsTop = h - pad - 40f - rows * rowH
        tripMap(c, s, RectF(pad, y, w - pad, statsTop - 18f), pal)
        grid(c, cells, pad, statsTop, cw, floatArrayOf(0.28f, 0.44f, 0.28f), 30f)
        mrz(c, StoryText.tripMrz(s), pad, h - pad, cw)
    }

    private fun tripSquare(c: Canvas, s: TripStory, pal: CardPalette, hideSpend: Boolean) {
        val w = 360f; val pad = 24f; val cw = w - 2 * pad
        val mark = text(15f, white(1f), bold)
        fit(c, s.displayTitle, text(28f, white(1f), bold, -0.4f), pad, 52f, cw - mark.measureText("Trippy") - 16f)
        mark.textAlign = Paint.Align.RIGHT
        c.drawText("Trippy", w - pad, 50f, mark)
        val (start, end) = StoryText.storyDates(s)
        c.drawText(StoryText.dateText(start, end).uppercase(), pad, 76f, text(12f, white(0.6f), medium, 1.5f))
        tripMap(c, s, RectF(pad, 92f, w - pad, 220f), pal)
        grid(c, StoryText.tripStatCells(s, hideSpend).take(3), pad, 238f, cw, floatArrayOf(0.28f, 0.44f, 0.28f), 26f)
        val (codes, extra) = StoryText.strip(StoryText.routeCodes(s.route))
        if (codes.isNotEmpty()) routeStrip(c, codes, extra, pad, 322f, cw, argb(pal.accent))
    }

    private fun yearStory(c: Canvas, y: YearStory) {
        val w = 360f; val h = 640f; val pad = 28f; val cw = w - 2 * pad
        header(c, "YEAR IN TRAVEL", pad, 44f, w - pad)
        c.drawText("${y.year}", pad - 3f, 132f, text(84f, white(1f), bold, -2f))
        c.drawText("${y.trips} trip${if (y.trips == 1) "" else "s"} · ${y.daysAway} days away", pad, 162f, text(16f, white(0.75f), medium))
        val cells = StoryText.yearStatCells(y).take(6)
        val rows = (cells.size + 2) / 3
        val statsTop = h - pad - 40f - rows * 64f
        var mapBottom = statsTop - 18f
        if (y.topDestinations.isNotEmpty()) {
            val names = y.topDestinations.take(3).joinToString(" · ") { it.city }
            c.drawText("TOP PLACES", pad, statsTop - 44f, label())
            fit(c, names, text(16f, white(0.92f), bold), pad, statsTop - 22f, cw)
            mapBottom = statsTop - 64f
        }
        yearMap(c, y, RectF(pad, 182f, w - pad, mapBottom))
        grid(c, cells, pad, statsTop, cw, floatArrayOf(0.28f, 0.44f, 0.28f), 30f)
        mrz(c, StoryText.yearMrz(y), pad, h - pad, cw)
    }

    private fun yearSquare(c: Canvas, y: YearStory) {
        val w = 360f; val pad = 24f; val cw = w - 2 * pad
        header(c, "YEAR IN TRAVEL", pad, 40f, w - pad)
        c.drawText("${y.year}", pad - 2f, 98f, text(56f, white(1f), bold, -1.5f))
        val sum = text(14f, white(0.75f), medium).apply { textAlign = Paint.Align.RIGHT }
        c.drawText("${y.trips} trips · ${y.daysAway} days", w - pad, 96f, sum)
        yearMap(c, y, RectF(pad, 114f, w - pad, 250f))
        grid(c, StoryText.yearStatCells(y).take(3), pad, 268f, cw, floatArrayOf(0.28f, 0.44f, 0.28f), 26f)
    }

    // --- Pieces ----------------------------------------------------------------------------------

    private fun background(c: Canvas, f: CardFormat, p: CardPalette) {
        val w = f.width.toFloat(); val h = f.height.toFloat()
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h, p.colors.map { argb(it) }.toIntArray(), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, h, bg)
        glow(c, w * 0.9f, h * 0.06f, w * 0.8f, argb(p.accent, 0.32f))
        glow(c, w * 0.02f, h * 0.78f, w * 0.75f, argb(p.colors[0], 0.5f))
    }

    private fun glow(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        val g = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(x, y, r, intArrayOf(color, color and 0x00FFFFFF), null, Shader.TileMode.CLAMP) }
        c.drawCircle(x, y, r, g)
    }

    private fun header(c: Canvas, title: String, x: Float, baseline: Float, right: Float) {
        c.drawText(title, x, baseline, label())
        val mark = text(17f, white(1f), bold, -0.2f).apply { textAlign = Paint.Align.RIGHT }
        c.drawText("Trippy", right, baseline + 1f, mark)
    }

    private fun label(): TextPaint = text(12f, white(0.55f), medium, 1.2f)

    private fun routeStrip(c: Canvas, codes: List<String>, extra: Int, x: Float, cy: Float, width: Float, accent: Int) {
        val plane = Path().apply {
            moveTo(x, cy - 5.5f); lineTo(x + 13f, cy); lineTo(x, cy + 5.5f); lineTo(x + 3.5f, cy); close()
        }
        c.drawPath(plane, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
        val p = text(14f, white(0.95f), monoBold, 0.4f)
        val labels = codes + if (extra > 0) listOf("+$extra") else emptyList()
        val widths = labels.map { p.measureText(it) }
        val start = x + 22f
        val gap = if (labels.size > 1) ((width - 22f - widths.sum()) / (labels.size - 1)).coerceAtMost(70f) else 0f
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white(0.35f); strokeWidth = 1.2f; style = Paint.Style.STROKE; pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f)
        }
        var cx = start
        labels.forEachIndexed { i, s ->
            c.drawText(s, cx, cy + 5f, p)
            cx += widths[i]
            if (i < labels.lastIndex) {
                if (gap > 14f) c.drawLine(cx + 6f, cy, cx + gap - 6f, cy, dash)
                cx += gap
            }
        }
    }

    private fun tripMap(c: Canvas, s: TripStory, rect: RectF, pal: CardPalette) {
        val pts = s.route.filter { it.lat != null && it.lng != null }
        val codes = pts.map { it.code ?: it.city?.take(3)?.uppercase() }
        mapPanel(c, rect, pts.map { it.lat!! to it.lng!! }, codes, pts.indices.zipWithNext().map { (a, b) -> Triple(a, b, 1f) }, pts.size, argb(pal.accent))
    }

    private fun yearMap(c: Canvas, y: YearStory, rect: RectF) {
        val pts = y.points.filter { it.lat != null && it.lng != null }
        val all = ArrayList<Pair<Double, Double>>(pts.map { it.lat!! to it.lng!! })
        val labels = ArrayList<String?>(pts.mapIndexed { i, p -> if (i < 6) (p.code ?: p.city?.take(3)?.uppercase()) else null })
        val segs = ArrayList<Triple<Int, Int, Float>>()
        val top = y.arcs.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
        for (a in y.arcs) {
            if (a.from.size < 2 || a.to.size < 2) continue
            all += a.from[0] to a.from[1]; labels += null
            all += a.to[0] to a.to[1]; labels += null
            segs += Triple(all.size - 2, all.size - 1, 0.6f + 0.8f * a.count / top)
        }
        mapPanel(c, rect, all, labels, segs, pts.size, argb(StoryTheme.Year.accent))
    }

    private fun mapPanel(
        c: Canvas, rect: RectF, pts: List<Pair<Double, Double>>, labels: List<String?>,
        segs: List<Triple<Int, Int, Float>>, dots: Int, accent: Int,
    ) {
        if (rect.height() < 40f) return
        val r = 16f
        c.save()
        c.clipPath(Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) })
        c.drawRect(rect, Paint().apply { color = white(0.06f) })
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white(0.05f); strokeWidth = 0.6f }
        for (i in 1..5) c.drawLine(rect.left + rect.width() * i / 6, rect.top, rect.left + rect.width() * i / 6, rect.bottom, grid)
        for (i in 1..3) c.drawLine(rect.left, rect.top + rect.height() * i / 4, rect.right, rect.top + rect.height() * i / 4, grid)
        val proj = StoryText.project(pts, rect.width(), rect.height(), 28f).map { (x, y) -> rect.left + x to rect.top + y }
        if (proj.isEmpty()) {
            val p = text(12f, white(0.4f), medium, 1f).apply { textAlign = Paint.Align.CENTER }
            c.drawText("NO MAPPED PLACES", rect.centerX(), rect.centerY() + 4f, p)
        }
        val glowLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = accent and 0x00FFFFFF or (0x40 shl 24) }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = accent }
        for ((a, b, weight) in segs) {
            val (x0, y0) = proj[a]; val (x1, y1) = proj[b]
            val d = hypot(x1 - x0, y1 - y0)
            if (d < 0.5f) continue
            val mx = (x0 + x1) / 2; val my = (y0 + y1) / 2
            var nx = -(y1 - y0) / d; var ny = (x1 - x0) / d
            if (ny > 0) { nx = -nx; ny = -ny }
            val path = Path().apply { moveTo(x0, y0); quadTo(mx + nx * d * 0.2f, my + ny * d * 0.2f, x1, y1) }
            glowLine.strokeWidth = 6f * weight
            line.strokeWidth = 2.2f * weight
            c.drawPath(path, glowLine)
            c.drawPath(path, line)
        }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent and 0x00FFFFFF or (0x80 shl 24) }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val code = text(9f, white(0.75f), monoBold, 0.4f)
        for (i in 0 until minOf(dots, proj.size)) {
            val (x, y) = proj[i]
            c.drawCircle(x, y, 6f, ring)
            c.drawCircle(x, y, 3.2f, dot)
            labels.getOrNull(i)?.let { c.drawText(it, x + 8f, y - 6f, code) }
        }
        c.restore()
        c.drawRoundRect(rect, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = white(0.14f) })
    }

    private fun grid(c: Canvas, cells: List<Pair<String, String>>, x: Float, top: Float, width: Float, weights: FloatArray, valueSize: Float) {
        val rowH = valueSize + 34f
        val value = text(valueSize, white(1f), bold, -0.5f)
        cells.chunked(weights.size).forEachIndexed { row, list ->
            var cx = x
            list.forEachIndexed { i, (l, v) ->
                val cw = width * weights[i]
                val y = top + row * rowH
                c.drawText(l, cx, y + 12f, label())
                fit(c, v, value, cx, y + 20f + valueSize * 0.9f, cw - 8f)
                cx += cw
            }
        }
    }

    private fun mrz(c: Canvas, lines: Pair<String, String>, x: Float, bottom: Float, width: Float) {
        val p = text(12f, white(0.2f), monoBold)
        p.textSize = 12f * width / p.measureText("<".repeat(34))
        c.drawText(lines.second, x, bottom - 2f, p)
        c.drawText(lines.first, x, bottom - 2f - p.textSize * 1.4f, p)
    }

    /** Single line, shrunk to fit [maxWidth]. */
    private fun fit(c: Canvas, s: String, paint: TextPaint, x: Float, baseline: Float, maxWidth: Float) {
        val p = TextPaint(paint)
        val m = p.measureText(s)
        if (m > maxWidth) p.textSize = maxOf(p.textSize * maxWidth / m, p.textSize * 0.55f)
        val out = if (p.measureText(s) > maxWidth) TextUtils.ellipsize(s, p, maxWidth, TextUtils.TruncateAt.END).toString() else s
        c.drawText(out, x, baseline, p)
    }

    private fun wrapped(c: Canvas, s: String, paint: TextPaint, x: Float, top: Float, width: Float, maxLines: Int): Float {
        val layout = StaticLayout.Builder.obtain(s, 0, s.length, paint, width.roundToInt())
            .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(0f, 0.92f).setIncludePad(false).build()
        c.save(); c.translate(x, top); layout.draw(c); c.restore()
        return layout.height.toFloat()
    }

    private fun text(size: Float, color: Int, face: Typeface = Typeface.DEFAULT, tracking: Float = 0f) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = face; letterSpacing = tracking / size }

    private fun white(a: Float): Int = Color.argb((a * 255).roundToInt(), 255, 255, 255)
    private fun argb(v: Long, a: Float = 1f): Int = (v.toInt() and 0x00FFFFFF) or ((a * 255).roundToInt() shl 24)
}
