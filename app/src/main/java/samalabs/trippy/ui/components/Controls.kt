package samalabs.trippy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TSize
import samalabs.trippy.ui.theme.TType

/** Dark rounded card used everywhere (iOS `trippyCard`). */
fun Modifier.trippyCard(radius: Dp = TSize.radius, color: Color = TColor.surface): Modifier =
    this.clip(RoundedCornerShape(radius)).background(color).border(1.dp, TColor.hairline, RoundedCornerShape(radius))

/** Click without the Material ripple, for rows that animate their own press state. */
fun Modifier.plainClick(onClick: () -> Unit): Modifier =
    this.clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onClick)

/** Tap and long-press without a ripple (copy on long-press, open on tap). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.longPlainClick(onClick: () -> Unit, onLong: () -> Unit): Modifier =
    this.combinedClickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onClick, onLongClick = onLong)

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    enabled: Boolean = true,
    color: Color = TColor.blue,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) color else color.copy(alpha = 0.4f))
            .clickable(enabled = enabled && !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
        else Text(text, style = TType.headline, color = Color.White)
    }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = TColor.blue, style: androidx.compose.ui.text.TextStyle = TType.calloutStrong) {
    Text(
        text,
        style = style,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = TType.label, color = TColor.textSecondary, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing.uppercase(), style = TType.label, color = TColor.textTertiary)
    }
}

@Composable
fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    secure: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    mono: Boolean = false,
) {
    var reveal by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label.uppercase(), style = TType.label, color = TColor.textSecondary)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(TColor.surface)
                .border(1.dp, TColor.hairline, RoundedCornerShape(14.dp))
                .padding(start = 14.dp, end = 4.dp)
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val style = (if (mono) TType.mono.copy(fontSize = TType.body.fontSize) else TType.body).copy(color = TColor.textPrimary)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(TColor.blue),
                visualTransformation = if (secure && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (secure) KeyboardType.Password else keyboardType,
                    capitalization = capitalization,
                    autoCorrectEnabled = false,
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) Text(placeholder, style = style, color = TColor.textTertiary)
                        inner()
                    }
                },
            )
            if (secure) {
                IconButton(onClick = { reveal = !reveal }) {
                    Icon(
                        if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (reveal) "Hide password" else "Show password",
                        tint = TColor.textTertiary,
                    )
                }
            } else Spacer(Modifier.width(10.dp))
        }
    }
}

@Composable
fun FormBanner(message: String, modifier: Modifier = Modifier, tint: Color = TColor.red, icon: ImageVector = Icons.Filled.Warning) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Text(message, style = TType.callout, color = TColor.textPrimary)
    }
}

@Composable
fun InfoBanner(message: String, modifier: Modifier = Modifier) =
    FormBanner(message, modifier, tint = TColor.blue, icon = Icons.Filled.Info)

enum class ServerCheck { Checking, Ok, Failed }

/** "● localhost:8000 · Trippy 0.2.0 · open sign-up    Server" */
@Composable
fun ServerStatusRow(host: String, state: ServerCheck, detail: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dot = when (state) {
        ServerCheck.Checking -> TColor.textTertiary
        ServerCheck.Ok -> TColor.green
        ServerCheck.Failed -> TColor.orange
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Column(Modifier.weight(1f)) {
            Text(host, style = TType.mono, color = TColor.textSecondary, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(detail, style = TType.label.copy(letterSpacing = TType.micro.letterSpacing), color = TColor.textTertiary, maxLines = 1)
        }
        Text("Server", style = TType.footnoteStrong, color = TColor.blue)
    }
}

@Composable
fun rememberBusy() = remember { mutableStateOf(false) }
