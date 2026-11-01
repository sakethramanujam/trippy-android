package samalabs.trippy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import samalabs.trippy.data.TripStory
import samalabs.trippy.data.TrippyJson
import java.time.LocalDateTime

class PhotoSuggestTest {
    private val start = LocalDateTime.of(2024, 5, 3, 9, 0)
    private val end = LocalDateTime.of(2024, 5, 5, 18, 0)
    private val austin = LatLng(30.2672, -97.7431)
    private fun photo(id: Long, at: LocalDateTime?, lat: Double? = null, lng: Double? = null) =
        LocalPhoto(PhotoSuggest.assetId(id), "content://media/external/images/media/$id", at, lat, lng)

    @Test fun suggestsByDateAndPlace() {
        val photos = listOf(
            photo(1, LocalDateTime.of(2024, 5, 3, 7, 0), 30.27, -97.74),     // early on day one, in Austin
            photo(2, LocalDateTime.of(2024, 5, 4, 12, 0)),                   // no GPS: date is enough
            photo(3, LocalDateTime.of(2024, 5, 5, 23, 59), 37.77, -122.42),  // last day but in San Francisco
            photo(4, LocalDateTime.of(2024, 5, 6, 0, 0), 30.27, -97.74),     // the day after
            photo(5, LocalDateTime.of(2024, 5, 2, 23, 0)),                   // the day before
            photo(6, null),
            photo(7, LocalDateTime.of(2024, 5, 4, 8, 0), 30.5, -97.8),       // rejected earlier
            photo(1, LocalDateTime.of(2024, 5, 3, 7, 0), 30.27, -97.74),     // same asset twice
        )
        val s = PhotoSuggest.suggest(photos, start, end, listOf(austin), skip = setOf(PhotoSuggest.assetId(7)))
        assertEquals(listOf("media:1", "media:2"), s.map { it.photo.assetId })
        assertTrue(s[0].nearPlace)
        assertEquals("Taken during the trip", s[1].reason)
        // Without known places, GPS can't rule anything out.
        assertEquals(listOf("media:1", "media:7", "media:2", "media:3"), PhotoSuggest.suggest(photos, start, end, emptyList()).map { it.photo.assetId })
        assertTrue(PhotoSuggest.suggest(photos, null, null, listOf(austin)).isEmpty())
    }

    @Test fun windowCoversWholeDays() {
        val (a, b) = PhotoSuggest.window(start, null)!!
        assertEquals(LocalDateTime.of(2024, 5, 3, 0, 0), a)
        assertEquals(LocalDateTime.of(2024, 5, 4, 0, 0), b)
        assertNull(PhotoSuggest.window(null, end))
    }

    @Test fun assetIdsAndExif() {
        assertEquals("media:1000000034", PhotoSuggest.assetIdForUri("content://media/picker/0/com.android.providers.media.photopicker/media/1000000034"))
        assertEquals("media:42", PhotoSuggest.assetIdForUri("content://media/external/images/media/42"))
        assertTrue(PhotoSuggest.assetIdForUri("content://com.example/doc/abc").startsWith("uri:"))
        assertEquals(42L, PhotoSuggest.mediaId("media:42")); assertNull(PhotoSuggest.mediaId("uri:abc")); assertNull(PhotoSuggest.mediaId("42"))
        assertEquals(LocalDateTime.of(2024, 5, 3, 8, 15), PhotoSuggest.exifDate("2024:05:03 08:15:00"))
        assertNull(PhotoSuggest.exifDate("0000:00:00 00:00:00")); assertNull(PhotoSuggest.exifDate("")); assertNull(PhotoSuggest.exifDate(null))
    }

    @Test fun thumbnailMath() {
        assertEquals(512 to 384, PhotoSuggest.fit(4032, 3024))
        assertEquals(288 to 512, PhotoSuggest.fit(1080, 1920))
        assertEquals(300 to 200, PhotoSuggest.fit(300, 200))
        assertEquals(4, PhotoSuggest.sampleSize(4032, 3024))
        assertEquals(1, PhotoSuggest.sampleSize(600, 600))
        assertEquals(1242.0, PhotoSuggest.km(30.2672, -97.7431, 39.7392, -104.9903), 25.0)
    }

    @Test fun centerCropForCards() {
        assertEquals(CardCrop.Crop(1008, 0, 2016, 3024), CardCrop.centerCrop(4032, 3024, 360f, 540f).let { CardCrop.Crop(it.x, it.y, it.w, it.h) })
        val tall = CardCrop.centerCrop(1000, 4000, 360f, 360f)
        assertEquals(CardCrop.Crop(0, 1500, 1000, 1000), tall)
    }

    @Test fun storyDecodesTripPhotos() {
        val s = TrippyJson.decodeFromString(TripStory.serializer(), """{"trip_id": 7, "trip_photos": [
            {"id": 3, "trip_id": 7, "device_id": "android-x", "asset_id": "media:1", "taken_at": "2024-05-03T07:00:00",
             "width": 512, "height": 384, "thumb_url": "/photos/3/thumb", "day": 1, "item_key": "lodging:2"}]}""")
        val p = s.tripPhotos.single()
        assertEquals("lodging:2", p.itemKey); assertEquals(1, p.day); assertEquals("/photos/3/thumb", p.thumbUrl)
        assertTrue(TrippyJson.decodeFromString(TripStory.serializer(), """{"trip_id": 7}""").tripPhotos.isEmpty())
    }
}
