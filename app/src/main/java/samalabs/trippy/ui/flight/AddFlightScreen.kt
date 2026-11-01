package samalabs.trippy.ui.flight

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import samalabs.trippy.data.ApiClient
import samalabs.trippy.data.ApiException
import samalabs.trippy.data.ApiFlight
import samalabs.trippy.data.ApiTrip
import samalabs.trippy.data.OfflineException
import samalabs.trippy.data.TrippyJson
import samalabs.trippy.domain.Airports
import samalabs.trippy.domain.Fmt
import samalabs.trippy.domain.departureDate
import samalabs.trippy.domain.arrivalDate
import samalabs.trippy.ui.LocalContainer
import samalabs.trippy.ui.components.AuthField
import samalabs.trippy.ui.components.BrandTile
import samalabs.trippy.ui.components.Brands
import samalabs.trippy.ui.components.CircleIcon
import samalabs.trippy.ui.components.FormBanner
import samalabs.trippy.ui.components.MicroLabel
import samalabs.trippy.ui.components.PrimaryButton
import samalabs.trippy.ui.components.SectionLabel
import samalabs.trippy.ui.components.plainClick
import samalabs.trippy.ui.components.trippyCard
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType
import java.net.URLEncoder
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

data class FlightFormDraft(
    val flightNumber: String = "",
    val airline: String = "",
    val date: String = "",
    val fromAirport: String = "",
    val toAirport: String = "",
    val departureTime: String = "09:00",
    val arrivalTime: String = "12:00",
    val nextDayArrival: Boolean = false,
    val confirmationNumber: String = "",
    val seat: String = "",
    val terminal: String = "",
    val gate: String = "",
    val terminalGate: String = "",
    val cabinClass: String = "",
    val notes: String = "",
)

data class FlightValidation(
    val valid: Boolean,
    val error: String? = null,
    val normalizedNumber: String = "",
    val resolvedAirline: String = "",
    val fromCode: String = "",
    val toCode: String = "",
    val departureIso: String = "",
    val arrivalIso: String = "",
    val distanceMiles: Double? = null,
    val durationMinutes: Int? = null,
)

@Serializable
data class FlightCreatePayload(
    val airline: String,
    @SerialName("flight_number") val flightNumber: String,
    @SerialName("departure_airport") val departureAirport: String,
    @SerialName("arrival_airport") val arrivalAirport: String,
    @SerialName("departure_time") val departureTime: String,
    @SerialName("arrival_time") val arrivalTime: String,
    val status: String = "Scheduled",
    @SerialName("confirmation_number") val confirmationNumber: String? = null,
    @SerialName("distance_miles") val distanceMiles: Double? = null,
    @SerialName("duration_minutes") val durationMinutes: Int? = null,
    val seat: String? = null,
    val terminal: String? = null,
    val gate: String? = null,
    @SerialName("cabin_class") val cabinClass: String? = null,
    val notes: String? = null,
    @SerialName("booking_source") val bookingSource: String = "manual",
)

data class FlightLookupPrefill(
    val airline: String? = null,
    val fromAirport: String? = null,
    val toAirport: String? = null,
    val departureTime: String? = null,
    val arrivalTime: String? = null,
    val nextDayArrival: Boolean? = null,
    val terminal: String? = null,
    val gate: String? = null,
    val terminalGate: String? = null,
    val message: String? = null,
)

