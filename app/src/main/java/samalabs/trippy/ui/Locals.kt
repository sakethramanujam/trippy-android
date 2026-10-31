package samalabs.trippy.ui

import androidx.compose.runtime.staticCompositionLocalOf
import samalabs.trippy.AppContainer

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
