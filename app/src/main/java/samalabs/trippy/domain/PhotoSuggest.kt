package samalabs.trippy.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A photo in this phone's library. [assetId] is device-local ("media:<MediaStore id>"); [uri] opens it. */
data class LocalPhoto(
    val assetId: String,
    val uri: String,
    val takenAt: LocalDateTime?,
    val lat: Double? = null,
    val lng: Double? = null,
)

data class PhotoSuggestion(val photo: LocalPhoto, val reason: String, val nearPlace: Boolean)

/** Which library photos look like they belong to a trip: taken on its dates, and (when both sides know) near its places. */
object PhotoSuggest {
    /** Farther than this from every place on the trip means "not this trip" (home on the travel day, say). */
    const val NEAR_KM = 250.0
    const val THUMB_MAX = 512
    const val THUMB_MAX_BYTES = 240_000

    /** Local wall-clock window covering the trip's days, both ends included. */
    fun window(start: LocalDateTime?, end: LocalDateTime?): Pair<LocalDateTime, LocalDateTime>? {
        val s = start ?: return null
        val e = end ?: s
        val a = minOf(s, e).toLocalDate(); val b = maxOf(s, e).toLocalDate()
        return a.atStartOfDay() to b.plusDays(1).atStartOfDay()
    }

    fun suggest(
        photos: List<LocalPhoto>,
        start: LocalDateTime?,
        end: LocalDateTime?,
        places: List<LatLng>,
        skip: Set<String> = emptySet(),
    ): List<PhotoSuggestion> {
        val (from, to) = window(start, end) ?: return emptyList()
        return photos.asSequence()
            .filter { it.assetId !in skip }
            .filter { p -> p.takenAt != null && !p.takenAt.isBefore(from) && p.takenAt.isBefore(to) }
            .distinctBy { it.assetId }
            .mapNotNull { p ->
                if (p.lat == null || p.lng == null || places.isEmpty()) return@mapNotNull PhotoSuggestion(p, "Taken during the trip", false)
                val nearest = places.minOf { km(it.lat, it.lng, p.lat, p.lng) }
                if (nearest > NEAR_KM) null else PhotoSuggestion(p, "Taken near a place on this trip", true)
            }
            .sortedBy { it.photo.takenAt }
            .toList()
    }

    fun km(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = Math.toRadians(1.0)
        val dLat = (lat2 - lat1) * r; val dLng = (lng2 - lng1) * r
        val h = sin(dLat / 2).let { it * it } + cos(lat1 * r) * cos(lat2 * r) * sin(dLng / 2).let { it * it }
        return 2 * 6371.0 * asin(min(1.0, sqrt(h)))
    }

    fun assetId(mediaId: Long) = "media:$mediaId"

    /** MediaStore id from an asset id, or null for other kinds. */
    fun mediaId(assetId: String): Long? = assetId.removePrefix("media:").takeIf { assetId.startsWith("media:") }?.toLongOrNull()

    /**
     * Photo Picker URIs end in the MediaStore id for photos on the phone
     * (content://media/picker/0/…/media/1000000034), so the same photo is one asset either way.
     */
    fun assetIdForUri(uri: String): String {
        val last = uri.substringBefore('?').trimEnd('/').substringAfterLast('/')
        return last.toLongOrNull()?.let(::assetId) ?: "uri:" + Integer.toHexString(uri.hashCode())
    }

    private val exif = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    /** EXIF DateTimeOriginal ("2024:05:03 08:15:00") is already the wall clock where the photo was taken. */
    fun exifDate(raw: String?): LocalDateTime? =
        raw?.trim()?.takeIf { it.length >= 19 }?.let { runCatching { LocalDateTime.parse(it.take(19), exif) }.getOrNull() }
            ?.takeIf { it.year > 1971 }

    /** Thumbnail size that fits in [maxSide] keeping the aspect ratio (never upscales). */
    fun fit(w: Int, h: Int, maxSide: Int = THUMB_MAX): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return 0 to 0
        val scale = min(1.0, maxSide.toDouble() / max(w, h))
        return max(1, (w * scale).roundToInt()) to max(1, (h * scale).roundToInt())
    }

    /** Largest power-of-two BitmapFactory sample size that keeps both sides at least [target]. */
    fun sampleSize(w: Int, h: Int, target: Int = THUMB_MAX): Int {
        var s = 1
        while (min(w, h) / (s * 2) >= target) s *= 2
        return s
    }

    /** Days a story photo spans for a gallery header, e.g. "Day 2". */
    fun dayLabel(day: Int?, date: LocalDate?): String? = day?.takeIf { it > 0 }?.let { "Day $it" } ?: date?.let { Fmt.monthDay(it.atStartOfDay()) }
}
