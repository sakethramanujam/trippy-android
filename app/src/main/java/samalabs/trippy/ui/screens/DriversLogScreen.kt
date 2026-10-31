package samalabs.trippy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import samalabs.trippy.data.ApiRentalCar
import samalabs.trippy.data.StoryRepository
import samalabs.trippy.data.YearStory
import samalabs.trippy.domain.Fmt
import samalabs.trippy.domain.RentalBrand
import samalabs.trippy.domain.RentalSummary
import samalabs.trippy.domain.dropoffDisplayCity
import samalabs.trippy.domain.isApproximate
import samalabs.trippy.domain.pickupCoordinate
import samalabs.trippy.domain.pickupDate
import samalabs.trippy.domain.pickupDisplayCity
import samalabs.trippy.domain.pickupDisplayCode
import samalabs.trippy.domain.pickupYear
import samalabs.trippy.domain.rentalDays
import samalabs.trippy.domain.rentalDuration
import samalabs.trippy.domain.vehicleName
import samalabs.trippy.ui.LocalContainer
import samalabs.trippy.ui.components.CompanyBadge
import samalabs.trippy.ui.components.plainClick
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

internal val purpleDeep = Color(0xFF2D138F)
internal val purpleNight = Color(0xFF120838)

/** Pickups per weekday, Sunday first (iOS calendar order). Approximate dates are skipped. */
internal fun weekdayCounts(rentals: List<ApiRentalCar>): IntArray {
    val counts = IntArray(7)
    rentals.filter { !it.isApproximate }.forEach { r -> r.pickupDate?.let { counts[it.dayOfWeek.value % 7] += 1 } }
    return counts
}

/** Pickup station codes by count, ties alphabetical. */
internal fun stationCounts(rentals: List<ApiRentalCar>): List<Pair<String, Int>> =
    rentals.filter { it.pickupCoordinate != null || it.pickupCode != null }
        .groupingBy { it.pickupDisplayCode }.eachCount().toList()
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

/** "Driver's Log": purple passport-style recap of rentals, with server stats below (see [statsBlocks]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriversLogScreen(nav: Navigator, statsYears: List<Int> = emptyList(), statsBlocks: @Composable ColumnScope.(Int?) -> Unit = {}, onRefresh: suspend (Int?) -> Unit = {}) {
    val container = LocalContainer.current
    val state by container.trips.state.collectAsState()
    val scope = rememberCoroutineScope()
    var selectedYear by rememberSaveable { mutableStateOf<Int?>(null) }
    val years = remember(state.rentals, statsYears) { (statsYears + state.rentals.mapNotNull { it.pickupYear }).toSet().sortedDescending() }
    val thisYear = LocalDate.now().year
    val cardYear = selectedYear ?: years.firstOrNull { it <= thisYear } ?: thisYear
    val stories = remember { StoryRepository(container.api) }
    var yearCard by remember { mutableStateOf<YearStory?>(null) }
    LaunchedEffect(cardYear) { yearCard = runCatching { stories.year(cardYear) }.getOrNull() }
    val rentals = remember(state.rentals, selectedYear) { selectedYear?.let { y -> state.rentals.filter { it.pickupYear == y } } ?: state.rentals }
    val s = remember(rentals) { RentalSummary(rentals) }

    PullToRefreshBox(
        isRefreshing = state.refreshing && state.loaded,
        onRefresh = { scope.launch { container.trips.refresh(); onRefresh(selectedYear) } },
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(0f to purpleDeep, 0.5f to TColor.canvas)),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Driver's Log", style = TType.largeTitle, color = Color.White, modifier = Modifier.padding(top = 16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                YearChip("All-Time", selectedYear == null) { selectedYear = null }
                years.forEach { y -> YearChip("$y", selectedYear == y) { selectedYear = y } }
            }
            YourYearCard(yearCard, cardYear) { nav.push(ShareCardRoute(null, cardYear)) }
            LogHero(selectedYear, s, rentals)
            if (s.count > 0) {
                CompaniesSection(s)
                WeekdaySection(rentals)
                LongestShortest(rentals)
                TopLocations(rentals)
                if (selectedYear == null && s.byYear.size > 1) ByYearSection(s)
                if (s.topCars.isNotEmpty()) CarsSection(s)
            }
            statsBlocks(selectedYear)
            Spacer(Modifier.height(110.dp))
        }
    }
}

@Composable
private fun YearChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label, style = TType.numberSmall, color = if (selected) Color.White else TColor.textSecondary,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .plainClick(onClick).padding(horizontal = 13.dp, vertical = 8.dp),
    )
}

@Composable
private fun YourYearCard(year: YearStory?, yearNumber: Int, onShare: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF4B1FD1), purpleDeep, purpleNight)))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(22.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("YOUR $yearNumber", style = TType.label, color = Color.White.copy(alpha = 0.7f))
                Text("Year in Travel", style = TType.number, color = Color.White)
            }
            Icon(Icons.Filled.AutoAwesome, null, tint = Color.White.copy(alpha = 0.85f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Mini("${year?.daysAway ?: 0}", "DAYS AWAY")
            Mini(year?.let { Fmt.number(it.miles) } ?: "–", "MILES")
            Mini("${year?.cities ?: 0}", "CITIES")
            Mini("${year?.countries ?: 0}", "COUNTRIES")
        }
        Row(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).plainClick(onShare),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Public, null, tint = purpleDeep, modifier = Modifier.size(18.dp))
            Text("  Passport card", style = TType.calloutStrong, color = purpleDeep)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Mini(value: String, label: String) {
    Column(Modifier.weight(1f)) {
        Text(value, style = TType.number, color = Color.White, maxLines = 1)
        Text(label, style = TType.micro, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Gradient recap card shared by the Driver's Log hero and the server stats cards. */
