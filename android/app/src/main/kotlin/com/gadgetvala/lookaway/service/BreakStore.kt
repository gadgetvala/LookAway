package com.gadgetvala.lookaway.service

import android.content.Context
import android.provider.Settings

/**
 * Persists config and timer state so a process restart (START_STICKY, OEM kill, app update)
 * resumes exactly where it left off. Timer values are elapsedRealtime-based, which resets on
 * reboot, so state saved during a previous boot is discarded.
 */
class BreakStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("look_away", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var config: BreakConfig
        get() = BreakConfig(
            workMs = prefs.getInt(KEY_WORK_MIN, BreakConfig.DEFAULT_WORK_MIN) * 60_000L,
            breakMs = prefs.getInt(KEY_BREAK_SEC, BreakConfig.DEFAULT_BREAK_SEC) * 1_000L,
            resetAfterScreenOff = prefs.getBoolean(KEY_RESET_AFTER_SCREEN_OFF, false),
        )
        set(value) = prefs.edit()
            .putInt(KEY_WORK_MIN, (value.workMs / 60_000L).toInt())
            .putInt(KEY_BREAK_SEC, (value.breakMs / 1_000L).toInt())
            .putBoolean(KEY_RESET_AFTER_SCREEN_OFF, value.resetAfterScreenOff)
            .apply()

    var timer: BreakTimer
        get() {
            if (prefs.getInt(KEY_BOOT, -1) != bootCount()) return BreakTimer()
            return BreakTimer(
                phase = Phase.valueOf(prefs.getString(KEY_PHASE, null) ?: Phase.STOPPED.name),
                endsAt = prefs.getLong(KEY_ENDS_AT, 0L),
                remaining = prefs.getLong(KEY_REMAINING, 0L),
                screenOffAt = prefs.getLong(KEY_SCREEN_OFF_AT, -1L).takeIf { it >= 0 },
                warned = prefs.getBoolean(KEY_WARNED, false),
            )
        }
        set(value) = prefs.edit()
            .putInt(KEY_BOOT, bootCount())
            .putString(KEY_PHASE, value.phase.name)
            .putLong(KEY_ENDS_AT, value.endsAt)
            .putLong(KEY_REMAINING, value.remaining)
            .putLong(KEY_SCREEN_OFF_AT, value.screenOffAt ?: -1L)
            .putBoolean(KEY_WARNED, value.warned)
            .apply()

    private fun bootCount() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_WORK_MIN = "work_min"
        const val KEY_BREAK_SEC = "break_sec"
        const val KEY_RESET_AFTER_SCREEN_OFF = "reset_after_screen_off"
        const val KEY_BOOT = "boot_count"
        const val KEY_PHASE = "phase"
        const val KEY_ENDS_AT = "ends_at"
        const val KEY_REMAINING = "remaining"
        const val KEY_SCREEN_OFF_AT = "screen_off_at"
        const val KEY_WARNED = "warned"
    }
}
