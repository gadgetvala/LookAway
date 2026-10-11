package co.tekonika.lookaway.service

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import co.tekonika.lookaway.overlay.BreakOverlay

/**
 * The always-on part of the app. A foreground service (type specialUse) owns the timer, the
 * status notification and the break overlay, and keeps running with the Flutter UI closed.
 *
 * No AlarmManager and no wake locks: work time only counts while the screen is on, and while the
 * screen is on the CPU is awake, so a plain Handler is accurate. While the screen is off nothing
 * needs to fire. Screen broadcasts only trigger [syncScreen], which reads the real screen and lock
 * state, so a missed or reordered broadcast can't leave the timer frozen.
 */
class BreakService : Service() {
    private lateinit var store: BreakStore
    private lateinit var notifications: BreakNotifications
    private lateinit var overlay: BreakOverlay
    private lateinit var config: BreakConfig
    private var timer = BreakTimer()

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = Runnable { tick() }
    private val unlockPoll = Runnable { syncScreen() }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = syncScreen()
    }

    override fun onCreate() {
        super.onCreate()
        store = BreakStore(this)
        notifications = BreakNotifications(this)
        overlay = BreakOverlay(this) { skip() }
        config = store.config
        instance = this

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Must be EXPORTED: USER_PRESENT is sent by SystemUI, not the system uid, and a NOT_EXPORTED
            // receiver silently never gets it, which left the timer frozen after unlock. Safe, because all
            // three are protected broadcasts that only the platform can send.
            registerReceiver(screenReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() contract: must be called on every start, before anything else.
        startInForeground()

        // A null intent means the system restarted us after a kill (START_STICKY): restore state.
        val action = intent?.action ?: ACTION_RUN
        if (action == ACTION_STOP) {
            stop()
            return START_NOT_STICKY
        }

        if (timer.phase == Phase.STOPPED) restore() else syncScreen()
        store.enabled = true
        val now = now()
        when (action) {
            ACTION_RESET, ACTION_RESUME -> update(timer.work(now, config))
            ACTION_SKIP -> skip()
            ACTION_BREAK_NOW -> update(timer.startBreak(now, config))
            ACTION_PAUSE -> update(timer.pause(now, (intent?.getIntExtra(EXTRA_MINUTES, 0) ?: 0) * 60_000L))
            ACTION_CONFIG -> {
                val old = config
                config = store.config
                // Only a new work interval restarts the work period; other settings apply as they are.
                if (timer.phase == Phase.WORKING && config.workMs != old.workMs) update(timer.work(now, config)) else publish()
            }
        }
        tick()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        handler.removeCallbacks(unlockPoll)
        unregisterReceiver(screenReceiver)
        overlay.hide()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Rehydrates persisted state, then reconciles it with the real screen state right now. */
    private fun restore() {
        val restored = store.timer
        update(if (restored.phase == Phase.STOPPED) restored.work(now(), config) else restored)
        syncScreen()
    }

    /**
     * Single source of truth for "is the user looking at the screen": the screen is on and unlocked.
     * While the lock screen is showing, re-checks every second instead of trusting USER_PRESENT alone,
     * because some OEM builds deliver it late or not at all.
     */
    private fun syncScreen() {
        handler.removeCallbacks(unlockPoll)
        val now = now()
        val interactive = getSystemService(PowerManager::class.java).isInteractive
        Log.d(TAG, "syncScreen interactive=$interactive locked=${isLocked()} phase=${timer.phase} left=${timer.timeLeft(now)}")
        when {
            !interactive -> update(timer.screenOff(now))
            isLocked() -> {
                update(timer.screenOff(now))
                handler.postDelayed(unlockPoll, UNLOCK_POLL_MS)
            }
            else -> update(timer.screenOn(now, config))
        }
        tick()
    }

    private fun skip() {
        if (timer.phase == Phase.BREAK) update(timer.work(now(), config))
        tick()
    }

    private fun stop() {
        store.enabled = false
        update(BreakTimer())
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startInForeground() {
        val notification = notifications.status(timer, config)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(BreakNotifications.ID_STATUS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(BreakNotifications.ID_STATUS, notification)
        }
    }

    /** Applies every transition that is due, then schedules the next one. */
    private fun tick() {
        while (true) {
            val (next, event) = timer.evaluate(now(), config)
            event ?: break
            update(next, event)
        }
        handler.removeCallbacks(tickRunnable)
        val wakeAt = timer.nextWakeAt(config) ?: return
        handler.postDelayed(tickRunnable, (wakeAt - now()).coerceAtLeast(0L))
    }

    /** Single place where state changes turn into side effects. */
    private fun update(next: BreakTimer, event: Event? = null) {
        val prev = timer
        if (next == prev && event == null) return
        timer = next

        if (prev.phase != Phase.BREAK && next.phase == Phase.BREAK) {
            notifications.cancelWarning()
            if (!overlay.show(next.endsAt, config)) notifications.breakFallback(config)
        }
        if (prev.phase == Phase.BREAK && next.phase != Phase.BREAK) overlay.hide()

        when (event) {
            Event.WARN -> notifications.warn(config)
            Event.BREAK_FINISHED -> notifications.breakFinished()
            else -> Unit
        }

        store.timer = next
        publish()
    }

    private fun publish() {
        if (timer.phase != Phase.STOPPED) notifications.updateStatus(timer, config)
        listener?.invoke(snapshot(timer, config))
    }

    private fun isLocked() = getSystemService(KeyguardManager::class.java).isKeyguardLocked

    companion object {
        const val ACTION_RUN = "co.tekonika.lookaway.action.RUN"
        const val ACTION_STOP = "co.tekonika.lookaway.action.STOP"
        const val ACTION_RESET = "co.tekonika.lookaway.action.RESET"
        const val ACTION_SKIP = "co.tekonika.lookaway.action.SKIP"
        const val ACTION_BREAK_NOW = "co.tekonika.lookaway.action.BREAK_NOW"
        const val ACTION_PAUSE = "co.tekonika.lookaway.action.PAUSE"
        const val ACTION_RESUME = "co.tekonika.lookaway.action.RESUME"
        const val ACTION_CONFIG = "co.tekonika.lookaway.action.CONFIG"
        const val EXTRA_MINUTES = "minutes"
        private const val UNLOCK_POLL_MS = 1_000L
        private const val TAG = "LookAway"

        /** Live state sink for the Flutter UI. Main thread only. */
        var listener: ((Map<String, Any>) -> Unit)? = null

        private var instance: BreakService? = null

        fun intent(context: Context, action: String): Intent =
            Intent(context, BreakService::class.java).setAction(action)

        fun send(context: Context, action: String, minutes: Int = 0) {
            // Nothing to stop or reconfigure if we aren't running; don't spin up a service just for that.
            if (instance == null && (action == ACTION_STOP || action == ACTION_CONFIG)) {
                if (action == ACTION_STOP) BreakStore(context).enabled = false
                listener?.invoke(state(context))
                return
            }
            context.startForegroundService(intent(context, action).putExtra(EXTRA_MINUTES, minutes))
        }

        fun state(context: Context): Map<String, Any> =
            instance?.let { snapshot(it.timer, it.config) } ?: snapshot(BreakTimer(), BreakStore(context).config)

        private fun now() = SystemClock.elapsedRealtime()

        private fun snapshot(timer: BreakTimer, config: BreakConfig): Map<String, Any> = mapOf(
            "phase" to timer.phase.name.lowercase(),
            "timeLeftMs" to timer.timeLeft(now()),
            "screenOn" to timer.isScreenOn,
            "workMin" to (config.workMs / 60_000L).toInt(),
            "breakSec" to (config.breakMs / 1_000L).toInt(),
            "resetAfterScreenOff" to config.resetAfterScreenOff,
        )
    }
}
