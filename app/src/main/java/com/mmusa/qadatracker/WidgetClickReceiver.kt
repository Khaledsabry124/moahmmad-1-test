package com.mmusa.qadatracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the "mark prayed / undo" tap from any of the widgets. Flips the
 * done flag in the shared prefs (the same ones PrayerBridge writes) and
 * refreshes every placed widget. The next time the app itself is opened,
 * it reconciles this back into its own tracking via AndroidBridge.getDoneStates
 * (see index.html) - so a change made from a widget while the app is closed
 * is never lost or double-counted.
 */
class WidgetClickReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TOGGLE = "com.mmusa.qadatracker.WIDGET_TOGGLE_DONE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return
        val cycleDate = intent.getStringExtra("cycleDate") ?: return
        val prayerId = intent.getStringExtra("prayerId") ?: return
        val current = WidgetData.isDone(context, cycleDate, prayerId)
        WidgetData.setDone(context, cycleDate, prayerId, !current)
        WidgetData.refreshAllWidgets(context)
    }
}