@Composable
internal fun LogCard(colors: List<Color>, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(colors, start = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f), end = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY)))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), content = content,
    )
}

@Composable
internal fun HeroStat(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label.uppercase(), style = TType.caption, color = Color.White.copy(alpha = 0.7f))
        Text(value, style = TType.codeSmall, color = Color.White, maxLines = 1)
        sub?.let { Text(it, style = TType.caption, color = Color.White.copy(alpha = 0.6f)) }
    }
}

@Composable
internal fun LogFooter(left: String, right: String? = null, chevron: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.1f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(left, style = TType.callout, color = Color.White, modifier = Modifier.weight(1f))
        right?.let { Text(it, style = TType.calloutStrong, color = Color.White, maxLines = 1) }
        if (chevron) Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun LogHero(year: Int?, s: RentalSummary, rentals: List<ApiRentalCar>) {
    val companies = rentals.map { RentalBrand.forCompany(it.company).name }.toSet().size
    val cars = rentals.map { it.vehicleName }.toSet().size
    val longest = rentals.mapNotNull { it.rentalDays }.maxOrNull() ?: 0
    LogCard(listOf(TColor.purple.copy(alpha = 0.85f), purpleDeep, purpleNight)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${year ?: "All-Time"} Driver's Log", style = TType.title2, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.TireRepair, null, tint = Color.White.copy(alpha = 0.8f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Badge, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
                Text(" LICENSE • LICENCIA • PERMIS", style = TType.micro, color = Color.White.copy(alpha = 0.6f))
            }
        }
        Row {
            HeroStat("Rentals", "${s.count}", Modifier.weight(1f), sub = if (longest.toLong() > 0) "Longest $longest days" else null)
            HeroStat("Spent", Fmt.money(s.spend, "USD", decimals = false), Modifier.weight(2f), sub = if (s.days > 0) "${Fmt.money(s.spend / s.days, "USD", decimals = false)}/day" else null)
        }
        Row {
            HeroStat("Days", Fmt.number(s.days), Modifier.weight(1f))
            HeroStat("Companies", "$companies", Modifier.weight(1f))
            HeroStat("Cars", "$cars", Modifier.weight(1f))
        }
        if (s.miles > 0) HeroStat("Distance", Fmt.number(s.miles) + " mi")
        s.byCompany.firstOrNull()?.let { LogFooter("Most rented: ${RentalBrand.forCompany(it.company).name} · ${it.count}×", chevron = true) }
    }
}

