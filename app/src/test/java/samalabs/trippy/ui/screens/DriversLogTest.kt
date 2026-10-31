package samalabs.trippy.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test
import samalabs.trippy.data.ApiRentalCar

class DriversLogTest {
    private fun car(id: Int, pickup: String, code: String?, approx: Boolean = false) =
        ApiRentalCar(id = id, pickupTime = pickup, pickupCode = code, dateApprox = approx)

    @Test fun weekdaysSundayFirstSkippingApproximate() {
        // 2026-05-03 is a Sunday, 2026-05-08 a Friday.
        val c = weekdayCounts(listOf(car(1, "2026-05-03T10:00:00", "SFO"), car(2, "2026-05-08T10:00:00", "SFO"), car(3, "2026-05-08T10:00:00", "LAX", approx = true)))
        assertEquals(listOf(1, 0, 0, 0, 0, 1, 0), c.toList())
    }

    @Test fun stationsByCountThenCode() {
        val s = stationCounts(listOf(car(1, "2026-05-03T10:00:00", "SFO"), car(2, "2026-05-08T10:00:00", "LAX"), car(3, "2026-05-08T10:00:00", "SFO"), car(4, "2026-05-08T10:00:00", "BOS")))
        assertEquals(listOf("SFO" to 2, "BOS" to 1, "LAX" to 1), s)
    }
}
