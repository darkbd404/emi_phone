package com.emilocker.customer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED || !Store.isSetupComplete(c)) return
        try {
            c.startForegroundService(Intent(c, LockerForegroundService::class.java))
        } catch (e: Exception) {
            Log.w("Boot", "service start blocked: ${e.message}")
        }
        if (Store.bool(c, "locked")) {
            PolicyController.showLockScreen(c)
        }
    }
}
