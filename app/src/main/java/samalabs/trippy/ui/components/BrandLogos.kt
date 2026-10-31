package samalabs.trippy.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import samalabs.trippy.R
import samalabs.trippy.domain.LogoKeys
import samalabs.trippy.domain.RentalBrand
import java.util.concurrent.TimeUnit

/** Bundled brand marks (res/drawable-nodpi, see LOGOS_SOURCES.md), looked up like iOS `BrandLogos`. */
object BrandLogos {
    @DrawableRes
    fun rental(company: String?): Int? = rentals[LogoKeys.rental(company)]

    @DrawableRes
    fun airline(name: String?, flightNumber: String? = null): Int? =
        LogoKeys.airline(name, flightNumber).firstNotNullOfOrNull { airlines[it] }

    private val rentals = mapOf(
        "alamo" to R.drawable.logo_rental_alamo, "avis" to R.drawable.logo_rental_avis,
        "budget" to R.drawable.logo_rental_budget, "enterprise" to R.drawable.logo_rental_enterprise,
        "hertz" to R.drawable.logo_rental_hertz, "national" to R.drawable.logo_rental_national,
        "sixt" to R.drawable.logo_rental_sixt, "thrifty" to R.drawable.logo_rental_thrifty,
    )

    private val airlines = mapOf(
        "aerlingus" to R.drawable.logo_airline_aerlingus, "aeromexico" to R.drawable.logo_airline_aeromexico,
        "airasia" to R.drawable.logo_airline_airasia, "aircanada" to R.drawable.logo_airline_aircanada,
        "airchina" to R.drawable.logo_airline_airchina, "airfrance" to R.drawable.logo_airline_airfrance,
        "airindia" to R.drawable.logo_airline_airindia, "airnz" to R.drawable.logo_airline_airnz,
        "airtransat" to R.drawable.logo_airline_airtransat, "alaska" to R.drawable.logo_airline_alaska,
        "allegiant" to R.drawable.logo_airline_allegiant, "american" to R.drawable.logo_airline_american,
        "ana" to R.drawable.logo_airline_ana, "austrian" to R.drawable.logo_airline_austrian,
        "avianca" to R.drawable.logo_airline_avianca, "britishairways" to R.drawable.logo_airline_britishairways,
        "cathay" to R.drawable.logo_airline_cathay, "chinaeastern" to R.drawable.logo_airline_chinaeastern,
        "chinasouthern" to R.drawable.logo_airline_chinasouthern, "copa" to R.drawable.logo_airline_copa,
        "delta" to R.drawable.logo_airline_delta, "easyjet" to R.drawable.logo_airline_easyjet,
        "emirates" to R.drawable.logo_airline_emirates, "ethiopian" to R.drawable.logo_airline_ethiopian,
        "etihad" to R.drawable.logo_airline_etihad, "eva" to R.drawable.logo_airline_eva,
        "finnair" to R.drawable.logo_airline_finnair, "frontier" to R.drawable.logo_airline_frontier,
        "hawaiian" to R.drawable.logo_airline_hawaiian, "iberia" to R.drawable.logo_airline_iberia,
        "icelandair" to R.drawable.logo_airline_icelandair, "indigo" to R.drawable.logo_airline_indigo,
        "jal" to R.drawable.logo_airline_jal, "jetblue" to R.drawable.logo_airline_jetblue,
        "klm" to R.drawable.logo_airline_klm, "korean" to R.drawable.logo_airline_korean,
        "lot" to R.drawable.logo_airline_lot, "lufthansa" to R.drawable.logo_airline_lufthansa,
        "norwegian" to R.drawable.logo_airline_norwegian, "pegasus" to R.drawable.logo_airline_pegasus,
        "qantas" to R.drawable.logo_airline_qantas, "qatar" to R.drawable.logo_airline_qatar,
        "ryanair" to R.drawable.logo_airline_ryanair, "sas" to R.drawable.logo_airline_sas,
        "saudia" to R.drawable.logo_airline_saudia, "singapore" to R.drawable.logo_airline_singapore,
        "southwest" to R.drawable.logo_airline_southwest, "spirit" to R.drawable.logo_airline_spirit,
        "swiss" to R.drawable.logo_airline_swiss, "turkish" to R.drawable.logo_airline_turkish,
        "united" to R.drawable.logo_airline_united, "virginatlantic" to R.drawable.logo_airline_virginatlantic,
        "westjet" to R.drawable.logo_airline_westjet, "wizzair" to R.drawable.logo_airline_wizzair,
    )
}

