package samalabs.trippy.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import samalabs.trippy.data.ApiDrive
import samalabs.trippy.data.ApiRentalCar
import samalabs.trippy.data.Fixtures
import samalabs.trippy.data.ImportPreview
import samalabs.trippy.data.StoryItem
import samalabs.trippy.data.TrippyJson
import java.io.StringReader
import java.time.LocalDate
import java.util.Base64

class TimelineSlimTest {
    private fun slim(s: String, limit: Int = MAX_TIMELINE_BYTES) = TimelineSlim.slim(StringReader(s), limit).decodeToString()

    @Test fun dropsRawSignalsAndProfileKeepsSegments() {
        val src = Fixtures.read("google_timeline_on_device_synthetic.json")
        val out = TrippyJson.parseToJsonElement(slim(src)).jsonObject
        val orig = TrippyJson.parseToJsonElement(src).jsonObject
        assertFalse("rawSignals" in out)
        assertFalse("userLocationProfile" in out)
        assertTrue("rawSignals" in orig)
        assertEquals(orig["semanticSegments"], out["semanticSegments"])
    }

    @Test fun iosListAndTakeoutPassThroughUnchanged() {
        for (name in listOf("google_timeline_ios_synthetic.json", "google_semantic_history_synthetic.json")) {
            val src = Fixtures.read(name)
            assertEquals(name, TrippyJson.parseToJsonElement(src), TrippyJson.parseToJsonElement(slim(src)))
        }
        assertTrue(TrippyJson.parseToJsonElement(slim(Fixtures.read("google_timeline_ios_synthetic.json"))) is JsonArray)
    }

    @Test fun handlesTrickyStringsAndKeyOrder() {
        val src = """ { "rawSignals" : [ {"a": "}]\"{,"} , 1 ], "x" : "a , b \" } ]" ,
            "semanticSegments": [ {"t": "2024", "n": -1.5e3, "ok": true, "z": null} ], "userLocationProfile": {"p": [1,2]} } """
        assertEquals("""{"x":"a , b \" } ]","semanticSegments":[{"t":"2024","n":-1.5e3,"ok":true,"z":null}]}""", slim(src))
        assertEquals("""{"semanticSegments":[]}""", slim("""{"rawSignals":[],"semanticSegments":[]}"""))
        assertEquals("""{"é":"ünï"}""", slim("""{ "é" : "ünï" }"""))
    }

    @Test fun rejectsNonJsonAndTooLarge() {
        try { slim("hello"); fail() } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("Timeline")) }
        try { slim("""{"semanticSegments": ["""); fail() } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("ends early")) }
        val big = "{\"semanticSegments\":[" + (1..2000).joinToString(",") { "\"segment-$it\"" } + "]}"
        try { slim(big, limit = 1000); fail() } catch (e: TimelineTooLargeException) { }
        // But it's fine when what's too big is the part we drop.
        val raw = "{\"rawSignals\":[" + (1..2000).joinToString(",") { "\"signal-$it\"" } + "],\"semanticSegments\":[]}"
        assertEquals("{\"semanticSegments\":[]}", slim(raw, limit = 1000))
    }
}

class TimelineImportTest {
    private val preview get() = TrippyJson.decodeFromString(ImportPreview.serializer(), Fixtures.read("import_timeline_preview.json"))

    @Test fun preselectsOnlySuggestedNewDrives() {
        assertEquals(setOf(0, 1), Importing.initialSelection(preview.items))
    }

    @Test fun matchLabels() {
        val items = preview.items
        assertEquals("Trip: Austin weekend", Importing.matchLabel(items[0]))
        assertEquals("Journey: Texas loop", Importing.matchLabel(items[1]))
        assertEquals("Not during a trip", Importing.matchLabel(items[2]))
        assertEquals(1, Importing.stat(preview.stats, "outside_trips"))
        assertEquals(0, Importing.stat(preview.stats, "missing"))
    }

