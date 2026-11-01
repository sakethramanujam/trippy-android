package samalabs.trippy.auto

import samalabs.trippy.data.ApiFlight
import samalabs.trippy.data.ApiLodging
import samalabs.trippy.data.ApiRentalCar
import samalabs.trippy.data.ApiTrip
import samalabs.trippy.domain.arrivalDate
import samalabs.trippy.domain.checkInDate
import samalabs.trippy.domain.checkOutDate
import samalabs.trippy.domain.companyName
import samalabs.trippy.domain.departureDate
import samalabs.trippy.domain.dropoffDate
import samalabs.trippy.domain.dropoffDisplayCode
import samalabs.trippy.domain.pickupDate
import samalabs.trippy.domain.pickupDisplayCode
import samalabs.trippy.domain.place
import samalabs.trippy.domain.Fmt
import java.time.LocalDateTime

sealed interface NextStop {
    val title: String
    val subtitle: String
    val timeDescription: String
    val navigationQuery: String
    val actionLabel: String
    val eventTime: LocalDateTime

    data class Flight(
        val flight: ApiFlight,
        override val eventTime: LocalDateTime,
    ) : NextStop {
        override val title: String
            get() = listOfNotNull(flight.airline, flight.flightNumber).joinToString(" ").ifEmpty { flight.flightNumber ?: "Flight" }
        override val subtitle: String
            get() = "${flight.departureAirport ?: "???"} → ${flight.arrivalAirport ?: "???"}" +
                (flight.confirmationNumber?.let { " · Conf: $it" } ?: "")
        override val timeDescription: String
            get() = "Departs ${Fmt.monthDay(eventTime)}, ${Fmt.time(eventTime)}"
        override val navigationQuery: String
            get() = "${flight.departureAirport ?: ""} Airport".trim()
        override val actionLabel: String
            get() = "Navigate to ${flight.departureAirport ?: "Airport"}"
    }

    data class Stay(
        val lodging: ApiLodging,
        override val eventTime: LocalDateTime,
    ) : NextStop {
        override val title: String
            get() = lodging.name?.takeIf { it.isNotBlank() } ?: "Hotel"
        override val subtitle: String
            get() = listOfNotNull(lodging.place, lodging.confirmationNumber?.let { "Conf: $it" }).joinToString(" · ")
        override val timeDescription: String
            get() = "Check-in ${Fmt.monthDay(eventTime)}, ${Fmt.time(eventTime)}"
        override val navigationQuery: String
            get() = listOfNotNull(lodging.name, lodging.place).joinToString(", ")
        override val actionLabel: String
            get() = "Navigate to hotel"
    }

    data class RentalPickup(
        val rental: ApiRentalCar,
        override val eventTime: LocalDateTime,
    ) : NextStop {
        override val title: String
            get() = rental.companyName
        override val subtitle: String
            get() = "${rental.pickupDisplayCode} · Pick up" + (rental.confirmationNumber?.let { " · Conf: $it" } ?: "")
        override val timeDescription: String
            get() = "Pick up ${Fmt.monthDay(eventTime)}, ${Fmt.time(eventTime)}"
        override val navigationQuery: String
            get() = listOfNotNull(rental.company, rental.pickupDisplayCode, rental.pickupLocation).joinToString(" ")
        override val actionLabel: String
            get() = "Navigate to rental pickup"
    }

    data class RentalDropoff(
        val rental: ApiRentalCar,
        override val eventTime: LocalDateTime,
    ) : NextStop {
        override val title: String
            get() = rental.companyName
        override val subtitle: String
            get() = "${rental.dropoffDisplayCode} · Return" + (rental.confirmationNumber?.let { " · Conf: $it" } ?: "")
        override val timeDescription: String
            get() = "Return ${Fmt.monthDay(eventTime)}, ${Fmt.time(eventTime)}"
        override val navigationQuery: String
            get() = listOfNotNull(rental.company, rental.dropoffDisplayCode, rental.dropoffLocation).joinToString(" ")
        override val actionLabel: String
            get() = "Navigate to rental return"
    }

    companion object {
        fun find(trips: List<ApiTrip>, rentals: List<ApiRentalCar>, now: LocalDateTime = LocalDateTime.now()): NextStop? {
            val candidates = mutableListOf<NextStop>()

            for (trip in trips) {
                for (flight in trip.flights) {
                    val dep = flight.departureDate ?: continue
                    val arr = flight.arrivalDate ?: dep
                    if (!arr.isBefore(now)) {
                        candidates.add(Flight(flight, dep))
                    }
                }
                for (stay in trip.lodgings) {
                    val inDate = stay.checkInDate ?: continue
                    val outDate = stay.checkOutDate ?: inDate
                    if (!outDate.isBefore(now)) {
                        candidates.add(Stay(stay, inDate))
                    }
                }
            }

            val allRentals = (rentals + trips.flatMap { it.rentalCars }).distinctBy { it.id }
            for (rental in allRentals) {
                val pickup = rental.pickupDate
                val dropoff = rental.dropoffDate
                if (pickup != null && !pickup.isBefore(now.minusHours(2))) {
                    candidates.add(RentalPickup(rental, pickup))
                } else if (dropoff != null && !dropoff.isBefore(now)) {
                    candidates.add(RentalDropoff(rental, dropoff))
                }
            }

            return candidates.minByOrNull { it.eventTime }
        }
    }
}
