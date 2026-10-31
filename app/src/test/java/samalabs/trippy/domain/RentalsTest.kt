package samalabs.trippy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import samalabs.trippy.data.ApiRentalCar
import java.time.LocalDateTime

class RentalsTest {
    private val now = LocalDateTime.of(2026, 5, 10, 12, 0)
    private fun car(id: Int = 1, pickup: String? = "2026-05-08T10:00:00", dropoff: String? = "2026-05-12T10:00:00", company: String = "Hertz",
                    model: String? = "Toyota Camry", cost: Double? = 300.0, currency: String? = "USD", approx: Boolean? = null) =
        ApiRentalCar(id = id, company = company, carModel = model, pickupTime = pickup, dropoffTime = dropoff, cost = cost, currency = currency, dateApprox = approx)

    @Test fun phases() {
        assertEquals(RentalPhase.ACTIVE, car().phase(now))
        assertEquals(RentalPhase.UPCOMING, car(pickup = "2026-06-01T09:00:00").phase(now))
        assertEquals(RentalPhase.RETURNED, car(dropoff = "2026-05-09T10:00:00").phase(now))
        assertEquals(RentalPhase.RETURNED, car(approx = true).phase(now))
        assertEquals(RentalPhase.RETURNED, car(pickup = null).phase(now))
        assertEquals(RentalPhase.ACTIVE, car(pickup = "2026-05-09T10:00:00", dropoff = null).phase(now))
        assertEquals(RentalPhase.RETURNED, car(pickup = "2026-05-01T10:00:00", dropoff = null).phase(now))
    }

    @Test fun progressAndCountdown() {
        assertEquals(0.5, car(pickup = "2026-05-10T00:00:00", dropoff = "2026-05-11T00:00:00").progress(now), 1e-9)
        assertEquals(1.0, car(pickup = null).progress(now), 1e-9)
        assertTrue(car(pickup = "2026-05-11T12:00:00").countdown(now).startsWith("Pickup in "))
        assertTrue(car().countdown(now).startsWith("Return in "))
        assertEquals("Return time not set", car(pickup = "2026-05-09T10:00:00", dropoff = null).countdown(now))
        assertEquals("Returned today", car(dropoff = "2026-05-10T08:00:00").countdown(now))
        assertEquals("Returned yesterday", car(pickup = "2026-05-01T10:00:00", dropoff = "2026-05-09T08:00:00").countdown(now))
        assertEquals("Returned 9 days ago", car(pickup = "2026-04-20T10:00:00", dropoff = "2026-05-01T08:00:00").countdown(now))
        assertEquals("Returned Jan 3, 2025", car(pickup = "2025-01-01T10:00:00", dropoff = "2025-01-03T08:00:00").countdown(now))
    }

    @Test fun similarModelAndSubtitle() {
        assertTrue(car(model = "Nissan Versa or Similar").isSimilarModel)
        assertFalse(car().isSimilarModel)
        val r = car(model = "Kia Rio or similar").copy(vehicleYear = 2024, vehicleColor = "DARK GRAY", vehicleClass = null)
        assertEquals("2024 · or similar · Dark Gray", r.vehicleSubtitle)
        assertEquals("", car().copy(vehicleColor = "Per Mfg Spec").vehicleSubtitle)
    }

    @Test fun summary() {
        val list = listOf(
            car(1, "2025-03-01T10:00:00", "2025-03-03T10:00:00", cost = 100.0),
            car(2, "2026-01-01T10:00:00", "2026-01-02T10:00:00", company = "Avis", cost = 50.0, currency = "EUR"),
            car(3, "2026-02-01T10:00:00", "2026-02-02T10:00:00", model = "Kia Rio or similar", cost = 70.0),
        )
        val s = RentalSummary(list)
        assertEquals(3, s.count)
        assertEquals(4.0, s.days, 1e-9)
        assertEquals(170.0, s.spend, 1e-9)
        assertEquals(listOf("Hertz", "Avis"), s.byCompany.map { it.company })
        assertEquals(2, s.byCompany.first().count)
        assertEquals(listOf(2025, 2026), s.byYear.map { it.year })
        assertEquals(1, s.topCars.size)
    }

    @Test fun brandsAndLogoKeys() {
        assertEquals("HZ", RentalBrand.forCompany("Hertz").short)
        assertEquals("EN", RentalBrand.forCompany("Enterprise Rent-A-Car").short)
        assertEquals("ZC", RentalBrand.forCompany("Zoom Cars").short)
        assertEquals("enterprise", LogoKeys.rental("Enterprise Rent-A-Car"))
        assertEquals("alamo", LogoKeys.rental("Alamo Rent A Car"))
        assertEquals("sixt", LogoKeys.rental("SIXT rent a car"))
        assertEquals("national", LogoKeys.rental("National Car Rental"))
        assertEquals("united", LogoKeys.airline("United Airlines").first())
        assertEquals("britishairways", LogoKeys.airline("British Airways").first())
        assertEquals("delta", LogoKeys.airline("Delta Air Lines").first())
        assertTrue("jetblue" in LogoKeys.airline("B6"))
        assertTrue("southwest" in LogoKeys.airline(null, "WN1234"))
        assertEquals("6E", LogoKeys.iata("6E 512"))
        assertEquals(null, LogoKeys.iata("12"))
    }
}