    @Test fun timelineRequestCarriesZoneAndScope() {
        val f = PickedFile("Timeline.json", "{}".toByteArray())
        val r = Importing.previewRequest(ImportSource.GoogleTimeline, "", "", f, timezone = "America/Chicago", drivesScope = DrivesScope.All)!!
        assertEquals("google_timeline", r.source)
        assertEquals("America/Chicago", r.timezone)
        assertEquals("all", r.drivesScope)
        assertEquals("{}", Base64.getDecoder().decode(r.dataBase64).decodeToString())
        val json = TrippyJson.parseToJsonElement(TrippyJson.encodeToString(samalabs.trippy.data.ImportPreviewRequest.serializer(), r)).jsonObject
        assertTrue("drives_scope" in json)
        // Other sources don't send Timeline fields.
        val ics = Importing.previewRequest(ImportSource.Ics, "", "", f, timezone = "America/Chicago")!!
        assertNull(ics.timezone); assertNull(ics.drivesScope)
        assertTrue(ImportSource.GoogleTimeline.file && !ImportSource.GoogleTimeline.paste)
    }
}

class DrivesTest {
    private val drives get() = TrippyJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ApiDrive.serializer()), Fixtures.read("drives.json"))

    @Test fun groupsByDayWithHandOffStops() {
        val days = Drives.days(drives)
        assertEquals(listOf(LocalDate.of(2024, 5, 3), LocalDate.of(2024, 5, 4)), days.map { it.date })
        val d = days[0]
        assertEquals(69.4, d.miles, 0.001)
        assertEquals(95, d.minutes)
        assertEquals("Austin → Waco", d.title)
        assertEquals(listOf("Salado", "Round Rock"), d.stops.map { it.name })
        assertEquals(2, d.paths.size)
        assertEquals(listOf(1, 2), d.driveIds)
        assertEquals("69 mi", d.metric)
        assertEquals("Driving day · 2 drives · 1h 35m · 2 stops", d.subtitle)
        assertEquals("Driving day", days[1].title)
        assertEquals("0.4 mi", days[1].metric)
        assertTrue(days[1].paths.isEmpty())
    }

    @Test fun countedMilesSkipsRentalOdometerDrives() {
        val rental = TrippyJson.decodeFromString(ApiRentalCar.serializer(), """{"id": 4, "pickup_time": "2024-05-04T08:00:00", "dropoff_time": "2024-05-04T18:00:00", "pickup_odometer": 100, "dropoff_odometer": 200}""")
        assertTrue((rental.milesDriven ?: 0.0) > 0)
        // Drive 2 is linked to rental 4 and drive 3 starts inside its dates: only drive 1 counts.
        assertEquals(9.4, Drives.countedMiles(drives, listOf(rental)), 0.001)
        assertEquals(69.8, Drives.countedMiles(drives, emptyList()), 0.001)
        val noMiles = TrippyJson.decodeFromString(ApiRentalCar.serializer(), """{"id": 4}""")
        assertEquals(69.8, Drives.countedMiles(drives, listOf(noMiles)), 0.001)
    }

    @Test fun storyItemBecomesDriveDay() {
        val item = StoryItem(key = "drive:2024-05-03", kind = "drive", date = "2024-05-03T08:15:00", title = "Austin → Waco", miles = 69.4,
            paths = listOf(listOf(listOf(30.0, -97.0), listOf(31.0, -97.5))), driveIds = listOf(1, 2))
        val d = Drives.fromStory(item)!!
        assertEquals("Austin", d.from); assertEquals("Waco", d.to); assertEquals(LocalDate.of(2024, 5, 3), d.date)
        assertNull(Drives.fromStory(item.copy(kind = "flight")))
        assertEquals("Driving day", Drives.fromStory(item.copy(title = "Driving day"))!!.title)
    }

    @Test fun projectionFitsBoxNorthUp() {
        val pts = PathProjection.fit(listOf(listOf(listOf(30.0, -98.0), listOf(31.0, -97.0))), 200f, 100f, 10f)
        val (a, b) = pts[0]
        assertTrue(b.second < a.second) // further north is higher up
        assertTrue(b.first > a.first)   // further east is to the right
        pts.flatten().forEach { (x, y) -> assertTrue(x in 0f..200f && y in 0f..100f) }
        assertTrue(PathProjection.fit(emptyList(), 100f, 100f, 4f).isEmpty())
        assertEquals(1, PathProjection.fit(listOf(listOf(listOf(30.0, -97.0))), 100f, 100f, 4f).size) // one point: centered dot
        assertEquals(Drives.hm(95), "1h 35m"); assertNull(Drives.hm(0))
    }
}
