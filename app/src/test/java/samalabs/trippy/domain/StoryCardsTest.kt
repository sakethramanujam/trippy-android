package samalabs.trippy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import samalabs.trippy.data.StoryEditBody
import samalabs.trippy.data.StoryItemEdit
import samalabs.trippy.data.StoryPlace
import samalabs.trippy.data.TripStory
import samalabs.trippy.data.TrippyJson
import samalabs.trippy.data.YearStory
import java.time.LocalDate

class StoryCardsTest {
    private fun story(): TripStory {
        val raw = javaClass.classLoader!!.getResource("fixtures/story.json")!!.readText()
        return TrippyJson.decodeFromString(TripStory.serializer(), raw)
    }

    @Test fun decodesServerStory() {
        val s = story()
        assertEquals("Lisbon & Porto", s.displayTitle)
        assertEquals(StoryTheme.Ocean, StoryTheme.from(s.theme))
        val f = s.items[1]
        assertTrue(f.hidden); assertEquals("Window seat", f.note); assertEquals("LIS", f.place?.code)
        assertEquals(2, s.items[0].people?.size)
        assertEquals("$1,240.50", s.stats.spendText)
        assertEquals(StoryTheme.Indigo, StoryTheme.from("nope"))
    }

    @Test fun dateTextRanges() {
        val d = { s: String -> LocalDate.parse(s) }
        assertEquals("Mar 1 – 8, 2026", StoryText.dateText(d("2026-03-01"), d("2026-03-08")))
        assertEquals("Mar 28 – Apr 4, 2026", StoryText.dateText(d("2026-03-28"), d("2026-04-04")))
        assertEquals("Dec 28, 2025 – Jan 3, 2026", StoryText.dateText(d("2025-12-28"), d("2026-01-03")))
        assertEquals("Mar 1, 2026", StoryText.dateText(d("2026-03-01"), null))
        assertEquals("", StoryText.dateText(null, null))
    }

    @Test fun routeCodesFallBackToCityAndCollapseRepeats() {
        val r = listOf(StoryPlace("sfo"), StoryPlace(null, "Porto"), StoryPlace(null, "porto"), StoryPlace(), StoryPlace("SFO"))
        assertEquals(listOf("SFO", "POR", "SFO"), StoryText.routeCodes(r))
        val (shown, extra) = StoryText.strip(List(9) { "C$it" })
        assertEquals(6, shown.size); assertEquals(3, extra)
    }

    @Test fun mrzLines() {
        assertEquals("SAO<PAULO<RIO", StoryText.mrzClean(" São Paulo & Rio! "))
        val (l1, l2) = StoryText.tripMrz(story())
        assertEquals(34, l1.length); assertEquals(34, l2.length)
        assertEquals("TRP<LISBON<PORTO<<SFO<LIS<POR<<<<<", l1)
        assertEquals("01MAR26<<8D<<11342MI<<TRIPPY<<<<<<", l2)
        val y = YearStory(year = 2026, trips = 5, daysAway = 40, miles = 30123.4, home = StoryPlace("SFO"))
        assertEquals("YIT<2026<<SFO".padEnd(34, '<'), StoryText.yearMrz(y).first)
    }

    @Test fun statCellsRespectRulesAndHideSpend() {
        val cells = StoryText.tripStatCells(story(), hideSpend = false)
        assertEquals(listOf("DAYS", "DISTANCE", "PLACES", "FLIGHTS", "NIGHTS", "COUNTRIES", "TRAVELERS", "SPENT"), cells.map { it.first })
        assertEquals("11.3K mi", cells[1].second)
        assertFalse(StoryText.tripStatCells(story(), hideSpend = true).any { it.first == "SPENT" })
        assertEquals("870", StoryText.compactMiles(870.2))
        assertEquals("9,999", StoryText.compactMiles(9999.0))
        assertEquals("12K", StoryText.compactMiles(12000.0))
        assertEquals("1.2M", StoryText.compactMiles(1_234_000.0))
    }

    @Test fun projectionStaysInsideTheBox() {
        val pts = story().route.map { it.lat!! to it.lng!! }
        val out = StoryText.project(pts, 300f, 200f, 20f)
        out.forEach { (x, y) -> assertTrue(x in 19.9f..280.1f && y in 19.9f..180.1f) }
        assertTrue(out[0].first < out[1].first) // SF is west of Lisbon
        assertTrue(out[2].second < out[1].second) // Porto is north of Lisbon
        assertEquals(150f to 100f, StoryText.project(listOf(10.0 to 10.0), 300f, 200f, 20f)[0])
    }

    @Test fun editBodyShape() {
        val json = TrippyJson.encodeToString(StoryEditBody.serializer(), StoryEditBody(title = "", theme = "rose", order = listOf("a"), items = mapOf("a" to StoryItemEdit(true, null))))
        assertTrue(json.contains("\"order\":[\"a\"]")); assertTrue(json.contains("\"hidden\":true")); assertFalse(json.contains("subtitle"))
    }
}
