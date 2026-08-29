package com.mmusa.qadatracker

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

/** Widget 3: live-ish countdown (refreshed every minute) to when the currently
 *  open prayer's window closes and it would count as missed. Before today's
 *  first prayer starts, shows a countdown to that instead. */
class CountdownWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateOne(context, appWidgetManager, it) }
        CountdownTicker.reconcile(context)
    }

    override fun onEnabled(context: Context) { CountdownTicker.reconcile(context) }
    override fun onDisabled(context: Context) { CountdownTicker.reconcile(context) }

    companion object {
        fun updateOne(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_countdown_layout)
            val colors = WidgetData.getColors(context)
            views.setInt(R.id.widget_root, "setBackgroundColor", colors.card)
            val en = WidgetData.getLang(context) == "en"

            val times = WidgetData.getPrayerTimes(context)
            val now = System.currentTimeMillis()
            val current = WidgetData.currentPrayer(times, now)
            val cycleDate = WidgetData.getCycleDate(context)

            if (current != null && current.end > 0) {
                val done = cycleDate != null && WidgetData.isDone(context, cycleDate, current.id)
                val remaining = current.end - now
                if (done) {
                    // Already prayed - current.end is, by construction, also the
                    // start of the next prayer, so the same countdown value now
                    // reads as "time until the next prayer begins" instead.
                    val nextId = WidgetData.nextPrayerIdAfter(current.id)
                    views.setTextViewText(R.id.countdown_prayer_name, "✓ " + WidgetData.prayerLabel(context, current.id))
                    views.setTextColor(R.id.countdown_prayer_name, colors.done)
                    views.setTextViewText(R.id.countdown_time, WidgetData.formatRemaining(context, remaining))
                    views.setTextColor(R.id.countdown_time, colors.muted)
                    views.setTextViewText(R.id.countdown_sub, if (en) "until " + WidgetData.prayerLabel(context, nextId) else "متبقي لدخول وقت " + WidgetData.prayerLabel(context, nextId))
                    views.setTextColor(R.id.countdown_sub, colors.muted)
                } else {
                    views.setTextViewText(R.id.countdown_prayer_name, WidgetData.prayerLabel(context, current.id))
                    views.setTextColor(R.id.countdown_prayer_name, colors.text)
                    views.setTextViewText(R.id.countdown_time, WidgetData.formatRemaining(context, remaining))
                    views.setTextColor(R.id.countdown_time, if (remaining < 15 * 60_000) colors.missed else colors.active)
                    views.setTextViewText(R.id.countdown_sub, if (en) "left to pray" else "متبقي عشان تصليها")
                    views.setTextColor(R.id.countdown_sub, colors.muted)
                }
            } else {
                val next = WidgetData.nextUpcoming(times, now)
                if (next != null) {
                    views.setTextViewText(R.id.countdown_prayer_name, WidgetData.prayerLabel(context, next.id))
                    views.setTextColor(R.id.countdown_prayer_name, colors.text)
                    views.setTextViewText(R.id.countdown_time, WidgetData.formatRemaining(context, next.start - now))
                    views.setTextColor(R.id.countdown_time, colors.muted)
                    views.setTextViewText(R.id.countdown_sub, if (en) "until it begins" else "لحد ما يدخل وقتها")
                    views.setTextColor(R.id.countdown_sub, colors.muted)
                } else {
                    views.setTextViewText(R.id.countdown_prayer_name, if (en) "Open the app" else "افتح التطبيق")
                    views.setTextColor(R.id.countdown_prayer_name, colors.muted)
                    views.setTextViewText(R.id.countdown_time, "--:--")
                    views.setTextColor(R.id.countdown_time, colors.muted)
                    views.setTextViewText(R.id.countdown_sub, "")
                }
            }

            mgr.updateAppWidget(widgetId, views)
        }
    }
}