@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) {
    Column {
        HorizontalDivider(color = TColor.divider, thickness = 0.5.dp)
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable private fun SectionTitle(t: String) = Text(t, style = TType.title3, color = Color.White)

@Composable
private fun BigCount(n: Int, suffix: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$n", style = TType.code, color = Color.White)
        Text(suffix, style = TType.title, color = TColor.textSecondary, modifier = Modifier.padding(bottom = 3.dp))
    }
}

/** Horizontal bar chart: label, purple bar scaled to the max, count. */
@Composable
private fun HBars(rows: List<Pair<String, Int>>) {
    val max = rows.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (label, n) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = TType.calloutStrong, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(96.dp))
                Box(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.fillMaxWidth(0.88f * n / max).height(20.dp).clip(RoundedCornerShape(4.dp)).background(TColor.purple))
                        Text(" $n", style = TType.timeSmall, color = TColor.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompaniesSection(s: RentalSummary) = Section {
    SectionTitle("Top Rental Companies")
    BigCount(s.byCompany.size, if (s.byCompany.size == 1) "company" else "total companies")
    HBars(s.byCompany.map { RentalBrand.forCompany(it.company).name to it.count })
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        s.byCompany.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompanyBadge(row.company, 26)
                Text(RentalBrand.forCompany(row.company).name, style = TType.calloutStrong, color = Color.White, modifier = Modifier.weight(1f))
                Text("${Fmt.number(row.days)} days", style = TType.timeSmall, color = TColor.textSecondary)
                Text(Fmt.money(row.spend, "USD", decimals = false), style = TType.calloutStrong, color = TColor.green, modifier = Modifier.width(76.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
    }
}

@Composable
private fun WeekdaySection(rentals: List<ApiRentalCar>) = Section {
    val counts = weekdayCounts(rentals)
    val days = (0..6).map { DayOfWeek.of(if (it == 0) 7 else it) }
    val busiest = counts.indices.maxByOrNull { counts[it] } ?: 0
    SectionTitle("Pickups by Weekday")
    if (counts[busiest] > 0) Text("You usually pick up on ${days[busiest].getDisplayName(TextStyle.FULL, Locale.getDefault())}s", style = TType.callout, color = TColor.textSecondary)
    val max = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        days.forEachIndexed { i, d ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (counts[i] > 0) Text("${counts[i]}", style = TType.micro, color = TColor.textTertiary)
                Box(Modifier.fillMaxWidth().fillMaxHeight(0.78f * counts[i] / max).clip(RoundedCornerShape(4.dp))
                    .background(if (i == busiest) TColor.purple else TColor.purple.copy(alpha = 0.45f)))
                Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault()), style = TType.caption, color = TColor.textSecondary, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun LongestShortest(rentals: List<ApiRentalCar>) {
    val timed = rentals.filter { !it.isApproximate && it.rentalDuration != null }.sortedByDescending { it.rentalDuration }
    if (timed.size < 2) return
    Section {
        ExtremeRow("Longest rental", timed.first())
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = TColor.divider, thickness = 0.5.dp)
        ExtremeRow("Shortest rental", timed.last())
    }
}

@Composable
private fun ExtremeRow(title: String, r: ApiRentalCar) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = TType.body, color = Color.White)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CompanyBadge(r.company, 30)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.pickupDisplayCity, style = TType.headline, color = Color.White, maxLines = 1)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.padding(horizontal = 6.dp).size(14.dp))
                    Text(r.dropoffDisplayCity, style = TType.headline, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${r.vehicleName} · ${r.pickupDate?.let { Fmt.monthDayYear(it) }.orEmpty()}", style = TType.footnote, color = TColor.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(r.rentalDuration?.let { Fmt.duration(it) } ?: "—", style = TType.time, color = Color.White)
        }
    }
}

@Composable
private fun TopLocations(rentals: List<ApiRentalCar>) {
    val counts = stationCounts(rentals)
    if (counts.isEmpty()) return
    Section {
        SectionTitle("Top Pickup Locations")
        BigCount(counts.size, "total locations")
        HBars(counts.take(8))
    }
}

@Composable
private fun ByYearSection(s: RentalSummary) = Section {
    SectionTitle("Spend by Year")
    val max = s.byYear.maxOfOrNull { it.spend }?.takeIf { it > 0 } ?: 1.0
    Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        s.byYear.forEach { y ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text("${y.count}", style = TType.label, color = TColor.textSecondary)
                Box(Modifier.fillMaxWidth().fillMaxHeight((0.72f * y.spend / max).toFloat()).clip(RoundedCornerShape(5.dp))
                    .background(Brush.verticalGradient(listOf(TColor.purple, TColor.purple.copy(alpha = 0.6f)))))
                Text("${y.year}", style = TType.caption, color = TColor.textSecondary, modifier = Modifier.padding(top = 4.dp))
                Text(Fmt.compactMoney(y.spend), style = TType.micro, color = TColor.textTertiary)
            }
        }
    }
    Text("Labels show number of rentals", style = TType.caption, color = TColor.textTertiary)
}

@Composable
private fun CarsSection(s: RentalSummary) = Section {
    SectionTitle("Cars You've Driven")
    s.topCars.take(6).forEachIndexed { i, (name, n) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${i + 1}", style = TType.calloutStrong, color = TColor.textTertiary, modifier = Modifier.width(20.dp))
            Text(name, style = TType.body, color = Color.White, modifier = Modifier.weight(1f))
            Text("$n×", style = TType.calloutStrong, color = TColor.textSecondary)
        }
    }
}
