package samalabs.trippy.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import samalabs.trippy.MainActivity
import samalabs.trippy.R
import samalabs.trippy.data.ApiTrip
import samalabs.trippy.domain.Reminders
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Local trip reminders via WorkManager one-shot jobs (no push, no exact-alarm permission).
 * WorkManager may run a job a few minutes late under Doze; that's the trade for not asking for SCHEDULE_EXACT_ALARM.
 */
class TripReminders(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("trippy.reminders", Context.MODE_PRIVATE)
    private val work get() = WorkManager.getInstance(ctx)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY, false)
        private set(v) { prefs.edit().putBoolean(KEY, v).apply() }

    val permitted: Boolean
        get() = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun enable(trips: List<ApiTrip>): Int { enabled = true; return reschedule(trips) }

    fun disable() { enabled = false; cancelAll() }

    /** Signed out: drop pending reminders but keep the user's choice. */
    fun cancelAll() { work.cancelAllWorkByTag(TAG) }

    /** Replace every pending reminder with a fresh plan. Returns how many are scheduled. */
    fun reschedule(trips: List<ApiTrip>, now: Instant = Instant.now()): Int {
        if (!enabled) return 0
        work.cancelAllWorkByTag(TAG)
        val plan = Reminders.plan(trips, now)
        for (p in plan) {
            val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(Duration.between(now, p.at).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("id" to p.id, "title" to p.title, "body" to p.body))
                .addTag(TAG)
                .build()
            work.enqueueUniqueWork(p.id, ExistingWorkPolicy.REPLACE, req)
        }
        return plan.size
    }

    companion object {
        const val TAG = "trippy-reminder"
        const val CHANNEL = "trip-reminders"
        private const val KEY = "enabled"

        fun createChannel(ctx: Context) {
            val ch = NotificationChannel(CHANNEL, "Trip reminders", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Check-in opening, leaving for the airport and hotel check-in." }
            ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }

        fun post(ctx: Context, id: String, title: String, body: String) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val open = PendingIntent.getActivity(ctx, id.hashCode(), Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title).setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setColor(0xFFFF7A59.toInt())
                .setContentIntent(open).setAutoCancel(true)
                .build()
            try { NotificationManagerCompat.from(ctx).notify(id, 1, n) } catch (_: SecurityException) {}
        }
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val d = inputData
        TripReminders.post(applicationContext, d.getString("id") ?: return Result.success(), d.getString("title").orEmpty(), d.getString("body").orEmpty())
        return Result.success()
    }
}
