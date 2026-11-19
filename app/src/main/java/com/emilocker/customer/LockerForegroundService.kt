package com.emilocker.customer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LockerForegroundService : Service() {
    private val tag = "LockerSvc"
    private val ex = Executors.newSingleThreadScheduledExecutor()
    private var ticks = 0

    @Volatile
    private var loc: Location? = null

    private val locL = object : LocationListener {
        override fun onLocationChanged(l: Location) {
            loc = l
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {
        }

        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
    }

    private val screenR = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (Store.bool(c, "locked")) PolicyController.showLockScreen(c)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int = START_STICKY

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("locker", "EMI Locker", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "locker")
            .setContentTitle("EMI Locker is active")
            .setContentText("EMI protection and location sharing are running")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(1, n)
        } catch (e: Exception) {
            Log.e(tag, "startForeground failed (grant location permission first): ${e.message}")
            stopSelf()
            return
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_USER_PRESENT) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenR, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenR, filter)
        startLocation()
        WallpaperWatcher.watch(this)
        ex.scheduleWithFixedDelay({
            try {
                tick()
            } catch (e: Exception) {
                Log.e(tag, "tick", e)
            }
        }, 1, 15, TimeUnit.SECONDS)
    }

    override fun onDestroy() {
        ex.shutdownNow()
        try { unregisterReceiver(screenR) } catch (_: Exception) {}
        try { getSystemService(LocationManager::class.java).removeUpdates(locL) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun startLocation() {
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) return
        val lm = getSystemService(LocationManager::class.java)
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                lm.requestLocationUpdates(p, 60000L, 0f, locL, Looper.getMainLooper())
            } catch (e: Exception) {
                Log.w(tag, "loc $p: ${e.message}")
            }
        }
    }

    private fun lastKnown(): Location? {
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) return null
        val lm = getSystemService(LocationManager::class.java)
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).mapNotNull {
            try { lm.getLastKnownLocation(it) } catch (e: Exception) { null }
        }.maxByOrNull { it.time }
    }

    private fun tick() {
        poll()
        // telemetry every ~60 s, or every poll while "live view" is on
        if (Store.bool(this, "live_view") || ticks % 4 == 0) telemetry()
        ticks++
    }

    private fun poll() {
        val r = Api.post(this, "device_poll.php", JSONObject()) ?: return
        if (!r.optBoolean("ok")) return
        val state = r.optJSONObject("state")
        PolicyController.saveState(this, state)
        val cmds = r.optJSONArray("commands") ?: return
        for (i in 0 until cmds.length()) {
            val c = cmds.getJSONObject(i)
            val id = c.optString("command_id")
            if (id.isEmpty()) continue
            val done = Store.get(this, "done_ids").split(",").filter { it.isNotEmpty() }
            val (ok, msg) = if (id in done) {
                true to "duplicate ignored (already executed)"
            } else {
                PolicyController.execute(this, c.optString("type"), c.optJSONObject("payload") ?: JSONObject(), state)
            }
            // remember the id before acking so a wipe/reboot cannot cause a replay
            if (id !in done) Store.put(this, "done_ids", (done + id).takeLast(50).joinToString(","))
            val ack = JSONObject()
                .put("command_id", id)
                .put("status", if (ok) "EXECUTED" else "FAILED")
                .put("message", msg)
            Api.post(this, "device_ack.php", ack)
        }
    }

    private fun networkType(): String {
        return try {
            val cm = getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            when {
                caps == null -> "NONE"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                else -> "OTHER"
            }
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }

    private fun batteryPct(): Int {
        return try {
            getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            -1
        }
    }

    private fun telemetry() {
        val tm = getSystemService(TelephonyManager::class.java)
        val carrier = tm.simOperatorName ?: ""
        var serial = "UNAVAILABLE"
        try {
            if (granted(Manifest.permission.READ_PHONE_STATE)) {
                // Android 10+ hides the real ICCID from normal apps; fall back to subscription IDs, which change with the SIM.
                val l = getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList
                if (!l.isNullOrEmpty()) {
                    serial = l.joinToString(",") { s ->
                        s.iccId?.takeIf { it.isNotEmpty() } ?: "sub${s.subscriptionId}"
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(tag, "sim: ${e.message}")
        }
        val fp = "$carrier|$serial"
        val old = Store.get(this, "sim_fp")
        val changed = old.isNotEmpty() && old != fp
        val b = JSONObject()
            .put("sim_carrier", carrier)
            .put("sim_serial", serial)
            .put("sim_changed", changed)
            .put("network_type", networkType())
            .put("battery", batteryPct())
        val cur = loc ?: lastKnown()
        if (cur != null) {
            b.put("lat", cur.latitude).put("lng", cur.longitude)
            Store.put(this, "last_lat", cur.latitude.toString())
            Store.put(this, "last_lng", cur.longitude.toString())
        } else {
            val la = Store.get(this, "last_lat")
            val ln = Store.get(this, "last_lng")
            if (la.isNotEmpty() && ln.isNotEmpty()) b.put("lat", la.toDouble()).put("lng", ln.toDouble())
        }
        val r = Api.post(this, "device_telemetry.php", b)
        if (r?.optBoolean("ok") == true) Store.put(this, "sim_fp", fp) // alert is sent once per change
    }
}

/** Re-applies the admin wallpaper if the user changes it while wallpaper_locked is set. */
object WallpaperWatcher {
    private var registered = false

    @Synchronized
    fun watch(c: Context) {
        if (registered) return
        registered = true
        val app = c.applicationContext
        val wm = android.app.WallpaperManager.getInstance(app)
        wm.addOnColorsChangedListener({ _, _ ->
            if (Store.bool(app, "wallpaper_locked") && System.currentTimeMillis() - PolicyController.lastApply > 8000) {
                Thread { try { PolicyController.applyFile(app) } catch (_: Exception) {} }.start()
            }
        }, Handler(Looper.getMainLooper()))
    }
}