/** Rounded-square logo tile; a hairline keeps dark tiles visible on black. */
@Composable
fun LogoTile(@DrawableRes res: Int, size: Int) {
    val shape = RoundedCornerShape((size * 0.26f).dp)
    Image(
        painterResource(res), null, contentScale = ContentScale.Crop,
        modifier = Modifier.size(size.dp).clip(shape).border(0.5.dp, Color.White.copy(alpha = 0.12f), shape),
    )
}

/** Rental company logo, or a gradient letter badge in the brand's colors. */
@Composable
fun CompanyBadge(company: String?, size: Int = 36) {
    BrandLogos.rental(company)?.let { LogoTile(it, size); return }
    val b = RentalBrand.forCompany(company)
    val c = Color(b.color)
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.28f).dp))
            .background(Brush.linearGradient(listOf(c, c.copy(alpha = 0.78f)))),
        Alignment.Center,
    ) {
        Text(b.short, color = Color(b.foreground), fontSize = (size * 0.36f).sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

/** Airline mark, falling back to a two-letter badge. */
@Composable
fun AirlineBadge(airline: String?, flightNumber: String? = null, size: Int = 30) {
    BrandLogos.airline(airline, flightNumber)?.let { LogoTile(it, size); return }
    val words = airline.orEmpty().split(" ").filter { it.isNotBlank() && it.lowercase() !in setOf("air", "airlines", "airways", "lines") }
    val letters = (if (words.size >= 2) words.take(2).joinToString("") { it.take(1) } else words.firstOrNull()?.take(2).orEmpty()).uppercase()
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.26f).dp)).background(Color(0xFF2C2C2E)), Alignment.Center) {
        if (letters.isEmpty()) Icon(Icons.Filled.Flight, null, tint = Color.White, modifier = Modifier.size((size * 0.5f).dp))
        else Text(letters, color = Color.White, fontSize = (size * 0.36f).sp, fontWeight = FontWeight.Black)
    }
}

/** Small loader for remote pictures (rental `photo_url`): OkHttp + BitmapFactory, cached in memory. */
object RemoteImages {
    private val http by lazy { OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build() }
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cached(url: String): Bitmap? = cache.get(url)

    suspend fun load(url: String): Bitmap? = cache.get(url) ?: withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val bytes = res.body.bytes()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 600 && bounds.outHeight / (sample * 2) >= 400) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }.getOrNull()?.also { cache.put(url, it) }
    }

    /** Absolute http(s) URLs as is; server-relative paths against [server]. */
    fun resolve(raw: String?, server: String): String? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        if (s.startsWith("http://") || s.startsWith("https://")) return s
        if (s.startsWith("/")) return server.trimEnd('/') + s
        return null
    }
}

/** A remote picture, showing [placeholder] until (or unless) it loads. */
@Composable
fun RemoteImage(url: String?, modifier: Modifier = Modifier, placeholder: @Composable () -> Unit) {
    var image by remember(url) { mutableStateOf<ImageBitmap?>(url?.let { RemoteImages.cached(it)?.asImageBitmap() }) }
    LaunchedEffect(url) { if (url != null && image == null) image = RemoteImages.load(url)?.asImageBitmap() }
    Box(modifier, Alignment.Center) {
        image?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) } ?: placeholder()
    }
}
