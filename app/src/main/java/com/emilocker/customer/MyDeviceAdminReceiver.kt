package com.emilocker.customer

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONObject
import java.util.concurrent.Executors

class MyDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Log.i("Admin", "Device admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.w("Admin", "Device admin disabled")
        Executors.newSingleThreadExecutor().execute {
            Api.post(
                context,
                "device_telemetry.php",
                JSONObject().put("tamper_alert", "Device Admin disabled on device")
            )
        }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Executors.newSingleThreadExecutor().execute {
            Api.post(
                context,
                "device_telemetry.php",
                JSONObject().put("tamper_alert", "Customer attempted to disable Device Admin")
            )
        }
        return "Disabling protection while an EMI plan is active is restricted. Contact the shop."
    }
}