object FlightFormLogic {
    private val FLIGHT_NUMBER_RE = Regex("^([A-Z0-9]{2}|[A-Z]{3})\\s*0*(\\d{1,4})([A-Z]?)$")
    private val DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE
    private val TIME_FMT = DateTimeFormatter.ofPattern("H:mm", Locale.US)
    private val TIME_OUT_FMT = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val WIRE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    private val CARRIER_NAMES = mapOf(
        "UA" to "United Airlines",
        "DL" to "Delta Air Lines",
        "AA" to "American Airlines",
        "WN" to "Southwest Airlines",
        "AS" to "Alaska Airlines",
        "B6" to "JetBlue",
        "HA" to "Hawaiian Airlines",
        "NK" to "Spirit Airlines",
        "F9" to "Frontier Airlines",
        "G4" to "Allegiant Air",
        "AC" to "Air Canada",
        "WS" to "WestJet",
        "AM" to "Aeromexico",
        "BA" to "British Airways",
        "LH" to "Lufthansa",
        "AF" to "Air France",
        "KL" to "KLM",
        "EK" to "Emirates",
        "QR" to "Qatar Airways",
        "EY" to "Etihad Airways",
        "SQ" to "Singapore Airlines",
        "NH" to "ANA",
        "JL" to "Japan Airlines",
        "QF" to "Qantas",
        "TK" to "Turkish Airlines",
        "IB" to "Iberia",
        "VS" to "Virgin Atlantic",
        "FR" to "Ryanair",
        "U2" to "easyJet",
        "DY" to "Norwegian",
        "W6" to "Wizz Air",
        "AI" to "Air India",
        "6E" to "IndiGo",
        "CX" to "Cathay Pacific",
        "KE" to "Korean Air",
        "BR" to "EVA Air",
        "AY" to "Finnair",
        "SK" to "SAS",
        "EI" to "Aer Lingus",
        "NZ" to "Air New Zealand",
        "LX" to "SWISS",
        "OS" to "Austrian Airlines",
        "FI" to "Icelandair",
        "CM" to "Copa Airlines",
        "AV" to "Avianca",
        "ET" to "Ethiopian Airlines",
        "LO" to "LOT Polish Airlines",
        "SV" to "Saudia",
        "TP" to "TAP Air Portugal",
    )

    /** Default date is the day after the last flight if the trip has flights, else the trip start date, else today. */
    fun defaultDate(trip: ApiTrip?, today: LocalDate = LocalDate.now()): LocalDate {
        if (trip != null) {
            val lastFlightDay = trip.flights.mapNotNull { (it.arrivalDate ?: it.departureDate)?.toLocalDate() }.maxOrNull()
            if (lastFlightDay != null) return lastFlightDay.plusDays(1)
            val start = Fmt.parse(trip.startDate)?.toLocalDate()
            if (start != null) return start
        }
        return today
    }

    /** Default departure airport is the arrival airport of the last flight on the trip if present. */
    fun defaultFromAirport(trip: ApiTrip?): String {
        val last = trip?.flights?.sortedBy { it.departureTime.orEmpty() }?.lastOrNull()
        return last?.arrivalAirport?.trim()?.uppercase().orEmpty()
    }

    fun normalizeFlightNumber(raw: String): String {
        val cleaned = raw.trim().uppercase().replace(Regex("\\s+"), " ")
        val m = FLIGHT_NUMBER_RE.matchEntire(cleaned) ?: return cleaned
        return "${m.groupValues[1]} ${m.groupValues[2]}${m.groupValues[3]}"
    }

    fun carrierPrefix(flightNumber: String): String? {
        val cleaned = flightNumber.trim().uppercase().replace(Regex("\\s+"), " ")
        val m = FLIGHT_NUMBER_RE.matchEntire(cleaned)
        if (m != null) return m.groupValues[1]
        val letters = cleaned.takeWhile { it.isLetterOrDigit() }.take(2)
        return letters.takeIf { it.length == 2 && it.any(Char::isLetter) }
    }

    fun inferAirline(flightNumber: String, explicitAirline: String = ""): String {
        val trimmed = explicitAirline.trim()
        if (trimmed.isNotEmpty()) return trimmed
        val code = carrierPrefix(flightNumber) ?: return ""
        return CARRIER_NAMES[code] ?: code
    }

