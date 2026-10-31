package samalabs.trippy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Palette mirrored from the iOS app's Theme.swift so both clients read as one product. */
object TColor {
    val canvas = Color(0xFF000000)
    val surface = Color(0xFF161618)
    val surfaceRaised = Color(0xFF242426)
    val surfaceHigh = Color(0xFF323235)
    val hairline = Color.White.copy(alpha = 0.08f)
    val divider = Color.White.copy(alpha = 0.11f)
    val textPrimary = Color.White
    val textSecondary = Color(0xFF8E8E93)
    val textTertiary = Color(0xFF5C5C61)
    val blue = Color(0xFF0A84FF)
    val green = Color(0xFF30D158)
    val amber = Color(0xFFFFD60A)
    val orange = Color(0xFFFF9F0A)
    val coral = Color(0xFFFF7A59)
    val red = Color(0xFFFF453A)
    val purple = Color(0xFF5E5CE6)
    val teal = Color(0xFF40C8E0)
    val route = Color(0xFF5EA8FF)

    val stay get() = coral
    val accent get() = coral
}

object TSize {
    val radius = 20.dp
    val radiusSheet = 28.dp
    val radiusSmall = 12.dp
    val page = 20.dp
    val section = 32.dp
    val card = 20.dp
}

/** Typography scale from iOS TType. Rounded faces fall back to the platform sans. */
object TType {
    private val tabular = "tnum"
    val hero = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.6).sp, lineHeight = 58.sp)
    val heroSmall = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp)
    val display = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp, lineHeight = 44.sp)
    val largeTitle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, lineHeight = 40.sp)
    val code = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, lineHeight = 38.sp)
    val codeSmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
    val title = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
    val title2 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp)
    val title3 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 25.sp)
    val headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
    val body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp)
    val callout = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
    val calloutStrong = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
    val footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val footnoteStrong = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val label = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em, lineHeight = 14.sp)
    val micro = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.04.em, lineHeight = 12.sp)
    val number = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = tabular)
    val numberSmall = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = tabular)
    val time = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = tabular)
    val timeSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = tabular)
    val mono = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
    val monoLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    val end = TextStyle(textAlign = TextAlign.End)
}

private val scheme = darkColorScheme(
    primary = TColor.blue,
    onPrimary = Color.White,
    secondary = TColor.coral,
    onSecondary = Color.White,
    tertiary = TColor.purple,
    background = TColor.canvas,
    onBackground = TColor.textPrimary,
    surface = TColor.surface,
    onSurface = TColor.textPrimary,
    surfaceVariant = TColor.surfaceRaised,
    onSurfaceVariant = TColor.textSecondary,
    surfaceContainer = TColor.surface,
    surfaceContainerHigh = TColor.surfaceRaised,
    surfaceContainerHighest = TColor.surfaceHigh,
    surfaceContainerLow = TColor.surface,
    outline = TColor.divider,
    outlineVariant = TColor.hairline,
    error = TColor.red,
)

@Composable
fun TrippyTheme(content: @Composable () -> Unit) {
    // Trippy is dark-only, like the iOS app.
    MaterialTheme(colorScheme = scheme, content = content)
}
