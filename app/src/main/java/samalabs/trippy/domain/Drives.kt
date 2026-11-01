package samalabs.trippy.domain

import samalabs.trippy.data.ApiDrive
import samalabs.trippy.data.ApiRentalCar
import samalabs.trippy.data.DriveStop
import java.time.LocalDate
import java.time.LocalDateTime

/** Everything you drove on one day, the way the trip timeline and stories show it. */
data class DriveDay(
    val date: LocalDate,
    val start: LocalDateTime?,
    val end: LocalDateTime?,
    val miles: Double,
    val minutes: Int,
    val from: String?,
    val to: String?,
    val stops: List<DriveStop>,
    val paths: List<List<List<Double>>>,
    val driveIds: List<Int>,
) {
    val title: String get() = if (!from.isNullOrBlank() && !to.isNullOrBlank() && from != to) "$from → $to" else "Driving day"

    val subtitle: String get() = listOfNotNull(
        if (title != "Driving day") "Driving day" else null,
        if (driveIds.size > 1) "${driveIds.size} drives" else null,
        Drives.hm(minutes),
        if (stops.isNotEmpty()) "${stops.size} stop${if (stops.size == 1) "" else "s"}" else null,
    ).joinToString(" · ")

    val metric: String get() = Drives.milesText(miles)
}

object Drives {
    /** A story/journey "drive" item as a [DriveDay] (server already grouped it). */
    fun fromStory(item: samalabs.trippy.data.StoryItem): DriveDay? {
        if (item.kind != "drive") return null
        val start = Fmt.parse(item.date) ?: return null
        val (from, to) = item.title.split(" → ").let { if (it.size == 2) it[0] to it[1] else null to null }
        return DriveDay(
            date = start.toLocalDate(), start = start, end = Fmt.parse(item.endDate), miles = item.miles ?: 0.0, minutes = 0,
            from = from, to = to, stops = item.stops, paths = item.paths, driveIds = item.driveIds,
        )
    }

    /** Same grouping as the server's driving_days: by local start date, stops include hand-offs between drives. */
    fun days(drives: List<ApiDrive>): List<DriveDay> =
        drives.mapNotNull { d -> (Fmt.parse(d.startTime) ?: return@mapNotNull null).let { it to d } }
            .groupBy { it.first.toLocalDate() }
            .toSortedMap()
            .map { (day, pairs) ->
                val rows = pairs.sortedBy { it.first }.map { it.second }
                val first = rows.first(); val last = rows.last()
                val stops = rows.flatMap { it.stops } + rows.dropLast(1).map { DriveStop(it.endPlace.name, it.endPlace.lat, it.endPlace.lng) }
                DriveDay(
                    date = day, start = Fmt.parse(first.startTime), end = Fmt.parse(last.endTime),
                    miles = rows.sumOf { it.distanceMiles }, minutes = rows.sumOf { it.durationMinutes ?: 0 },
                    from = first.startPlace.name, to = last.endPlace.name,
                    stops = stops.take(30), paths = rows.map { it.path }.filter { it.isNotEmpty() }, driveIds = rows.map { it.id },
                )
            }

    /**
     * Timeline miles not already counted by a rental's odometer (mirrors the server's counted_miles):
     * a drive linked to, or starting inside the dates of, a rental with recorded miles is skipped.
     */
    fun countedMiles(drives: List<ApiDrive>, rentals: List<ApiRentalCar>): Double {
        val withMiles = rentals.filter { (it.milesDriven ?: 0.0) > 0 }
        val covered = withMiles.mapNotNull { r -> r.pickupDate?.let { a -> r.dropoffDate?.let { b -> a to b } } }
        val ids = withMiles.map { it.id }.toSet()
        return drives.filter { d ->
            if (d.rentalId != null && d.rentalId in ids) return@filter false
            val t = Fmt.parse(d.startTime) ?: return@filter true
            covered.none { (a, b) -> !t.isBefore(a) && !t.isAfter(b) }
        }.sumOf { it.distanceMiles }
    }

    fun hm(minutes: Int): String? = when {
        minutes <= 0 -> null
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60}h ${"%02d".format(minutes % 60)}m"
    }

    fun milesText(miles: Double): String = if (miles >= 1) "${Fmt.number(miles)} mi" else "%.1f mi".format(miles)
}

/** Fits lat/lng paths into a w×h box (equirectangular, north up, aspect kept, centered). */
object PathProjection {
    fun fit(paths: List<List<List<Double>>>, w: Float, h: Float, pad: Float): List<List<Pair<Float, Float>>> {
        val pts = paths.flatten().filter { it.size >= 2 }
        if (pts.isEmpty() || w <= 2 * pad || h <= 2 * pad) return emptyList()
        val meanLat = pts.sumOf { it[0] } / pts.size
        val k = kotlin.math.cos(Math.toRadians(meanLat)).coerceAtLeast(0.05)
        val xs = pts.map { it[1] * k }; val ys = pts.map { it[0] }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val spanX = (maxX - minX).coerceAtLeast(1e-6); val spanY = (maxY - minY).coerceAtLeast(1e-6)
        val scale = minOf((w - 2 * pad) / spanX, (h - 2 * pad) / spanY)
        val offX = (w - spanX * scale) / 2; val offY = (h - spanY * scale) / 2
        return paths.map { path ->
            path.filter { it.size >= 2 }.map { p ->
                ((offX + (p[1] * k - minX) * scale).toFloat() to (offY + (maxY - p[0]) * scale).toFloat())
            }
        }.filter { it.isNotEmpty() }
    }
}
