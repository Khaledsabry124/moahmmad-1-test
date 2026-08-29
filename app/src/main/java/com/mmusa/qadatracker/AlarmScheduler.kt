package com.mmusa.qadatracker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Schedules/cancels the native alarms that fire prayer-time and
 * heads-up/missed alerts, even while the app/WebView isn't running.
 *
 * There is deliberately only ONE alert alarm per prayer transition (the
 * "upcoming" type) - it fires once, a configurable number of minutes before
 * the next prayer starts, and PrayerAlarmReceiver picks between "prayer
 * approaching" or "you haven't prayed the one before it yet" wording based
 * on whether the previous prayer was logged. An earlier version had a
 * second, separate "reminder" alarm which - because both alarms end up
 * computed relative to the very same next-prayer-start timestamp - could
 * fire at the exact same instant as "upcoming" and produce two
 * notifications back to back. Collapsing to one alarm type removes that
 * duplication entirely rather than trying to de-duplicate after the fact.
 */
object AlarmScheduler {

    private const val REQ_BASE_ATHAN = 1000
    private const val REQ_BASE_UPCOMING = 3000
    private val PRAYER_ORDER = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")

    private fun requestCode(base: Int, prayerId: String): Int {
        return base + PRAYER_ORDER.indexOf(prayerId)
    }

    private fun pendingIntent(
        context: Context, prayerId: String, type: String,
        extras: Map<String, String>, reqCode: Int
    ): PendingIntent {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = "com.mmusa.qadatracker.ALARM_${type}_${prayerId}"
            putExtra("type", type)
            putExtra("prayerId", prayerId)
            extras.forEach { (k, v) -> putExtra(k, v) }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, reqCode, intent, flags)
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        PRAYER_ORDER.forEach { id ->
            am.cancel(pendingIntent(context, id, "athan", emptyMap(), requestCode(REQ_BASE_ATHAN, id)))
            am.cancel(pendingIntent(context, id, "upcoming", emptyMap(), requestCode(REQ_BASE_UPCOMING, id)))
            // Also cancel any stale "reminder" alarms from before this simplification,
            // so nothing left over from an older install keeps firing.
            am.cancel(pendingIntent(context, id, "reminder", emptyMap(), 2000 + PRAYER_ORDER.indexOf(id)))
        }
    }

    private fun scheduleAt(context: Context, timeMillis: Long, pi: PendingIntent) {
        if (timeMillis <= System.currentTimeMillis()) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                // No exact-alarm permission granted - use inexact-but-doze-aware scheduling
                // instead. Prayer alerts don't need to-the-second precision.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pi)
        }
    }

    /**
     * prayerTimes: (id, startMillis, endMillis) for fajr..isha, in order.
     * Cancels any previously scheduled alarms first, so this is always safe
     * to call again (e.g. once per day, or whenever settings/location change).
     *
     * athanMode controls the exact-prayer-time alert ("full" audio via
     * AthanPlaybackService, "notification" only, or "off" - synced from the
     * app, so it fires exactly when the app itself determines the prayer
     * begins, never early or late).
     *
     * alertsEnabled/alertLeadMinutes control the single heads-up/missed
     * alert described above.
     */
    fun scheduleDay(
        context: Context,
        cycleDate: String,
        prayerTimes: List<Triple<String, Long, Long>>,
        athanMode: String,
        alertsEnabled: Boolean,
        alertLeadMinutes: Int
    ) {
        cancelAll(context)

        prayerTimes.forEachIndexed { index, (id, start, end) ->
            if (athanMode != "off") {
                val pi = pendingIntent(context, id, "athan", mapOf("cycleDate" to cycleDate), requestCode(REQ_BASE_ATHAN, id))
                scheduleAt(context, start, pi)
            }
            if (alertsEnabled) {
                val leadMinutes = if (alertLeadMinutes < 1) 1 else alertLeadMinutes
                val alertTime = start - leadMinutes * 60_000L
                val previousId = if (index > 0) prayerTimes[index - 1].first else ""
                val pi = pendingIntent(
                    context, id, "upcoming",
                    mapOf("cycleDate" to cycleDate, "previousPrayerId" to previousId),
                    requestCode(REQ_BASE_UPCOMING, id)
                )
                scheduleAt(context, alertTime, pi)
            }
        }
    }
}
