package samalabs.trippy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import samalabs.trippy.ui.screens.JourneyPalette
import samalabs.trippy.ui.screens.journeyArt

class JourneysTest {
    private val json = """
        {"id":7,"title":"Japan + Korea","trip_ids":[1,2],"trip_count":2,"start_date":"2026-04-10","end_date":"2026-05-07",
         "stats":{"days":28,"flights":4,"nights":26,"miles":13000},"cost_by_currency":{"USD":1200.0,"JPY":0.0,"EUR":300},
         "legs":[
           {"trip_id":1,"title":"Tokyo","start_date":"2026-04-10","end_date":"2026-04-20","leg":1,
            "route":[{"code":"SFO","lat":37.6,"lng":-122.4},{"code":"HND","lat":35.5,"lng":139.8}]},
           {"trip_id":2,"title":"Seoul","start_date":"2026-04-21","leg":2,
            "route":[{"code":"HND","lat":35.5,"lng":139.8},{"code":"ICN","lat":37.5,"lng":126.4},{"city":"Nowhere"}]}
         ]}
    """.trimIndent()

    private val j = TrippyJson.decodeFromString(ApiJourney.serializer(), json)

    @Test fun dateAndCostText() {
        assertEquals("Apr 10 – May 7, 2026", j.dateText)
        assertEquals("$1,200 + €300", j.costText)
        assertNull(j.copy(costByCurrency = emptyMap()).costText)
        assertEquals("No dates", j.copy(startDate = null).dateText)
    }

    @Test fun legSubtitle() {
        assertEquals("Apr 10 – Apr 20  •  SFO → HND", j.legs[0].subtitle())
        assertEquals("Apr 21  •  HND → ICN → Nowhere", j.legs[1].subtitle())
    }

    @Test fun paletteWrapsAndArtJoinsLegs() {
        assertEquals(JourneyPalette.color(1), JourneyPalette.color(7))
        val art = journeyArt(j)
        assertEquals(listOf("SFO", "HND", "ICN"), art.map { it.code })
        assertEquals(JourneyPalette.color(2), art.last().color)
    }

    @Test fun suggestionDecodes() {
        val s = TrippyJson.decodeFromString(SuggestionList.serializer(),
            """{"suggestions":[{"key":"1-2","trip_ids":[1,2],"trips":[{"id":1,"title":"A"}],"title":"A + B","score":0.9,"confidence":"high","reasons":["Back to back"],"journey_id":null,"extra":1}]}""")
        assertEquals(listOf(1, 2), s.suggestions[0].tripIds)
        assertNull(s.suggestions[0].journeyId)
    }
}
