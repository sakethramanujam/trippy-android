package samalabs.trippy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import samalabs.trippy.ui.AppRoot
import samalabs.trippy.ui.LocalContainer
import samalabs.trippy.ui.theme.TrippyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val container = (application as TrippyApp).container
        setContent {
            CompositionLocalProvider(LocalContainer provides container) {
                TrippyTheme { AppRoot() }
            }
        }
    }
}
