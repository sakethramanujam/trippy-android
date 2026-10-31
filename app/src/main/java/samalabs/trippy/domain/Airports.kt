package samalabs.trippy.domain

import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class Airport(val code: String, val city: String, val lat: Double, val lng: Double, val zone: String?)

/** Flights only carry IATA codes; this is the same table the iOS app ships. */
object Airports {
    private val table: Map<String, Airport> = mapOf(
        "ATL" to Airport("ATL", "Atlanta", 33.6407, -84.4277, "America/New_York"),
        "AUS" to Airport("AUS", "Austin", 30.1975, -97.6664, "America/Chicago"),
        "BOS" to Airport("BOS", "Boston", 42.3656, -71.0096, "America/New_York"),
        "BWI" to Airport("BWI", "Baltimore", 39.1774, -76.6684, "America/New_York"),
        "CLT" to Airport("CLT", "Charlotte", 35.2144, -80.9473, "America/New_York"),
        "DCA" to Airport("DCA", "Washington", 38.8512, -77.0402, "America/New_York"),
        "DEN" to Airport("DEN", "Denver", 39.8561, -104.6737, "America/Denver"),
        "DFW" to Airport("DFW", "Dallas", 32.8998, -97.0403, "America/Chicago"),
        "DTW" to Airport("DTW", "Detroit", 42.2162, -83.3554, "America/Detroit"),
        "EWR" to Airport("EWR", "Newark", 40.6895, -74.1745, "America/New_York"),
        "FLL" to Airport("FLL", "Fort Lauderdale", 26.0742, -80.1506, "America/New_York"),
        "HNL" to Airport("HNL", "Honolulu", 21.3245, -157.9251, "Pacific/Honolulu"),
        "IAD" to Airport("IAD", "Washington", 38.9531, -77.4565, "America/New_York"),
        "IAH" to Airport("IAH", "Houston", 29.9902, -95.3368, "America/Chicago"),
        "JFK" to Airport("JFK", "New York", 40.6413, -73.7781, "America/New_York"),
        "LAS" to Airport("LAS", "Las Vegas", 36.0840, -115.1537, "America/Los_Angeles"),
        "LAX" to Airport("LAX", "Los Angeles", 33.9416, -118.4085, "America/Los_Angeles"),
        "LGA" to Airport("LGA", "New York", 40.7769, -73.8740, "America/New_York"),
        "MCO" to Airport("MCO", "Orlando", 28.4312, -81.3081, "America/New_York"),
        "MDW" to Airport("MDW", "Chicago", 41.7868, -87.7522, "America/Chicago"),
        "MIA" to Airport("MIA", "Miami", 25.7959, -80.2870, "America/New_York"),
        "MSP" to Airport("MSP", "Minneapolis", 44.8848, -93.2223, "America/Chicago"),
        "OAK" to Airport("OAK", "Oakland", 37.7126, -122.2197, "America/Los_Angeles"),
        "ORD" to Airport("ORD", "Chicago", 41.9742, -87.9073, "America/Chicago"),
        "PDX" to Airport("PDX", "Portland", 45.5898, -122.5951, "America/Los_Angeles"),
        "PHL" to Airport("PHL", "Philadelphia", 39.8744, -75.2424, "America/New_York"),
        "PHX" to Airport("PHX", "Phoenix", 33.4352, -112.0101, "America/Phoenix"),
        "SAN" to Airport("SAN", "San Diego", 32.7338, -117.1933, "America/Los_Angeles"),
        "SEA" to Airport("SEA", "Seattle", 47.4502, -122.3088, "America/Los_Angeles"),
        "SFO" to Airport("SFO", "San Francisco", 37.6213, -122.3790, "America/Los_Angeles"),
        "SJC" to Airport("SJC", "San Jose", 37.3639, -121.9289, "America/Los_Angeles"),
        "SLC" to Airport("SLC", "Salt Lake City", 40.7899, -111.9791, "America/Denver"),
        "SMF" to Airport("SMF", "Sacramento", 38.6951, -121.5908, "America/Los_Angeles"),
        "YVR" to Airport("YVR", "Vancouver", 49.1967, -123.1815, "America/Vancouver"),
        "YYZ" to Airport("YYZ", "Toronto", 43.6777, -79.6248, "America/Toronto"),
        "MEX" to Airport("MEX", "Mexico City", 19.4361, -99.0719, "America/Mexico_City"),
        "CUN" to Airport("CUN", "Cancún", 21.0365, -86.8771, "America/Cancun"),
        "LHR" to Airport("LHR", "London", 51.4700, -0.4543, "Europe/London"),
        "LGW" to Airport("LGW", "London", 51.1537, -0.1821, "Europe/London"),
        "CDG" to Airport("CDG", "Paris", 49.0097, 2.5479, "Europe/Paris"),
        "AMS" to Airport("AMS", "Amsterdam", 52.3105, 4.7683, "Europe/Amsterdam"),
        "FRA" to Airport("FRA", "Frankfurt", 50.0379, 8.5622, "Europe/Berlin"),
        "MUC" to Airport("MUC", "Munich", 48.3537, 11.7750, "Europe/Berlin"),
        "MAD" to Airport("MAD", "Madrid", 40.4983, -3.5676, "Europe/Madrid"),
        "BCN" to Airport("BCN", "Barcelona", 41.2974, 2.0833, "Europe/Madrid"),
        "FCO" to Airport("FCO", "Rome", 41.8003, 12.2389, "Europe/Rome"),
        "ZRH" to Airport("ZRH", "Zurich", 47.4582, 8.5555, "Europe/Zurich"),
        "DUB" to Airport("DUB", "Dublin", 53.4264, -6.2499, "Europe/Dublin"),
        "LIS" to Airport("LIS", "Lisbon", 38.7742, -9.1342, "Europe/Lisbon"),
        "IST" to Airport("IST", "Istanbul", 41.2753, 28.7519, "Europe/Istanbul"),
        "DXB" to Airport("DXB", "Dubai", 25.2532, 55.3657, "Asia/Dubai"),
        "DOH" to Airport("DOH", "Doha", 25.2731, 51.6081, "Asia/Qatar"),
        "DEL" to Airport("DEL", "Delhi", 28.5562, 77.1000, "Asia/Kolkata"),
        "BOM" to Airport("BOM", "Mumbai", 19.0896, 72.8656, "Asia/Kolkata"),
        "BLR" to Airport("BLR", "Bengaluru", 13.1986, 77.7066, "Asia/Kolkata"),
        "HYD" to Airport("HYD", "Hyderabad", 17.2403, 78.4294, "Asia/Kolkata"),
        "MAA" to Airport("MAA", "Chennai", 12.9941, 80.1709, "Asia/Kolkata"),
        "SIN" to Airport("SIN", "Singapore", 1.3644, 103.9915, "Asia/Singapore"),
        "HKG" to Airport("HKG", "Hong Kong", 22.3080, 113.9185, "Asia/Hong_Kong"),
        "NRT" to Airport("NRT", "Tokyo", 35.7720, 140.3929, "Asia/Tokyo"),
        "HND" to Airport("HND", "Tokyo", 35.5494, 139.7798, "Asia/Tokyo"),
        "KIX" to Airport("KIX", "Osaka", 34.4320, 135.2304, "Asia/Tokyo"),
        "ICN" to Airport("ICN", "Seoul", 37.4602, 126.4407, "Asia/Seoul"),
        "TPE" to Airport("TPE", "Taipei", 25.0797, 121.2342, "Asia/Taipei"),
        "PEK" to Airport("PEK", "Beijing", 40.0799, 116.6031, "Asia/Shanghai"),
        "PVG" to Airport("PVG", "Shanghai", 31.1443, 121.8083, "Asia/Shanghai"),
        "BKK" to Airport("BKK", "Bangkok", 13.6900, 100.7501, "Asia/Bangkok"),
        "SYD" to Airport("SYD", "Sydney", -33.9399, 151.1753, "Australia/Sydney"),
        "MEL" to Airport("MEL", "Melbourne", -37.6690, 144.8410, "Australia/Melbourne"),
        "AKL" to Airport("AKL", "Auckland", -37.0082, 174.7850, "Pacific/Auckland"),
        "GRU" to Airport("GRU", "São Paulo", -23.4356, -46.4731, "America/Sao_Paulo"),
        "EZE" to Airport("EZE", "Buenos Aires", -34.8222, -58.5358, "America/Argentina/Buenos_Aires"),
        "JNB" to Airport("JNB", "Johannesburg", -26.1367, 28.2411, "Africa/Johannesburg"),
        "CPT" to Airport("CPT", "Cape Town", -33.9715, 18.6021, "Africa/Johannesburg"),
        "PIT" to Airport("PIT", "Pittsburgh", 40.4915, -80.2329, "America/New_York"),
        "ANC" to Airport("ANC", "Anchorage", 61.1743, -149.9963, "America/Anchorage"),
        "FCA" to Airport("FCA", "Kalispell", 48.3105, -114.2560, "America/Denver"),
        "CMH" to Airport("CMH", "Columbus", 39.9980, -82.8919, "America/New_York"),
        "BER" to Airport("BER", "Berlin", 52.3667, 13.5033, "Europe/Berlin"),
        "VTZ" to Airport("VTZ", "Visakhapatnam", 17.7212, 83.2245, "Asia/Kolkata"),
    )

    fun get(code: String?): Airport? = code?.let { table[it.trim().uppercase()] }
    fun city(code: String?): String? = get(code)?.city
    fun coordinate(code: String?): LatLng? = get(code)?.let { LatLng(it.lat, it.lng) }
    fun zone(code: String?): ZoneId? = get(code)?.zone?.let { runCatching { ZoneId.of(it) }.getOrNull() }

    /** Zone of the closest known airport (within ~300 mi), else a whole-hour guess from longitude. */
    fun zone(near: LatLng): ZoneId {
        val best = table.values.minByOrNull { miles(near, LatLng(it.lat, it.lng)) }
        if (best != null && miles(near, LatLng(best.lat, best.lng)) < 300) zone(best.code)?.let { return it }
        val hours = (near.lng / 15).roundToInt()
        return java.time.ZoneOffset.ofHours(hours.coerceIn(-12, 14))
    }

    fun miles(a: LatLng, b: LatLng): Double {
        val r = 3958.8
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }
}

data class LatLng(val lat: Double, val lng: Double)
