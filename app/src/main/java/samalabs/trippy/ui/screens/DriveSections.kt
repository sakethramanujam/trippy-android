package samalabs.trippy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import samalabs.trippy.data.ApiDrive
import samalabs.trippy.data.StoryItem
import samalabs.trippy.domain.DriveDay
import samalabs.trippy.domain.Drives
import samalabs.trippy.ui.LocalContainer
import samalabs.trippy.ui.components.DriveDayCard
import samalabs.trippy.ui.components.SectionLabel

/** Timeline drives on a trip, one card per driving day. Hidden when there are none (or the server predates drives). */
@Composable
fun TripDrivesSection(tripId: Int, refreshKey: Any?) {
    val c = LocalContainer.current
    var drives by remember(tripId) { mutableStateOf<List<ApiDrive>>(emptyList()) }
    LaunchedEffect(tripId, refreshKey) { runCatching { c.drives.forTrip(tripId) }.onSuccess { drives = it } }
    DriveDaysList(drives)
}

/** The journey detail already carries its driving days (journey.drives). */
@Composable
fun JourneyDrivesSection(items: List<StoryItem>) {
    val days = remember(items) { items.mapNotNull { Drives.fromStory(it) } }
    DriveDaysCards(days)
}

@Composable
private fun DriveDaysList(drives: List<ApiDrive>) {
    val days = remember(drives) { Drives.days(drives) }
    DriveDaysCards(days)
}

@Composable
private fun DriveDaysCards(days: List<DriveDay>) {
    if (days.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Driving days", trailing = Drives.milesText(days.sumOf { it.miles }))
        days.forEach { DriveDayCard(it) }
    }
}
