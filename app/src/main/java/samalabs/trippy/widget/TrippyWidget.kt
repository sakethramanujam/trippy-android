package samalabs.trippy.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import samalabs.trippy.MainActivity
import samalabs.trippy.data.ApiTrip
import samalabs.trippy.domain.Fmt
import samalabs.trippy.domain.WidgetSnapshot
import java.time.LocalDate
import java.time.LocalDateTime

/** Next trip + next flight, from the snapshot the app writes (no network in the widget). */
class TrippyWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetStore.read(context)
        provideContent { Content(snap) }
    }

    @Composable
    private fun Content(s: WidgetSnapshot) {
        fun c(color: Color) = ColorProvider(color, color)
        val white = c(Color.White); val grey = c(Color(0xFF9A9AA0)); val coral = c(Color(0xFFFF7A59))
        Column(
            GlanceModifier.fillMaxSize().background(Color(0xFF1C1C1E)).cornerRadius(20.dp).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            val t = s.nextTrip
            if (t == null && s.nextFlight == null) {
                Text("No upcoming trips.", style = TextStyle(color = white, fontSize = 15.sp, fontWeight = FontWeight.Medium))
                Text("Open Trippy to sync.", style = TextStyle(color = grey, fontSize = 12.sp))
                return@Column
            }
            if (t != null) {
                val days = s.daysToTrip(LocalDate.now()) ?: 0
                Text("NEXT TRIP", style = TextStyle(color = grey, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(t.title, style = TextStyle(color = white, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                    Text(if (days <= 0) "Now" else "${days}d", style = TextStyle(color = coral, fontSize = 22.sp, fontWeight = FontWeight.Bold))
                }
                Text(Fmt.parse(t.start)?.let { Fmt.monthDay(it) + (Fmt.parse(t.end)?.let { e -> " – " + Fmt.monthDay(e) } ?: "") }.orEmpty(),
                    style = TextStyle(color = grey, fontSize = 12.sp))
            }
            s.nextFlight?.let { f ->
                if (t != null) Spacer(GlanceModifier.height(10.dp))
                Text("NEXT FLIGHT", style = TextStyle(color = grey, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                Text("${f.from} → ${f.to}", style = TextStyle(color = white, fontSize = 22.sp, fontWeight = FontWeight.Bold))
                val dep = Fmt.parse(f.departs)
                Text(listOfNotNull(f.number.ifBlank { null }, dep?.let { Fmt.day(it) + " · " + Fmt.time(it) }).joinToString(" · "),
                    style = TextStyle(color = white, fontSize = 13.sp))
                dep?.let { d -> if (d.isAfter(LocalDateTime.now())) Text("Departs in " + Fmt.duration(LocalDateTime.now(), d), style = TextStyle(color = coral, fontSize = 12.sp)) }
                f.confirmation?.let { Text("Conf $it", style = TextStyle(color = grey, fontSize = 12.sp)) }
            }
        }
    }
}

class TrippyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TrippyWidget()
}

object WidgetStore {
    private const val PREFS = "trippy.widget"
    private const val KEY = "snapshot"

    fun read(ctx: Context): WidgetSnapshot = WidgetSnapshot.decode(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    suspend fun write(ctx: Context, trips: List<ApiTrip>) = save(ctx, WidgetSnapshot.make(trips, LocalDateTime.now()))

    suspend fun clear(ctx: Context) = save(ctx, WidgetSnapshot())

    private suspend fun save(ctx: Context, s: WidgetSnapshot) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, s.encode()).apply()
        runCatching { TrippyWidget().updateAll(ctx) }
    }

    /** Asks the launcher to place the widget (Android 8+ launchers that support pinning). */
    fun requestPin(ctx: Context): Boolean {
        val m = ctx.getSystemService(AppWidgetManager::class.java) ?: return false
        if (!m.isRequestPinAppWidgetSupported) return false
        return m.requestPinAppWidget(ComponentName(ctx, TrippyWidgetReceiver::class.java), null, null)
    }
}
