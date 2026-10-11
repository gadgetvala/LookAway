package co.tekonika.lookaway.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.text.format.DateFormat
import co.tekonika.lookaway.MainActivity
import co.tekonika.lookaway.R

/**
 * All notification UI. Channels are the user-facing knobs: anyone who finds the warning or the
 * end-of-break sound annoying can turn that channel off in system settings, so no in-app toggles.
 */
class BreakNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CH_STATUS, context.getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
                NotificationChannel(CH_WARNING, context.getString(R.string.channel_warning), NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        setSound(null, null)
                        enableVibration(false)
                    },
                NotificationChannel(CH_DONE, context.getString(R.string.channel_done), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    /** The persistent foreground-service notification: Android's equivalent of the macOS menu bar item. */
    fun status(timer: BreakTimer, config: BreakConfig): Notification {
        val now = SystemClock.elapsedRealtime()
        val left = timer.timeLeft(now)
        val builder = builder(CH_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        val countdown = timer.isScreenOn && timer.phase != Phase.STOPPED
        builder.setShowWhen(countdown).setUsesChronometer(countdown).setChronometerCountDown(countdown)
        if (countdown) builder.setWhen(System.currentTimeMillis() + left)

        when (timer.phase) {
            Phase.WORKING -> {
                if (timer.isScreenOn) {
                    builder.setContentTitle(context.getString(R.string.status_working_title)).setContentText(
                        context.getString(R.string.status_working_text, config.workMs / 60_000, config.breakMs / 1_000),
                    )
                } else {
                    val minutes = ((left + 59_999) / 60_000).toInt()
                    builder.setContentTitle(context.getString(R.string.status_screen_off_title))
                        .setContentText(context.resources.getQuantityString(R.plurals.minutes_left, minutes, minutes))
                }
                builder.addAction(action(R.string.action_pause_30, BreakService.ACTION_PAUSE, 30))
                    .addAction(action(R.string.action_reset, BreakService.ACTION_RESET))
                    .addAction(action(R.string.action_stop, BreakService.ACTION_STOP))
            }
            Phase.BREAK -> builder.setContentTitle(context.getString(R.string.status_break_title))
                .setContentText(context.getString(R.string.overlay_message, config.breakMs / 1_000))
                .addAction(action(R.string.action_skip, BreakService.ACTION_SKIP))
                .addAction(action(R.string.action_stop, BreakService.ACTION_STOP))
            Phase.PAUSED -> {
                val resumeAt = DateFormat.getTimeFormat(context).format(System.currentTimeMillis() + left)
                builder.setContentTitle(context.getString(R.string.status_paused_title))
                    .setContentText(context.getString(R.string.status_paused_text, resumeAt))
                    .addAction(action(R.string.action_resume, BreakService.ACTION_RESUME))
                    .addAction(action(R.string.action_stop, BreakService.ACTION_STOP))
            }
            Phase.STOPPED -> builder.setContentTitle(context.getString(R.string.status_stopped_title))
        }
        return builder.build()
    }

    fun updateStatus(timer: BreakTimer, config: BreakConfig) = manager.notify(ID_STATUS, status(timer, config))

    fun warn(config: BreakConfig) = manager.notify(
        ID_WARNING,
        builder(CH_WARNING)
            .setContentTitle(context.getString(R.string.warning_title, config.warnMs / 1_000))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter(config.warnMs)
            .build(),
    )

    fun cancelWarning() = manager.cancel(ID_WARNING)

    /** Plays the channel sound, the Android counterpart of the macOS "Purr". */
    fun breakFinished() = manager.notify(
        ID_DONE,
        builder(CH_DONE)
            .setContentTitle(context.getString(R.string.done_title))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter(DONE_TIMEOUT_MS)
            .build(),
    )

    /** Shown when the overlay can't be drawn (permission revoked), so the break isn't silently lost. */
    fun breakFallback(config: BreakConfig) = manager.notify(
        ID_WARNING,
        builder(CH_WARNING)
            .setContentTitle(context.getString(R.string.status_break_title))
            .setContentText(context.getString(R.string.overlay_message, config.breakMs / 1_000))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter(config.breakMs)
            .build(),
    )

    private fun builder(channel: String) = Notification.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_stat_eye)
        .setColor(context.getColor(R.color.brand))
        .setContentIntent(openApp)

    private val openApp: PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(label: Int, action: String, minutes: Int = 0): Notification.Action {
        val intent = BreakService.intent(context, action).putExtra(BreakService.EXTRA_MINUTES, minutes)
        val pending = PendingIntent.getForegroundService(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(null, context.getString(label), pending).build()
    }

    companion object {
        const val ID_STATUS = 1
        private const val ID_WARNING = 2
        private const val ID_DONE = 3
        private const val DONE_TIMEOUT_MS = 5_000L
        private const val CH_STATUS = "status"
        private const val CH_WARNING = "warning"
        private const val CH_DONE = "break_done"
    }
}
