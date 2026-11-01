package samalabs.trippy.ui.photos

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import samalabs.trippy.domain.LocalPhoto
import samalabs.trippy.domain.PhotoSuggest
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64

/** What the app may read from the photo library for suggestions. The Photo Picker needs none of this. */
enum class LibraryAccess { None, Partial, Full }

object PhotoAccess {
    /** Runtime permissions to ask for when the user turns on suggestions. */
    fun request(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.ACCESS_MEDIA_LOCATION)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.ACCESS_MEDIA_LOCATION)
        Build.VERSION.SDK_INT >= 29 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun level(ctx: Context): LibraryAccess {
        fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 33 && has(Manifest.permission.READ_MEDIA_IMAGES) -> LibraryAccess.Full
            // Android 14+: "Select photos" grants only the ones picked (and can be widened later).
            Build.VERSION.SDK_INT >= 34 && has(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> LibraryAccess.Partial
            Build.VERSION.SDK_INT < 33 && has(Manifest.permission.READ_EXTERNAL_STORAGE) -> LibraryAccess.Full
            else -> LibraryAccess.None
        }
    }

    fun location(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
}

/** Reads the phone's photo library. Nothing here leaves the device except the thumbnails the user accepts. */
object PhotoLibrary {
    private const val SCAN_LIMIT = 400

    /** Photos taken in the local [from, to) window (with a day of slack for time zones). */
    /**
     * Photos on this phone taken around [from]..[to]. MediaStore's DATE_TAKEN does most of the work; photos
     * whose EXIF has no time zone (copied from a camera, older phones) have no DATE_TAKEN, so the most recent
     * of those are read and kept when their EXIF date lands in the window.
     */
    suspend fun scan(ctx: Context, from: LocalDateTime, to: LocalDateTime): List<LocalPhoto> = withContext(Dispatchers.IO) {
        if (PhotoAccess.level(ctx) == LibraryAccess.None) return@withContext emptyList()
        val zone = ZoneId.systemDefault()
        val lo = from.minusDays(1).atZone(zone).toInstant().toEpochMilli()
        val hi = to.plusDays(1).atZone(zone).toInstant().toEpochMilli()
        val taken = MediaStore.Images.Media.DATE_TAKEN
        val dated = query(ctx, "$taken >= ? AND $taken < ?", arrayOf(lo.toString(), hi.toString()), "$taken ASC", zone)
        val undated = query(ctx, "$taken IS NULL OR $taken = 0", null, "${MediaStore.Images.Media.DATE_MODIFIED} DESC", zone)
            .filter { p -> p.takenAt != null && !p.takenAt.isBefore(from.minusDays(1)) && p.takenAt.isBefore(to.plusDays(1)) }
        dated + undated
    }

    private fun query(ctx: Context, where: String, args: Array<String>?, order: String, zone: ZoneId): List<LocalPhoto> {
        val out = mutableListOf<LocalPhoto>()
        val proj = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN)
        ctx.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, where, args, order)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            while (c.moveToNext() && out.size < SCAN_LIMIT) {
                val id = c.getLong(idCol)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                val fallback = c.getLong(takenCol).takeIf { it > 0 }?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
                out += describe(ctx, uri, PhotoSuggest.assetId(id), fallback)
            }
        }
        return out
    }

    /** Photos chosen in the system Photo Picker. */
    suspend fun fromPicker(ctx: Context, uris: List<Uri>): List<LocalPhoto> = withContext(Dispatchers.IO) {
        uris.map { describe(ctx, it, PhotoSuggest.assetIdForUri(it.toString()), null) }
    }

    /** Date (EXIF wall clock, else [fallback]) and GPS, reading the unredacted original when allowed. */
    private fun describe(ctx: Context, uri: Uri, assetId: String, fallback: LocalDateTime?): LocalPhoto {
        val exif = readExif(ctx, uri)
        val taken = PhotoSuggest.exifDate(exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
            ?: PhotoSuggest.exifDate(exif?.getAttribute(ExifInterface.TAG_DATETIME)) ?: fallback
        val ll = FloatArray(2).takeIf { exif?.getLatLong(it) == true }?.takeIf { !(it[0] == 0f && it[1] == 0f) }
        return LocalPhoto(assetId, uri.toString(), taken, ll?.get(0)?.toDouble(), ll?.get(1)?.toDouble())
    }

    private fun readExif(ctx: Context, uri: Uri): ExifInterface? {
        val original = if (Build.VERSION.SDK_INT >= 29 && PhotoAccess.location(ctx) && uri.authority == MediaStore.AUTHORITY) {
            runCatching { MediaStore.setRequireOriginal(uri) }.getOrNull()
        } else null
        for (u in listOfNotNull(original, uri)) {
            runCatching { ctx.contentResolver.openInputStream(u)?.use { return ExifInterface(it) } }
        }
        return null
    }

    /** A small bitmap for previews (and the thumbnail upload), rotated upright. */
    suspend fun bitmap(ctx: Context, uri: Uri, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching { return@withContext ctx.contentResolver.loadThumbnail(uri, Size(maxSide, maxSide), null) }
        }
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val opts = BitmapFactory.Options().apply { inSampleSize = PhotoSuggest.sampleSize(bounds.outWidth, bounds.outHeight, maxSide) }
            val raw = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return@runCatching null
            val degrees = readExif(ctx, uri)?.rotationDegrees ?: 0
            if (degrees == 0) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
        }.getOrNull()
    }

    /** The ≤512px JPEG (≤240 KB) the server keeps, as base64. */
    suspend fun thumbnail(ctx: Context, uri: Uri): String? = withContext(Dispatchers.Default) {
        val src = bitmap(ctx, uri, PhotoSuggest.THUMB_MAX) ?: return@withContext null
        val (w, h) = PhotoSuggest.fit(src.width, src.height)
        val scaled = if (w == src.width && h == src.height) src else Bitmap.createScaledBitmap(src, w, h, true)
        var quality = 82
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
            quality -= 12
        } while (bytes.size > PhotoSuggest.THUMB_MAX_BYTES && quality > 20)
        if (bytes.size > PhotoSuggest.THUMB_MAX_BYTES) null else Base64.getEncoder().encodeToString(bytes)
    }

    /** A bigger copy of one of this phone's photos (share-card backgrounds); null when it isn't here or isn't readable. */
    suspend fun large(ctx: Context, assetId: String, maxSide: Int = 1600): Bitmap? {
        val id = PhotoSuggest.mediaId(assetId) ?: return null
        if (PhotoAccess.level(ctx) == LibraryAccess.None) return null
        return bitmap(ctx, ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), maxSide)
    }
}

private val ExifInterface.rotationDegrees: Int
    get() = when (getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
