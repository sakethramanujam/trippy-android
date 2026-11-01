package samalabs.trippy.data

import android.content.Context
import android.util.LruCache
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

/** A photo's server copy: a ≤512px JPEG thumbnail. The full photo stays on the device named by [deviceId]. */
@Serializable
data class TripPhoto(
    val id: Int,
    @SerialName("trip_id") val tripId: Int? = null,
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("asset_id") val assetId: String = "",
    @SerialName("taken_at") val takenAt: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("thumb_url") val thumbUrl: String = "",
    /** Story only: trip day (1-based) and the story item it belongs to. */
    val day: Int? = null,
    @SerialName("item_key") val itemKey: String? = null,
)

@Serializable
data class PhotoUpload(
    @SerialName("device_id") val deviceId: String,
    @SerialName("asset_id") val assetId: String,
    @SerialName("taken_at") val takenAt: String?,
    val lat: Double?,
    val lng: Double?,
    @SerialName("thumb_base64") val thumbBase64: String,
)

class PhotosRepository(private val api: ApiClient, ctx: Context) {
    private val prefs = ctx.getSharedPreferences("trippy.photos", Context.MODE_PRIVATE)
    private val thumbs = object : LruCache<Int, ByteArray>(8 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: ByteArray) = value.size
    }

    /** Random per-install id, so the server knows which phone holds each full photo. */
    val deviceId: String
        @Synchronized get() = prefs.getString(KEY_DEVICE, null) ?: ("android-" + UUID.randomUUID()).also { prefs.edit().putString(KEY_DEVICE, it).apply() }

    suspend fun list(tripId: Int): List<TripPhoto> =
        TrippyJson.decodeFromString(ListSerializer(TripPhoto.serializer()), api.get("/trips/$tripId/photos"))

    suspend fun add(tripId: Int, upload: PhotoUpload): TripPhoto =
        TrippyJson.decodeFromString(TripPhoto.serializer(), api.post("/trips/$tripId/photos", TrippyJson.encodeToString(PhotoUpload.serializer(), upload)))

    suspend fun delete(photo: TripPhoto) {
        api.delete("/photos/${photo.id}")
        thumbs.remove(photo.id)
    }

    suspend fun thumb(photo: TripPhoto): ByteArray =
        thumbs.get(photo.id) ?: api.getBytes(photo.thumbUrl.ifBlank { "/photos/${photo.id}/thumb" }).also { thumbs.put(photo.id, it) }

    /** Suggestions you turned down for a trip, so they don't come back. Kept on this phone only. */
    fun rejected(tripId: Int): Set<String> = prefs.getStringSet("rejected.$tripId", emptySet()).orEmpty()

    fun reject(tripId: Int, assetIds: Collection<String>) {
        prefs.edit().putStringSet("rejected.$tripId", rejected(tripId) + assetIds).apply()
    }

    fun clearCache() = thumbs.evictAll()

    private companion object { const val KEY_DEVICE = "device_id" }
}
