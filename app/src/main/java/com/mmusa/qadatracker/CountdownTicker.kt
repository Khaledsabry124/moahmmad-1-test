package com.mmusa.qadatracker

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Widgets only auto-refresh at most every ~30 minutes via the standard
 * updatePeriodMillis mechanism, which is nowhere near enough for a live
 * countdown. This drives a once-a-minute refresh instead, but ONLY while the
 * countdown widget is actually placed on a home screen - reconcile() is
 * called from every refresh path and turns the ticking on/off accordingly,
 * so it never runs pointlessly in the background.
 */
object CountdownTicker {
    private const val REQ_CODE = 9001

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, CountdownTickReceiver::class.java)
        intent.action = "com.mmusa.qadatracker.COUNTDOWN_TICK"
        return PendingIntent.getBroadcast(
            context, REQ_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun hasCountdownWidgets(context: Context): Boolean {
        val mgr = AppWidgetManager.getInstance(context)
        val a = mgr.getAppWidgetIds(ComponentName(context, CountdownWidget::class.java))
        return a.isNotEmpty()
    }

    fun reconcile(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context)
        if (hasCountdownWidgets(context)) {
            am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + 60_000, 60_000, pi)
        } else {
            am.cancel(pi)
        }
    }
}

class CountdownTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, CountdownWidget::class.java))
        if (ids.isNotEmpty()) CountdownWidget().onUpdate(context, mgr, ids)
    }
}
