package com.mmusa.qadatracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

/**
 * Plays a full athan recording to completion, with optional ringing-style
 * vibration and flashlight strobe while it plays (both user-toggleable in
 * Settings). Runs as a foreground service - not directly inside
 * PrayerAlarmReceiver.onReceive() - specifically because a plain
 * BroadcastReceiver's hosting process has no guarantee of staying alive once
 * onReceive() returns, and Android can reclaim it within seconds, killing a
 * multi-minute athan partway through. A foreground service keeps the process
 * alive for exactly as long as playback takes.
 *
 * Pressing either hardware volume button stops everything (audio, vibration,
 * flash) immediately - see watchVolumeButtons().
 */
class AthanPlaybackService : Service() {

    private var player: MediaPlayer? = null
    private var volumeObserver: android.database.ContentObserver? = null
    private var baselineVolume: Int = -1
    private var vibrator: Vibrator? = null
    private var cameraManager: CameraManager? = null
    private var torchCameraId: String? = null
    private var flashHandler: Handler? = null
    private var flashRunnable: Runnable? = null
    private var flashOn = false

    companion object {
        const val EXTRA_RES_ID = "resId"
        const val EXTRA_TITLE = "title"
        const val CHANNEL_ID = "athan_playback_v1"
        const val NOTIF_ID = 7001
    }

    private fun prefs() = getSharedPreferences("qada_native_prefs", MODE_PRIVATE)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resId = intent?.getIntExtra(EXTRA_RES_ID, 0) ?: 0
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Athan"

        ensureChannel()
        startForeground(NOTIF_ID, buildNotification(title))

        if (resId == 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            player = MediaPlayer.create(this, Uri.parse("android.resource://$packageName/$resId"), null, attrs, 0)
            player?.setOnCompletionListener {
                it.release()
                stopEverything()
            }
            player?.setOnErrorListener { _, _, _ ->
                stopEverything()
                true
            }
            player?.start()
            watchVolumeButtons()
            startRingingVibration()
            startFlashStrobe()
        } catch (e: Exception) {
            stopEverything()
        }

        return START_NOT_STICKY
    }

    /**
     * Stops playback entirely the moment the user presses either hardware
     * volume button, rather than just letting it get quieter/louder. There's
     * no public "volume KEY pressed" broadcast in Android, so this watches
     * the system volume setting itself via ContentObserver: pressing either
     * button changes the stored media stream volume, which fires onChange()
     * here regardless of whether the app is in the foreground.
     */
    private fun watchVolumeButtons() {
        val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        baselineVolume = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val handler = android.os.Handler(mainLooper)
        val observer = object : android.database.ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                val current = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                if (baselineVolume != -1 && current != baselineVolume) {
                    stopEverything()
                }
            }
        }
        volumeObserver = observer
        try {
            contentResolver.registerContentObserver(
                android.provider.Settings.System.CONTENT_URI, true, observer
            )
        } catch (e: Exception) { /* if this fails, playback just isn't volume-key-interruptible on this device */ }
    }

    /** Continuous vibrate/pause/vibrate pattern, like an incoming call, for as long as the athan plays. */
    private fun startRingingVibration() {
        if (!prefs().getBoolean("vibrationEnabled", true)) return
        try {
            vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
            val pattern = longArrayOf(0, 1000, 500) // wait, vibrate 1s, pause 0.5s, repeat
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) { /* vibration unavailable on this device - safe to skip */ }
    }

    private fun stopRingingVibration() {
        try { vibrator?.cancel() } catch (e: Exception) { /* already stopped */ }
        vibrator = null
    }

    /** Toggles the flashlight on/off every 400ms for as long as the athan plays. */
    private fun startFlashStrobe() {
        if (!prefs().getBoolean("flashEnabled", false)) return
        try {
            cameraManager = getSystemService(CAMERA_SERVICE) as? CameraManager
            torchCameraId = cameraManager?.cameraIdList?.firstOrNull { id ->
                cameraManager?.getCameraCharacteristics(id)
                    ?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (torchCameraId == null) return

            flashHandler = Handler(mainLooper)
            flashRunnable = object : Runnable {
                override fun run() {
                    flashOn = !flashOn
                    try { cameraManager?.setTorchMode(torchCameraId!!, flashOn) } catch (e: Exception) { /* device may briefly refuse - skip this tick */ }
                    flashHandler?.postDelayed(this, 400)
                }
            }
            flashHandler?.post(flashRunnable!!)
        } catch (e: Exception) { /* no flash unit on this device, or camera busy - safe to skip */ }
    }

    private fun stopFlashStrobe() {
        flashRunnable?.let { flashHandler?.removeCallbacks(it) }
        flashRunnable = null
        flashHandler = null
        if (flashOn) {
            try { torchCameraId?.let { cameraManager?.setTorchMode(it, false) } } catch (e: Exception) { /* already off or camera released */ }
        }
        flashOn = false
    }

    private fun stopEverything() {
        stopRingingVibration()
        stopFlashStrobe()
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        volumeObserver?.let {
            try { contentResolver.unregisterContentObserver(it) } catch (e: Exception) { /* already unregistered */ }
        }
        volumeObserver = null
        stopRingingVibration()
        stopFlashStrobe()
        player?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (e: Exception) { /* already released or invalid state - safe to ignore */ }
        }
        player = null
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                // Low importance and silent - this channel exists only to satisfy
                // Android's mandatory foreground-service notification requirement,
                // not to alert on its own (the athan audio itself is the alert).
                val channel = NotificationChannel(CHANNEL_ID, "Athan playback", NotificationManager.IMPORTANCE_LOW)
                channel.setSound(null, null)
                mgr.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(title: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
