package com.mmusa.qadatracker

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared read/write access to the same SharedPreferences that PrayerBridge
 * writes, plus small helpers widgets need. Widgets are fully native views
 * (RemoteViews) - they cannot use the WebView/JS layer at all, so this is
 * the one place both sides agree on: prayer times, done-state, language and
 * theme, all keyed exactly the way PrayerBridge already stores them.
 */
object WidgetData {

    val PRAYER_ORDER = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")

    data class PrayerTime(val id: String, val start: Long, val end: Long)

    data class Colors(val bg: Int, val card: Int, val text: Int, val muted: Int, val accent: Int, val done: Int, val active: Int, val missed: Int)

    val DARK = Colors(
        bg = 0xFF0E2027.toInt(), card = 0xFF16323A.toInt(), text = 0xFFF3ECDC.toInt(),
        muted = 0xFF8FA9AF.toInt(), accent = 0xFFE8C468.toInt(),
        done = 0xFF4E8B6B.toInt(), active = 0xFFD3903F.toInt(), missed = 0xFFC17A4F.toInt()
    )
    val LIGHT = Colors(
        bg = 0xFFF4ECD8.toInt(), card = 0xFFFFFFFF.toInt(), text = 0xFF2B2013.toInt(),
        muted = 0xFF6B5D43.toInt(), accent = 0xFFA9791F.toInt(),
        done = 0xFF3F7A5A.toInt(), active = 0xFFB5731F.toInt(), missed = 0xFFA85A37.toInt()
    )

    fun prefs(context: Context) = context.getSharedPreferences("qada_native_prefs", Context.MODE_PRIVATE)

    fun getLang(context: Context): String = prefs(context).getString("lang", "ar") ?: "ar"
    fun getColors(context: Context): Colors =
        if ((prefs(context).getString("theme", "dark") ?: "dark") == "light") LIGHT else DARK
    fun getCycleDate(context: Context): String? = prefs(context).getString("cycleDate", null)

    fun getPrayerTimes(context: Context): List<PrayerTime> {
        val json = prefs(context).getString("prayerTimesJson", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                PrayerTime(o.getString("id"), o.optLong("start", 0L), o.optLong("end", 0L))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isDone(context: Context, cycleDate: String, prayerId: String): Boolean =
        prefs(context).getBoolean("done_${cycleDate}_${prayerId}", false)

    fun setDone(context: Context, cycleDate: String, prayerId: String, done: Boolean) {
        prefs(context).edit().putBoolean("done_${cycleDate}_${prayerId}", done).apply()
    }

    fun prayerLabel(context: Context, id: String): String {
        val ar = mapOf("fajr" to "الفجر", "dhuhr" to "الظهر", "asr" to "العصر", "maghrib" to "المغرب", "isha" to "العشاء")
        val en = mapOf("fajr" to "Fajr", "dhuhr" to "Dhuhr", "asr" to "Asr", "maghrib" to "Maghrib", "isha" to "Isha")
        return if (getLang(context) == "en") (en[id] ?: id) else (ar[id] ?: id)
    }

    fun statusLabel(context: Context, status: String): String {
        val en = getLang(context) == "en"
        return when (status) {
            "done" -> if (en) "Prayed" else "تمت"
            "active" -> if (en) "Due now" else "وقتها دلوقتي"
            "missed" -> if (en) "Time passed" else "فات وقتها"
            else -> if (en) "Not yet" else "لسه"
        }
    }

    fun formatTime(context: Context, millis: Long): String {
        if (millis <= 0L) return "--:--"
        val locale = if (getLang(context) == "en") Locale.US else Locale("ar", "EG")
        return SimpleDateFormat("h:mm a", locale).format(Date(millis))
    }

    /** done | active | missed | upcoming */
    fun statusOf(context: Context, cycleDate: String, pt: PrayerTime, now: Long): String {
        if (isDone(context, cycleDate, pt.id)) return "done"
        if (pt.start > 0 && now < pt.start) return "upcoming"
        if (pt.end > 0 && now >= pt.end) return "missed"
        return "active"
    }

    /** The prayer whose window is currently open (already started, not yet ended). */
    fun currentPrayer(times: List<PrayerTime>, now: Long): PrayerTime? =
        times.filter { it.start > 0 && it.start <= now && (it.end <= 0 || now < it.end) }.maxByOrNull { it.start }

    fun nextUpcoming(times: List<PrayerTime>, now: Long): PrayerTime? =
        times.filter { it.start > 0 && it.start > now }.minByOrNull { it.start }

    /** The next prayer in today's fixed order after this one (wraps to fajr after isha). */
    fun nextPrayerIdAfter(id: String): String {
        val i = PRAYER_ORDER.indexOf(id)
        return if (i in 0 until PRAYER_ORDER.size - 1) PRAYER_ORDER[i + 1] else "fajr"
    }

    fun formatRemaining(context: Context, millis: Long): String {
        if (millis <= 0) return if (getLang(context) == "en") "0m" else "٠ د"
        val totalMin = millis / 60000
        val h = totalMin / 60
        val m = totalMin % 60
        val en = getLang(context) == "en"
        return when {
            h > 0 && en -> "${h}h ${m}m"
            h > 0 -> "${h} س ${m} د"
            en -> "${m}m"
            else -> "${m} د"
        }
    }

    fun refreshAllWidgets(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        updateWidgetsOfType(context, mgr, TodayPrayersWidget::class.java)
        updateWidgetsOfType(context, mgr, CountdownWidget::class.java)
        CountdownTicker.reconcile(context)
    }

    private fun updateWidgetsOfType(context: Context, mgr: AppWidgetManager, cls: Class<*>) {
        val ids = mgr.getAppWidgetIds(ComponentName(context, cls))
        if (ids.isEmpty()) return
        val intent = android.content.Intent(context, cls)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        context.sendBroadcast(intent)
    }
}
