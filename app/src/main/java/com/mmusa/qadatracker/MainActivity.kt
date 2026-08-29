package com.mmusa.qadatracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Thin native shell around the self-contained web app in assets/index.html.
 * Prayer-time calculation, tracking logic and local storage happen inside
 * the WebView via localStorage. The native responsibilities are limited to
 * what only native code can do: bridging the geolocation permission prompt,
 * requesting the notification permission once on Android 13+, and exposing
 * PrayerBridge so the web layer can schedule alarms/notifications that keep
 * working while the app isn't open.
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingGeoCallback?.invoke(pendingGeoOrigin, granted, false)
        pendingGeoCallback = null
        pendingGeoOrigin = null
    }

    private val notificationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* if denied, alerts are just skipped elsewhere - nothing to do here */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        requestBatteryOptimizationExemption()

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.setGeolocationEnabled(true)
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.addJavascriptInterface(PrayerBridge(this), "AndroidBridge")

        // Identify the app in requests to public APIs (prayer-times/reverse-geocoding)
        // as a courtesy, per those services' usage policies.
        webView.settings.userAgentString = webView.settings.userAgentString + " QadaTracker/1.0 (personal-use-app)"

        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    callback?.invoke(origin, true, false)
                } else {
                    pendingGeoOrigin = origin
                    pendingGeoCallback = callback
                    locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
        }

        webView.loadUrl("file:///android_asset/index.html")
    }

    /**
     * Alarms/notifications only fire reliably in the background if the OS
     * isn't allowed to freeze the app. This asks the user once to exempt
     * the app from battery optimization - a standard, one-time system
     * dialog (not a custom prompt), which they can decline with no crash
     * either way (alerts just become less reliable if the OS restricts
     * the app in the background).
     */
    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Some OEM builds restrict this intent - safe to ignore, the
                // rest of the app still works, just possibly less reliably
                // in the background on that device.
            }
        }
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
