package com.gadgetvala.lookaway

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.gadgetvala.lookaway.service.BreakConfig
import com.gadgetvala.lookaway.service.BreakService
import com.gadgetvala.lookaway.service.BreakStore
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

/**
 * Bridge between the Flutter UI and the native service. Flutter only renders and sends commands;
 * all timing lives in [BreakService], so closing or killing the UI never affects breaks.
 */
class MainActivity : FlutterActivity() {
    private val store by lazy { BreakStore(this) }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger

        MethodChannel(messenger, CONTROL_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "getState" -> result.success(BreakService.state(this))
                "start" -> command(BreakService.ACTION_RUN, result)
                "stop" -> command(BreakService.ACTION_STOP, result)
                "reset" -> command(BreakService.ACTION_RESET, result)
                "breakNow" -> command(BreakService.ACTION_BREAK_NOW, result)
                "skip" -> command(BreakService.ACTION_SKIP, result)
                "resume" -> command(BreakService.ACTION_RESUME, result)
                "pause" -> command(BreakService.ACTION_PAUSE, result, call.argument<Int>("minutes") ?: 0)
                "setConfig" -> {
                    store.config = BreakConfig(
                        workMs = (call.argument<Int>("workMin") ?: BreakConfig.DEFAULT_WORK_MIN) * 60_000L,
                        breakMs = (call.argument<Int>("breakSec") ?: BreakConfig.DEFAULT_BREAK_SEC) * 1_000L,
                        resetAfterScreenOff = call.argument<Boolean>("resetAfterScreenOff") ?: false,
                    )
                    command(BreakService.ACTION_CONFIG, result)
                }
                "getPermissions" -> result.success(permissions())
                "requestOverlay" -> open(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, result)
                "requestNotifications" -> requestNotifications(result)
                "openAppSettings" -> open(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, result)
                else -> result.notImplemented()
            }
        }

        EventChannel(messenger, STATE_CHANNEL).setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                BreakService.listener = events::success
                events.success(BreakService.state(this@MainActivity))
            }

            override fun onCancel(arguments: Any?) {
                BreakService.listener = null
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // Self-heal: restarts the service if an OEM battery killer took it down, and re-syncs the
        // screen state if it's running (the app being visible proves the screen is on and unlocked).
        if (store.enabled) BreakService.send(this, BreakService.ACTION_RUN)
    }

    private fun command(action: String, result: MethodChannel.Result, minutes: Int = 0) {
        BreakService.send(this, action, minutes)
        result.success(null) // The new state arrives on the state stream once the service applied it.
    }

    private fun permissions(): Map<String, Boolean> = mapOf(
        "overlay" to Settings.canDrawOverlays(this),
        "notifications" to getSystemService(NotificationManager::class.java).areNotificationsEnabled(),
        "battery" to getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName),
    )

    /**
     * Android 13+ shows the runtime dialog the first time; once the user denied it, the dialog won't
     * appear again, so fall back to the app's notification settings page.
     */
    private fun requestNotifications(result: MethodChannel.Result) {
        val prefs = getSharedPreferences("look_away_ui", MODE_PRIVATE)
        val permission = Manifest.permission.POST_NOTIFICATIONS
        val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED &&
            (!prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false) || shouldShowRequestPermissionRationale(permission))

        if (canAsk) {
            prefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, true).apply()
            requestPermissions(arrayOf(permission), 0)
        } else {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
        result.success(null)
    }

    private fun open(action: String, result: MethodChannel.Result) {
        startActivity(Intent(action, Uri.parse("package:$packageName")))
        result.success(null)
    }

    private companion object {
        const val CONTROL_CHANNEL = "com.gadgetvala.lookaway/control"
        const val STATE_CHANNEL = "com.gadgetvala.lookaway/state"
        const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"
    }
}
