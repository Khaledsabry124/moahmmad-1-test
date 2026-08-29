package com.mmusa.qadatracker

import android.content.Context
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bridge exposed to the WebView as `AndroidBridge`. The web app calls these
 * whenever settings change or prayer times are (re)computed, so native
 * alarms/notifications keep working even when the app isn't open.
 * All prayer-time math stays in JS (single source of truth); this class
 * only stores what native code needs and schedules alarms from it.
 */
class PrayerBridge(private val context: Context) {

    private fun prefs() = context.getSharedPreferences("qada_native_prefs", Context.MODE_PRIVATE)

    @JavascriptInterface
    fun saveSettings(json: String) {
        try {
            val obj = JSONObject(json)
            prefs().edit()
                .putString("athanMode", obj.optString("athanMode", "notification"))
                .putBoolean("alertsEnabled", obj.optBoolean("alertsEnabled", false))
                .putInt("alertLeadMinutes", obj.optInt("alertLeadMinutes", 20))
                .putString("reminderStyle", obj.optString("reminderStyle", "plain"))
                .putBoolean("vibrationEnabled", obj.optBoolean("vibrationEnabled", true))
                .putBoolean("flashEnabled", obj.optBoolean("flashEnabled", false))
                .putString("lang", obj.optString("lang", "ar"))
                .putString("theme", obj.optString("theme", "dark"))
                .apply()
            WidgetData.refreshAllWidgets(context)
        } catch (e: Exception) {
            // malformed input from the web layer - ignore rather than crash
        }
    }

    @JavascriptInterface
    fun setPrayerDone(cycleDate: String, prayerId: String, done: Boolean) {
        prefs().edit().putBoolean("done_${cycleDate}_${prayerId}", done).apply()
        WidgetData.refreshAllWidgets(context)
    }

    /**
     * Returns which of today's prayers are marked done, as JSON e.g.
     * {"fajr":true,"dhuhr":false,...} - so the WebView can reconcile with
     * anything marked done via a home-screen widget while the app wasn't open.
     */
    @JavascriptInterface
    fun getDoneStates(cycleDate: String): String {
        val obj = JSONObject()
        WidgetData.PRAYER_ORDER.forEach { id ->
            obj.put(id, prefs().getBoolean("done_${cycleDate}_${id}", false))
        }
        return obj.toString()
    }

    /**
     * jsonArray: [{id, start, end}, ...] epoch-millis start/end for today's 5 prayers,
     * in fajr..isha order (end of isha = tomorrow's fajr). Also stored (not just
     * scheduled as alarms) so home-screen widgets can read today's times/labels
     * without the WebView needing to be running.
     */
    @JavascriptInterface
    fun schedulePrayers(cycleDate: String, jsonArray: String) {
        try {
            val arr = JSONArray(jsonArray)
            val list = mutableListOf<Triple<String, Long, Long>>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val start = o.optLong("start", 0L)
                val end = o.optLong("end", 0L)
                if (start > 0L) list.add(Triple(o.getString("id"), start, end))
            }
            val p = prefs()
            p.edit()
                .putString("cycleDate", cycleDate)
                .putString("prayerTimesJson", jsonArray)
                .apply()
            AlarmScheduler.scheduleDay(
                context, cycleDate, list,
                p.getString("athanMode", "notification") ?: "notification",
                p.getBoolean("alertsEnabled", false),
                p.getInt("alertLeadMinutes", 20)
            )
            WidgetData.refreshAllWidgets(context)
        } catch (e: Exception) {
            // malformed input from the web layer - ignore rather than crash
        }
    }
}
