package samalabs.trippy.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import samalabs.trippy.domain.CalendarDraft
import samalabs.trippy.domain.Links
import samalabs.trippy.ui.components.FactHandler
import samalabs.trippy.ui.components.FactKind

/** Copy, open, dial and Add to Calendar via system intents (no permissions needed). */
object DeviceActions {
    fun copy(ctx: Context, value: String, label: String) {
        val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(ClipData.newPlainText(label, value))
        // Android 13+ shows its own clipboard confirmation; older versions get the iOS-style toast.
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, "$label copied", Toast.LENGTH_SHORT).show()
    }

    fun open(ctx: Context, url: String) = start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    fun dial(ctx: Context, phone: String) { Links.phone(phone)?.let { start(ctx, Intent(Intent.ACTION_DIAL, Uri.parse(it))) } }

    /** Opens the calendar app's new-event screen pre-filled; the user saves it there. */
    fun addToCalendar(ctx: Context, d: CalendarDraft) {
        val i = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, d.title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, d.startMillis())
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, d.endMillis())
        d.location?.let { i.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
        d.notes?.let { i.putExtra(CalendarContract.Events.DESCRIPTION, it) }
        d.zone?.let { i.putExtra(CalendarContract.Events.EVENT_TIMEZONE, it.id) }
        start(ctx, i, "No calendar app found")
    }

    private fun start(ctx: Context, i: Intent, missing: String = "No app can open this") {
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, missing, Toast.LENGTH_SHORT).show()
        }
    }
}

/** Tap: copy a code, open an address in Maps, dial a phone, open a link. Long-press: copy the value. */
@Composable
fun rememberFactHandler(): FactHandler {
    val ctx = LocalContext.current
    return remember(ctx) {
        FactHandler { f, long ->
            when {
                long || f.kind == FactKind.COPY -> DeviceActions.copy(ctx, f.value, f.label)
                f.kind == FactKind.ADDRESS -> DeviceActions.open(ctx, Links.maps(f.value))
                f.kind == FactKind.PHONE -> DeviceActions.dial(ctx, f.value)
                f.kind == FactKind.LINK -> DeviceActions.open(ctx, f.url ?: f.value)
                else -> Unit
            }
        }
    }
}
