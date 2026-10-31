package samalabs.trippy.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import samalabs.trippy.domain.TripSnapshot
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType

@Composable
fun CountChip(icon: ImageVector, count: Int, tint: Color = TColor.textSecondary) {
    Row(
        Modifier.border(1.dp, TColor.divider, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(12.dp))
        Text("$count", style = TType.caption, color = TColor.textSecondary)
    }
}

/** Flights, stays, rentals, expenses, people: only the ones the trip has. */
@Composable
fun TripChips(s: TripSnapshot, modifier: Modifier = Modifier) {
    val t = s.trip
    val others = t.participants.size
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (t.flights.isNotEmpty()) CountChip(Icons.Filled.Flight, t.flights.size)
        val stays = t.lodgings.count { it.status?.lowercase() != "cancelled" }
        if (stays > 0) CountChip(Icons.Filled.Hotel, stays)
        if (s.rentals.isNotEmpty()) CountChip(Icons.Filled.DirectionsCar, s.rentals.size)
        if (t.expenses.isNotEmpty()) CountChip(Icons.Filled.Payments, t.expenses.size)
        if (others > 0) CountChip(Icons.Filled.Group, others)
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color = Color.Black, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), style = TType.micro, color = fg,
        modifier = modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun AutoTag() {
    Text(
        "AUTO", style = TType.micro, color = Color(0xFF9D9BFF),
        modifier = Modifier.background(TColor.purple.copy(alpha = 0.22f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
    )
}