    fun parseTime(raw: String): LocalTime? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        return runCatching { LocalTime.parse(s, TIME_FMT) }.getOrNull()
    }

    fun validate(draft: FlightFormDraft): FlightValidation {
        val number = normalizeFlightNumber(draft.flightNumber)
        if (number.isBlank()) {
            return FlightValidation(false, "Enter a flight number (e.g. UA 1580).")
        }
        if (!number.any(Char::isDigit)) {
            return FlightValidation(false, "Include the flight number digits (e.g. UA 1580).")
        }
        val airline = inferAirline(number, draft.airline)
        if (airline.isBlank()) {
            return FlightValidation(false, "Enter the airline name.")
        }

        val from = draft.fromAirport.trim().uppercase()
        val to = draft.toAirport.trim().uppercase()
        if (from.length != 3 || !from.all(Char::isLetter)) {
            return FlightValidation(false, "From airport must be a 3-letter IATA code.")
        }
        if (Airports.get(from) == null) {
            return FlightValidation(false, "Unknown IATA code \"$from\".")
        }
        if (to.length != 3 || !to.all(Char::isLetter)) {
            return FlightValidation(false, "To airport must be a 3-letter IATA code.")
        }
        if (Airports.get(to) == null) {
            return FlightValidation(false, "Unknown IATA code \"$to\".")
        }
        if (from == to) {
            return FlightValidation(false, "Departure and arrival airports must be different.")
        }

        val date = runCatching { LocalDate.parse(draft.date.trim(), DATE_FMT) }.getOrNull()
            ?: return FlightValidation(false, "Enter a valid date (YYYY-MM-DD).")
        val depTime = parseTime(draft.departureTime)
            ?: return FlightValidation(false, "Enter departure time as HH:MM (24-hour, e.g. 09:30).")
        val arrTime = parseTime(draft.arrivalTime)
            ?: return FlightValidation(false, "Enter arrival time as HH:MM (24-hour, e.g. 17:45).")

        val depDateTime = LocalDateTime.of(date, depTime)
        val arrDate = if (draft.nextDayArrival) date.plusDays(1) else date
        val arrDateTime = LocalDateTime.of(arrDate, arrTime)

        if (!arrDateTime.isAfter(depDateTime)) {
            return FlightValidation(false, "Arrival must be after departure (turn on +1 day for overnight flights).")
        }

        val fromCoord = Airports.coordinate(from)
        val toCoord = Airports.coordinate(to)
        val miles = if (fromCoord != null && toCoord != null) {
            (Airports.miles(fromCoord, toCoord) * 10.0).roundToInt() / 10.0
        } else null

        val depZone = Airports.zone(from)
        val arrZone = Airports.zone(to)
        val durationMins = if (depZone != null && arrZone != null) {
            val zDep = depDateTime.atZone(depZone)
            val zArr = arrDateTime.atZone(arrZone)
            val diff = Duration.between(zDep, zArr).toMinutes().toInt()
            if (diff > 0) diff else Duration.between(depDateTime, arrDateTime).toMinutes().toInt()
        } else {
            Duration.between(depDateTime, arrDateTime).toMinutes().toInt()
        }

        return FlightValidation(
            valid = true,
            error = null,
            normalizedNumber = number,
            resolvedAirline = airline,
            fromCode = from,
            toCode = to,
            departureIso = depDateTime.format(WIRE_FMT),
            arrivalIso = arrDateTime.format(WIRE_FMT),
            distanceMiles = miles,
            durationMinutes = durationMins.takeIf { it > 0 },
        )
    }

    fun toPayload(draft: FlightFormDraft): FlightCreatePayload? {
        val v = validate(draft)
        if (!v.valid) return null
        return FlightCreatePayload(
            airline = v.resolvedAirline,
            flightNumber = v.normalizedNumber,
            departureAirport = v.fromCode,
            arrivalAirport = v.toCode,
            departureTime = v.departureIso,
            arrivalTime = v.arrivalIso,
            confirmationNumber = draft.confirmationNumber.trim().uppercase().takeIf { it.isNotEmpty() },
            distanceMiles = v.distanceMiles,
            durationMinutes = v.durationMinutes,
            seat = draft.seat.trim().uppercase().takeIf { it.isNotEmpty() },
            terminal = draft.terminal.trim().takeIf { it.isNotEmpty() },
            gate = draft.gate.trim().uppercase().takeIf { it.isNotEmpty() },
            cabinClass = draft.cabinClass.trim().takeIf { it.isNotEmpty() },
            notes = draft.notes.trim().takeIf { it.isNotEmpty() },
        )
    }

    /** Extracts HH:mm local clock and date from provider timestamps ("2026-11-01 11:45-07:00" or ISO). */
    fun extractLocalClockAndDate(raw: String?): Pair<LocalDate, String>? {
        val s = raw?.trim()?.replace(' ', 'T') ?: return null
        runCatching {
            val odt = OffsetDateTime.parse(s)
            return odt.toLocalDate() to odt.toLocalTime().format(TIME_OUT_FMT)
        }
        runCatching {
            val odt = OffsetDateTime.parse(s.replace(Regex("([+-]\\d{2})(\\d{2})$"), "$1:$2"))
            return odt.toLocalDate() to odt.toLocalTime().format(TIME_OUT_FMT)
        }
        runCatching {
            val ldt = LocalDateTime.parse(s.removeSuffix("Z"))
            return ldt.toLocalDate() to ldt.toLocalTime().format(TIME_OUT_FMT)
        }
        return null
    }

    /** Look up flight schedule from the server's flight-status endpoint if configured; never blocks saving. */
    suspend fun lookup(api: ApiClient, flightNumber: String, date: String): FlightLookupPrefill {
        val norm = normalizeFlightNumber(flightNumber)
        if (norm.isBlank() || !norm.any(Char::isDigit)) {
            return FlightLookupPrefill(message = "Enter a flight number like UA 1580 first.")
        }
        val qNum = URLEncoder.encode(norm, "UTF-8")
        val qDate = URLEncoder.encode(date.trim(), "UTF-8")
        return try {
            val body = api.get("/flights/lookup?flight_number=$qNum&date=$qDate")
            parseLookupResponse(body, norm)
        } catch (e: ApiException) {
            FlightLookupPrefill(message = "Couldn't look this up, fill it in manually")
        } catch (e: Exception) {
            FlightLookupPrefill(message = "Couldn't look this up, fill it in manually")
        }
    }

    internal fun parseLookupResponse(body: String, flightNumber: String): FlightLookupPrefill {
        val root = runCatching { TrippyJson.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return FlightLookupPrefill(message = "Couldn't look this up, fill it in manually")
        val configured = root["configured"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true
        val statusObj = (root["status"] as? JsonObject) ?: root
        if (!configured || (root.containsKey("status") && root["status"] !is JsonObject)) {
            return FlightLookupPrefill(message = "Couldn't look this up, fill it in manually")
        }
        fun str(vararg keys: String): String? = keys.firstNotNullOfOrNull { k ->
            statusObj[k]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: root[k]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        }
        val depRaw = str("departure_scheduled", "departure_estimated", "departure_time")
        val arrRaw = str("arrival_scheduled", "arrival_estimated", "arrival_time")
        val from = str("departure_airport", "from_airport", "origin")?.uppercase()
        val to = str("arrival_airport", "to_airport", "destination")?.uppercase()
        val depParsed = extractLocalClockAndDate(depRaw)
        val arrParsed = extractLocalClockAndDate(arrRaw)
        val term = str("departure_terminal", "terminal")
        val gate = str("departure_gate", "gate")
        val termGate = listOfNotNull(term?.let { if (it.all(Char::isDigit)) "T$it" else it }, gate).joinToString(" · ").takeIf { it.isNotBlank() }

        if (depParsed == null && arrParsed == null && from == null && to == null) {
            return FlightLookupPrefill(message = "Couldn't look this up, fill it in manually")
        }
        val nextDay = if (depParsed != null && arrParsed != null) arrParsed.first.isAfter(depParsed.first) else null
        return FlightLookupPrefill(
            airline = str("airline") ?: inferAirline(flightNumber).takeIf { it.isNotBlank() },
            fromAirport = from,
            toAirport = to,
            departureTime = depParsed?.second,
            arrivalTime = arrParsed?.second,
            nextDayArrival = nextDay,
            terminal = term,
            gate = gate,
            terminalGate = termGate,
            message = null,
        )
    }
}

private val CABIN_OPTIONS = listOf(
    "" to "Any",
    "economy" to "Economy",
    "premium_economy" to "Premium Econ",
    "business" to "Business",
    "first" to "First",
)

@Composable
fun AddFlightScreen(
    tripId: Int,
    editingFlight: ApiFlight? = null,
    onBack: () -> Unit,
) {
    val container = LocalContainer.current
    val state by container.trips.state.collectAsState()
    val trip = state.trips.firstOrNull { it.id == tripId }
    val scope = rememberCoroutineScope()

    var flightNumber by rememberSaveable { mutableStateOf(editingFlight?.flightNumber ?: "") }
    var airline by rememberSaveable { mutableStateOf(editingFlight?.airline ?: "") }
    var date by rememberSaveable {
        mutableStateOf(
            editingFlight?.departureDate?.toLocalDate()?.toString()
                ?: FlightFormLogic.defaultDate(trip).toString()
        )
    }
    var fromAirport by rememberSaveable {
        mutableStateOf(editingFlight?.departureAirport ?: FlightFormLogic.defaultFromAirport(trip))
    }
    var toAirport by rememberSaveable { mutableStateOf(editingFlight?.arrivalAirport ?: "") }
    var departureTime by rememberSaveable {
        mutableStateOf(
            editingFlight?.departureDate?.format(DateTimeFormatter.ofPattern("HH:mm", Locale.US)) ?: "09:00"
        )
    }
    var arrivalTime by rememberSaveable {
        mutableStateOf(
            editingFlight?.arrivalDate?.format(DateTimeFormatter.ofPattern("HH:mm", Locale.US)) ?: "12:00"
        )
    }
    var nextDayArrival by rememberSaveable {
        mutableStateOf(
            run {
                val dep = editingFlight?.departureDate
                val arr = editingFlight?.arrivalDate
                if (dep != null && arr != null) arr.toLocalDate().isAfter(dep.toLocalDate()) else false
            }
        )
    }
    var confirmationNumber by rememberSaveable { mutableStateOf(editingFlight?.confirmationNumber ?: "") }
    var seat by rememberSaveable { mutableStateOf(editingFlight?.seat ?: "") }
    var terminal by rememberSaveable { mutableStateOf(editingFlight?.terminal ?: "") }
    var gate by rememberSaveable { mutableStateOf(editingFlight?.gate ?: "") }
    var cabinClass by rememberSaveable { mutableStateOf(editingFlight?.cabinClass ?: "") }
    var notes by rememberSaveable { mutableStateOf(editingFlight?.notes ?: "") }

    var lookingUp by remember { mutableStateOf(false) }
    var lookupNotice by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }

    val draft = FlightFormDraft(
        flightNumber = flightNumber,
        airline = airline,
        date = date,
        fromAirport = fromAirport,
        toAirport = toAirport,
        departureTime = departureTime,
        arrivalTime = arrivalTime,
        nextDayArrival = nextDayArrival,
        confirmationNumber = confirmationNumber,
        seat = seat,
        terminal = terminal,
        gate = gate,
        cabinClass = cabinClass,
        notes = notes,
    )
    val validation = remember(draft) { FlightFormLogic.validate(draft) }
    val resolvedAirline = remember(flightNumber, airline) { FlightFormLogic.inferAirline(flightNumber, airline) }

    fun stepDate(days: Long) {
        val current = runCatching { LocalDate.parse(date.trim()) }.getOrElse { FlightFormLogic.defaultDate(trip) }
        date = current.plusDays(days).toString()
    }

    Box(Modifier.fillMaxSize().background(TColor.canvas).safeDrawingPadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Top bar
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    MicroLabel(trip?.title?.uppercase() ?: "TRIP")
                    Text(if (editingFlight != null) "Edit Flight" else "Add Flight", style = TType.title, color = TColor.textPrimary)
                }
                CircleIcon(Icons.Filled.Close, "Cancel", onClick = onBack, size = 34)
            }

            // Flighty-style live preview card (dark surface, coral accent, huge airport codes)
            FlightPreviewHero(
                airline = resolvedAirline.ifBlank { "Airline" },
                flightNumber = FlightFormLogic.normalizeFlightNumber(flightNumber).ifBlank { "XX 000" },
                fromCode = fromAirport.trim().uppercase().take(3).ifBlank { "FROM" },
                toCode = toAirport.trim().uppercase().take(3).ifBlank { "TO" },
                departureTime = departureTime,
                arrivalTime = arrivalTime,
                nextDayArrival = nextDayArrival,
                distanceMiles = validation.distanceMiles,
                durationMinutes = validation.durationMinutes,
            )

            // Section 1: Flight number, Look up & Date
            Column(
                Modifier.fillMaxWidth().trippyCard().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionLabel("Flight & Date")
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    AuthField(
                        label = "Flight number",
                        value = flightNumber,
                        onValueChange = {
                            flightNumber = it.uppercase()
                            lookupNotice = null
                            submitError = null
                        },
                        placeholder = "e.g. UA 1580",
                        capitalization = KeyboardCapitalization.Characters,
                        mono = true,
                        modifier = Modifier.weight(1f).testTag("input_flight_number"),
                    )
                    Box(
                        Modifier
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(TColor.coral.copy(alpha = 0.16f))
                            .border(1.dp, TColor.coral.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                            .clickable(enabled = !lookingUp) {
                                scope.launch {
                                    lookingUp = true
                                    lookupNotice = null
                                    val res = FlightFormLogic.lookup(container.api, flightNumber, date)
                                    lookingUp = false
                                    if (res.message != null) {
                                        lookupNotice = res.message
                                    } else {
                                        res.airline?.let { if (airline.isBlank()) airline = it }
                                        res.fromAirport?.let { fromAirport = it }
                                        res.toAirport?.let { toAirport = it }
                                        res.departureTime?.let { departureTime = it }
                                        res.arrivalTime?.let { arrivalTime = it }
                                        res.nextDayArrival?.let { nextDayArrival = it }
                                        res.terminal?.let { if (terminal.isBlank()) terminal = it }
                                        res.gate?.let { if (gate.isBlank()) gate = it }
                                        lookupNotice = "Prefilled from live schedule."
                                    }
                                }
                            }
                            .padding(horizontal = 14.dp)
                            .testTag("btn_lookup_flight"),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (lookingUp) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = TColor.coral, strokeWidth = 2.dp)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.Search, contentDescription = null, tint = TColor.coral, modifier = Modifier.size(16.dp))
                                Text("Look up", style = TType.calloutStrong, color = TColor.coral)
                            }
                        }
                    }
                }

                lookupNotice?.let { msg ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(TColor.surfaceRaised)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = TColor.coral, modifier = Modifier.size(16.dp))
                        Text(msg, style = TType.footnote, color = TColor.textSecondary)
                    }
                }

                AuthField(
                    label = "Airline",
                    value = airline,
                    onValueChange = { airline = it; submitError = null },
                    placeholder = resolvedAirline.ifBlank { "e.g. United Airlines" },
                    capitalization = KeyboardCapitalization.Words,
                    modifier = Modifier.fillMaxWidth().testTag("input_airline"),
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    AuthField(
                        label = "Departure date (YYYY-MM-DD)",
                        value = date,
                        onValueChange = { date = it; submitError = null },
                        placeholder = "2026-11-15",
                        mono = true,
                        modifier = Modifier.weight(1f).testTag("input_date"),
                    )
                    StepChip("-1d") { stepDate(-1) }
                    StepChip("+1d") { stepDate(1) }
                }
            }

            // Section 2: Route (IATA codes) & Local Times
            Column(
                Modifier.fillMaxWidth().trippyCard().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionLabel("Route & Local Times")
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AuthField(
                            label = "From (IATA)",
                            value = fromAirport,
                            onValueChange = {
                                fromAirport = it.uppercase().filter(Char::isLetter).take(3)
                                submitError = null
                            },
                            placeholder = "SFO",
                            capitalization = KeyboardCapitalization.Characters,
                            mono = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_from_airport"),
                        )
                        AirportHint(fromAirport)
                    }
                    Box(
                        Modifier
                            .padding(bottom = 20.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(TColor.surfaceRaised)
                            .plainClick {
                                val tmp = fromAirport
                                fromAirport = toAirport
                                toAirport = tmp
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = "Swap airports", tint = TColor.textSecondary)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AuthField(
                            label = "To (IATA)",
                            value = toAirport,
                            onValueChange = {
                                toAirport = it.uppercase().filter(Char::isLetter).take(3)
                                submitError = null
                            },
                            placeholder = "JFK",
                            capitalization = KeyboardCapitalization.Characters,
                            mono = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_to_airport"),
                        )
                        AirportHint(toAirport)
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    AuthField(
                        label = "Departs (local)",
                        value = departureTime,
                        onValueChange = { departureTime = it; submitError = null },
                        placeholder = "09:00",
                        mono = true,
                        modifier = Modifier.weight(1f).testTag("input_dep_time"),
                    )
                    AuthField(
                        label = "Arrives (local)",
                        value = arrivalTime,
                        onValueChange = { arrivalTime = it; submitError = null },
                        placeholder = "17:30",
                        mono = true,
                        modifier = Modifier.weight(1f).testTag("input_arr_time"),
                    )
                }

                // +1 day overnight arrival toggle
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (nextDayArrival) TColor.coral.copy(alpha = 0.16f) else TColor.surfaceRaised)
                        .border(
                            1.dp,
                            if (nextDayArrival) TColor.coral else Color.Transparent,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { nextDayArrival = !nextDayArrival; submitError = null }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("toggle_next_day"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Arrives next day (+1)", style = TType.calloutStrong, color = TColor.textPrimary)
                        Text("Enable for red-eye or long-haul flights landing the next calendar day", style = TType.caption, color = TColor.textSecondary)
                    }
                    StatusPill(if (nextDayArrival) "+1 DAY ON" else "SAME DAY", if (nextDayArrival) TColor.coral else TColor.textTertiary)
                }
            }

            // Section 3: Optional details
            Column(
                Modifier.fillMaxWidth().trippyCard().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionLabel("Booking Details", trailing = "Optional")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AuthField(
                        label = "Confirmation",
                        value = confirmationNumber,
                        onValueChange = { confirmationNumber = it.uppercase() },
                        placeholder = "ABC123",
                        capitalization = KeyboardCapitalization.Characters,
                        mono = true,
                        modifier = Modifier.weight(1f),
                    )
                    AuthField(
                        label = "Seat",
                        value = seat,
                        onValueChange = { seat = it.uppercase() },
                        placeholder = "14A",
                        capitalization = KeyboardCapitalization.Characters,
                        mono = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AuthField(
                        label = "Terminal",
                        value = terminal,
                        onValueChange = { terminal = it },
                        placeholder = "e.g. 3",
                        modifier = Modifier.weight(1f),
                    )
                    AuthField(
                        label = "Gate",
                        value = gate,
                        onValueChange = { gate = it.uppercase() },
                        placeholder = "e.g. F12",
                        capitalization = KeyboardCapitalization.Characters,
                        modifier = Modifier.weight(1f),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CABIN", style = TType.label, color = TColor.textSecondary)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CABIN_OPTIONS.forEach { (key, label) ->
                            val selected = cabinClass == key
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) TColor.coral.copy(alpha = 0.20f) else TColor.surfaceRaised)
                                    .border(1.dp, if (selected) TColor.coral else TColor.hairline, RoundedCornerShape(10.dp))
                                    .clickable { cabinClass = key }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(label, style = TType.footnoteStrong, color = if (selected) TColor.coral else TColor.textSecondary)
                            }
                        }
                    }
                }

                AuthField(
                    label = "Notes",
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = "Baggage allowance, meal, or connection notes",
                    keyboardType = KeyboardType.Text,
                )
            }

            val bannerError = submitError ?: validation.error?.takeIf {
                flightNumber.isNotBlank() || fromAirport.isNotBlank() || toAirport.isNotBlank()
            }
            if (bannerError != null) {
                FormBanner(bannerError)
            }

            PrimaryButton(
                text = if (saving) "Saving…" else if (editingFlight != null) "Save changes" else "Save flight",
                busy = saving,
                enabled = validation.valid && !saving,
                color = TColor.coral,
                modifier = Modifier.testTag("btn_save_flight"),
                onClick = {
                    val payload = FlightFormLogic.toPayload(draft)
                    if (payload == null) {
                        submitError = validation.error
                        return@PrimaryButton
                    }
                    scope.launch {
                        saving = true
                        submitError = null
                        try {
                            val json = TrippyJson.encodeToString(FlightCreatePayload.serializer(), payload)
                            if (editingFlight != null) {
                                container.trips.updateFlight(editingFlight.id, tripId, json)
                            } else {
                                container.trips.addFlight(tripId, json)
                            }
                            saving = false
                            onBack()
                        } catch (e: OfflineException) {
                            saving = false
                            submitError = "You're offline. Connect to your Trippy server to save this flight."
                        } catch (e: Exception) {
                            saving = false
                            submitError = e.message ?: "Couldn't save the flight."
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun FlightPreviewHero(
    airline: String,
    flightNumber: String,
    fromCode: String,
    toCode: String,
    departureTime: String,
    arrivalTime: String,
    nextDayArrival: Boolean,
    distanceMiles: Double?,
    durationMinutes: Int?,
) {
    val previewFlight = remember(airline, flightNumber) {
        ApiFlight(id = 0, airline = airline, flightNumber = flightNumber)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .trippyCard()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandTile(Brands.carrierCode(previewFlight), Brands.flight(previewFlight), size = 34)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(airline, style = TType.headline, color = TColor.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(flightNumber, style = TType.mono, color = TColor.textSecondary, maxLines = 1)
            }
            StatusPill("Scheduled", TColor.coral)
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(fromCode, style = TType.code, color = TColor.textPrimary)
                Text(Airports.city(fromCode) ?: "Departure", style = TType.caption, color = TColor.textSecondary)
                Text(departureTime.ifBlank { "—" }, style = TType.timeSmall, color = TColor.textPrimary, modifier = Modifier.padding(top = 2.dp))
            }
            Column(
                Modifier.weight(1f).padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(TColor.coral))
                    HorizontalDivider(Modifier.weight(1f), color = TColor.divider)
                    Icon(
                        Icons.Filled.Flight,
                        contentDescription = null,
                        tint = TColor.coral,
                        modifier = Modifier.size(20.dp).padding(horizontal = 4.dp),
                    )
                    HorizontalDivider(Modifier.weight(1f), color = TColor.divider)
                    Box(Modifier.size(6.dp).clip(CircleShape).background(TColor.coral))
                }
                val meta = listOfNotNull(
                    durationMinutes?.let { Fmt.duration(it * 60L) },
                    distanceMiles?.let { "${Fmt.number(it)} mi" },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = TType.caption, color = TColor.textSecondary, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(toCode, style = TType.code, color = TColor.textPrimary)
                Text(Airports.city(toCode) ?: "Arrival", style = TType.caption, color = TColor.textSecondary)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text(arrivalTime.ifBlank { "—" }, style = TType.timeSmall, color = TColor.textPrimary)
                    if (nextDayArrival) {
                        Text(" +1", style = TType.footnoteStrong, color = TColor.coral)
                    }
                }
            }
        }
    }
}

@Composable
private fun AirportHint(code: String) {
    val trimmed = code.trim().uppercase()
    val city = Airports.city(trimmed)
    val text = when {
        trimmed.isEmpty() -> "3-letter code"
        city != null -> "$city · ${Airports.get(trimmed)?.zone?.substringAfter('/')?.replace('_', ' ').orEmpty()}"
        trimmed.length == 3 -> "Unknown IATA code"
        else -> "Keep typing…"
    }
    val color = when {
        city != null -> TColor.green
        trimmed.length == 3 -> TColor.red
        else -> TColor.textTertiary
    }
    Text(text, style = TType.caption, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text.uppercase(), style = TType.micro, color = color)
    }
}

@Composable
private fun StepChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(TColor.surfaceRaised)
            .border(1.dp, TColor.hairline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TType.mono, color = TColor.textSecondary)
    }
}
