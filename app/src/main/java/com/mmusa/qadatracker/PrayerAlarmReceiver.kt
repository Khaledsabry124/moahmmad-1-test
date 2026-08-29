package com.mmusa.qadatracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Fires when a scheduled prayer-time or heads-up/missed alarm goes off.
 * Reads its own copy of settings/done-state from SharedPreferences (written
 * by PrayerBridge) so it works even if the WebView isn't running.
 */
class PrayerAlarmReceiver : BroadcastReceiver() {

    companion object {
        val PRAYER_INDEX = mapOf("fajr" to 0, "dhuhr" to 1, "asr" to 2, "maghrib" to 3, "isha" to 4)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra("type") ?: return
        val prayerId = intent.getStringExtra("prayerId") ?: return

        when (type) {
            "athan" -> handleAthan(context, prayerId)
            "upcoming" -> handleUpcoming(context, intent, prayerId)
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences("qada_native_prefs", Context.MODE_PRIVATE)

    /**
     * One notification channel per sound identity, since Android only allows
     * one sound per channel (set once, at creation - it can't be changed
     * later without a new channel ID). Tries each name in soundCandidates in
     * order (e.g. a prayer-specific file first, then a shared fallback file),
     * then the generic "notification_sound.mp3" if none of those exist, then
     * the system default - so every combination of files-added-or-not works.
     *
     * Vibration is intentionally NOT set on the channel (channel-level
     * vibration is locked in permanently at creation, which would make the
     * in-app vibration toggle useless after the first alert). Instead
     * postNotification() below triggers vibration manually every time,
     * checked fresh against the current setting on each alert.
     */
    private fun ensureChannel(context: Context, channelId: String, soundCandidates: List<String>, description: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(channelId, "Prayer alerts", NotificationManager.IMPORTANCE_HIGH)
                channel.description = description

                var customResId = 0
                for (name in soundCandidates + "notification_sound") {
                    customResId = context.resources.getIdentifier(name, "raw", context.packageName)
                    if (customResId != 0) break
                }
                val soundUri = if (customResId != 0)
                    android.net.Uri.parse("android.resource://${context.packageName}/$customResId")
                else
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)

                val audioAttrs = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                channel.setSound(soundUri, audioAttrs)
                channel.enableVibration(false)
                mgr.createNotificationChannel(channel)
            }
        }
    }

    // Two sound categories per prayer, matching the two things that can
    // actually happen: "on track" (this prayer, or the one before it, was
    // handled in time) vs "missed" (the one before it wasn't, and this alert
    // is really about THAT prayer, not the one approaching).
    private fun prayerChannelId(prayerId: String) = "prayer_alert_${prayerId}_v1"
    private fun missedChannelId(prayerId: String) = "prayer_alert_${prayerId}_missed_v3"

    private fun prayerLabel(context: Context, prayerId: String): String {
        val lang = prefs(context).getString("lang", "ar") ?: "ar"
        val arabic = mapOf("fajr" to "الفجر", "dhuhr" to "الظهر", "asr" to "العصر", "maghrib" to "المغرب", "isha" to "العشاء")
        val english = mapOf("fajr" to "Fajr", "dhuhr" to "Dhuhr", "asr" to "Asr", "maghrib" to "Maghrib", "isha" to "Isha")
        return if (lang == "en") (english[prayerId] ?: prayerId) else (arabic[prayerId] ?: prayerId)
    }

    /**
     * The single heads-up/missed alert, fired once per prayer transition (see
     * AlarmScheduler for why there's only one). Picks exactly one of two
     * messages - never both, never repeated - based on whether the prayer
     * right before this one has been logged yet.
     */
    private fun handleUpcoming(context: Context, intent: Intent, prayerId: String) {
        val label = prayerLabel(context, prayerId)
        val lang = prefs(context).getString("lang", "ar") ?: "ar"
        val cycleDate = intent.getStringExtra("cycleDate")
        val previousId = intent.getStringExtra("previousPrayerId")
        val style = prefs(context).getString("reminderStyle", "plain") ?: "plain"

        val previousMissed = !previousId.isNullOrEmpty() && cycleDate != null &&
            !prefs(context).getBoolean("done_${cycleDate}_${previousId}", false)

        if (previousMissed) {
            val prevLabel = prayerLabel(context, previousId!!)
            val title: String
            val body: String
            if (lang == "en") {
                title = if (style == "gentle") "A gentle reminder" else "Pray $prevLabel before you miss it"
                body = if (style == "gentle") "There's still time for $prevLabel - it closes soon." else "Its time is about to end."
            } else {
                title = if (style == "gentle") "تذكير بلطف" else "صلّ $prevLabel قبل ما يفوتك"
                body = if (style == "gentle") "لسه في وقت لصلاة $prevLabel… بادر بيها" else "وقتها بيوشك يخلص."
            }
            val channelId = missedChannelId(previousId)
            ensureChannel(context, channelId, listOf("missed_$previousId", "reminder_$previousId", "notification_${previousId}_missed", "notification_reminder"), "Missed prayer alert for $previousId")
            postNotification(context, channelId, title, body, 5000 + (PRAYER_INDEX[prayerId] ?: 0))
        } else {
            val title = if (lang == "en") "$label is coming up" else "اقترب وقت صلاة $label"
            val body = if (lang == "en") "Prayer time starts shortly." else "دخل وقتها كمان شوية."
            val channelId = prayerChannelId(prayerId)
            ensureChannel(context, channelId, listOf("notification_$prayerId"), "Alerts for $prayerId")
            postNotification(context, channelId, title, body, 5000 + (PRAYER_INDEX[prayerId] ?: 0))
        }
    }

    /**
     * Fires exactly at the prayer's actual start time as determined by the
     * app's own sync (never early, never late - this alarm is scheduled
     * directly against the synced timestamp, with no separate estimate).
     */
    private fun handleAthan(context: Context, prayerId: String) {
        val athanMode = prefs(context).getString("athanMode", "notification") ?: "notification"
        if (athanMode == "off") return

        val label = prayerLabel(context, prayerId)
        val lang = prefs(context).getString("lang", "ar") ?: "ar"
        val title = if (lang == "en") "Time for $label" else "حان وقت صلاة $label"

        if (athanMode == "full") {
            // Look for a prayer-specific file first (athan_fajr.mp3, athan_dhuhr.mp3, ...) -
            // useful since Fajr's athan traditionally includes an extra phrase - falling
            // back to one generic athan.mp3 for all prayers, then to notification-only.
            // Looked up by name at runtime rather than a compiled R.raw reference, so the
            // project still builds even if neither file has been added yet.
            var resId = context.resources.getIdentifier("athan_$prayerId", "raw", context.packageName)
            if (resId == 0) resId = context.resources.getIdentifier("athan", "raw", context.packageName)
            if (resId != 0) {
                try {
                    val serviceIntent = Intent(context, AthanPlaybackService::class.java).apply {
                        putExtra(AthanPlaybackService.EXTRA_RES_ID, resId)
                        putExtra(AthanPlaybackService.EXTRA_TITLE, title)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    // fall through - the notification below still fires either way
                }
                return // full athan mode: the service's own ringing vibration covers this alert, no extra notification vibration
            }
        }
        // Notification-only mode, or "full" mode with no athan file added yet -
        // uses the same per-prayer channel/sound as the "on track" heads-up.
        val channelId = prayerChannelId(prayerId)
        ensureChannel(context, channelId, listOf("notification_$prayerId"), "Alerts for $prayerId")
        postNotification(context, channelId, title, "", 3000 + (PRAYER_INDEX[prayerId] ?: 0))
    }

    private fun postNotification(context: Context, channelId: String, title: String, body: String, notifId: Int) {
        val launchIntent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (e: SecurityException) {
            // Notification permission not granted - skip silently rather than crash.
        }

        vibrateOneSecond(context)
    }

    private fun vibrateOneSecond(context: Context) {
        if (!prefs(context).getBoolean("vibrationEnabled", true)) return
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(1000)
            }
        } catch (e: Exception) { /* vibration unavailable on this device - safe to skip */ }
    }
}
