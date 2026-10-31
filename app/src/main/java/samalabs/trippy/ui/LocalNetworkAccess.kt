package samalabs.trippy.ui

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import samalabs.trippy.data.LocalNetwork

/** Whether the app may reach the current server; [epoch] bumps when that changes so screens re-check. */
@Immutable
data class NetworkAccess(val blocked: Boolean = false, val epoch: Int = 0, val request: () -> Unit = {})

val LocalNetworkAccess = compositionLocalOf { NetworkAccess() }

/** Asks for Android 17's local-network permission when the server lives on the LAN. */
@Composable
fun ProvideNetworkAccess(server: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    fun granted() = Build.VERSION.SDK_INT < LocalNetwork.MIN_SDK ||
        ContextCompat.checkSelfPermission(context, LocalNetwork.PERMISSION) == PackageManager.PERMISSION_GRANTED
    var epoch by remember { mutableIntStateOf(0) }
    var isGranted by remember { mutableStateOf(granted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted = it || granted(); epoch++
    }
    val needs = LocalNetwork.needsPermission(server)
    LaunchedEffect(server) {
        isGranted = granted()
        if (needs && !isGranted) launcher.launch(LocalNetwork.PERMISSION)
    }
    val access = NetworkAccess(blocked = needs && !isGranted, epoch = epoch, request = { launcher.launch(LocalNetwork.PERMISSION) })
    CompositionLocalProvider(LocalNetworkAccess provides access, content = content)
}
