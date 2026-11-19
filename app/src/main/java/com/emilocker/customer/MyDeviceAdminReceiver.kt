package com.emilocker.customer

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONObject

class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i("Admin", "Device admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.w("Admin", "Device admin disabled")
        reportTamper(context, "ADMIN_DISABLED")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        reportTamper(context, "ADMIN_DISABLE_REQUESTED")
        return "Disabling protection while an EMI plan is active may violate your purchase agreement."
    }

    private fun reportTamper(context: Context, kind: String) {
        if (!Store.isSetupComplete(context)) return
        val app = context.applicationContext
        Thread {
            val b = JSONObject()
                .put("tamper_alert", kind)
                .put("message", "Device admin event: $kind")
            Api.post(app, "device_telemetry.php", b)
        }.start()
    }
}
