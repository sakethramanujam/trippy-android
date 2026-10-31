package samalabs.trippy.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StatsModelsTest {
    @Test fun nestedBlocksWinOverFlatAliases() {
        val s = TrippyJson.decodeFromString(ApiStats.serializer(), """
            {"total_trips":3,"total_expenses":50,"total_flights":9,"total_airports":4,
             "total_expenses_by_currency":{"USD":120.5,"EUR":3},
             "flights":{"count":2,"airports":3,"distance_miles":1234.5,"distance_km":1,"duration_minutes":300,
                        "total_cost_by_currency":{},"top_aircraft":[{"name":"Boeing 737","count":2}],"top_airlines":[]},
             "stays":{"count":1,"nights":4,"cities":1}}""")
        assertEquals(2, s.flightCount); assertEquals(3, s.airportCount); assertEquals(120.5, s.usdExpenses, 0.0)
        assertEquals("Boeing 737", s.logbookAircraft.single().name); assertEquals(4, s.stays?.nights)
    }

    @Test fun flatPayloadAndHours() {
        val s = TrippyJson.decodeFromString(ApiStats.serializer(), """{"total_trips":1,"total_expenses":50,"total_flights":9,"total_airports":4,"top_airlines":[{"name":"Delta","count":1}]}""")
        assertEquals(9, s.flightCount); assertEquals(50.0, s.usdExpenses, 0.0); assertEquals("Delta", s.logbookAirlines.single().name)
        assertEquals("0h", formatFlightHours(0)); assertEquals("5h 20m", formatFlightHours(320)); assertEquals("1d 4h", formatFlightHours(1680))
    }
}
