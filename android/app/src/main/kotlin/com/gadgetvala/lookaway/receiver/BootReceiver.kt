package com.gadgetvala.lookaway.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gadgetvala.lookaway.service.BreakService
import com.gadgetvala.lookaway.service.BreakStore

/** Restarts the timer after a reboot or an app update, if the user had it running. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (BreakStore(context).enabled) BreakService.send(context, BreakService.ACTION_RUN)
    }
}
