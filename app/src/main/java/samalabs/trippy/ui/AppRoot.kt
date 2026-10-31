package samalabs.trippy.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import samalabs.trippy.ui.screens.MainShell
import samalabs.trippy.ui.screens.OnboardingFlow
import samalabs.trippy.ui.theme.TColor

/** Onboarding until there's a session, then the app; rebuilt per account so nothing leaks across. */
@Composable
fun AppRoot() {
    val container = LocalContainer.current
    val session by container.sessions.session.collectAsState()
    LaunchedEffect(Unit) {
        if (container.sessions.session.value == null) container.sessions.load()
        runCatching { container.auth.reloadProfile() }
    }
    Box(Modifier.fillMaxSize().background(TColor.canvas)) {
        val s = session ?: return@Box
        Crossfade(s.signedIn, label = "root") { signedIn ->
            if (signedIn) key(s.scope) { MainShell() } else OnboardingFlow()
        }
    }
}
