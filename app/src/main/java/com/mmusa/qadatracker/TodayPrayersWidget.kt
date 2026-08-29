package com.mmusa.qadatracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Widget 1: all 5 of today's prayers, each with a done/not-done toggle. */
class TodayPrayersWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateOne(context, appWidgetManager, it) }
        CountdownTicker.reconcile(context)
    }

    override fun onEnabled(context: Context) { CountdownTicker.reconcile(context) }
    override fun onDisabled(context: Context) { CountdownTicker.reconcile(context) }

    companion object {
        fun updateOne(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_today_layout)
            val colors = WidgetData.getColors(context)
            views.setInt(R.id.widget_root, "setBackgroundColor", colors.card)
            views.setTextColor(R.id.widget_title, colors.accent)
            views.setTextViewText(R.id.widget_title, if (WidgetData.getLang(context) == "en") "Today's prayers" else "صلوات اليوم")

            val cycleDate = WidgetData.getCycleDate(context)
            val times = WidgetData.getPrayerTimes(context)
            val now = System.currentTimeMillis()
            val rowIds = listOf(
                Triple(R.id.row1_name, R.id.row1_time, R.id.row1_toggle),
                Triple(R.id.row2_name, R.id.row2_time, R.id.row2_toggle),
                Triple(R.id.row3_name, R.id.row3_time, R.id.row3_toggle),
                Triple(R.id.row4_name, R.id.row4_time, R.id.row4_toggle),
                Triple(R.id.row5_name, R.id.row5_time, R.id.row5_toggle)
            )

            WidgetData.PRAYER_ORDER.forEachIndexed { i, id ->
                val (nameId, timeId, toggleId) = rowIds[i]
                val pt = times.find { it.id == id }
                views.setTextViewText(nameId, WidgetData.prayerLabel(context, id))
                views.setTextColor(nameId, colors.text)
                views.setTextViewText(timeId, if (pt != null) WidgetData.formatTime(context, pt.start) else "--:--")
                views.setTextColor(timeId, colors.muted)

                val status = if (cycleDate != null && pt != null) WidgetData.statusOf(context, cycleDate, pt, now) else "upcoming"
                views.setTextViewText(toggleId, if (status == "done") "✓" else "○")
                views.setTextColor(toggleId, when (status) {
                    "done" -> colors.done
                    "active" -> colors.active
                    "missed" -> colors.missed
                    else -> colors.muted
                })

                if (cycleDate != null) {
                    val intent = Intent(context, WidgetClickReceiver::class.java).apply {
                        action = WidgetClickReceiver.ACTION_TOGGLE
                        putExtra("cycleDate", cycleDate)
                        putExtra("prayerId", id)
                    }
                    val reqCode = widgetId * 10 + i
                    val pi = PendingIntent.getBroadcast(context, reqCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    views.setOnClickPendingIntent(toggleId, pi)
                }
            }

            mgr.updateAppWidget(widgetId, views)
        }
    }
}
