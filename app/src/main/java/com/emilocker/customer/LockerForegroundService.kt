package com.emilocker.customer

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
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
    @Volatile private var loc: Location? = null

    private val locL = object : LocationListener {
        override fun onLocationChanged(l: Location) { loc = l }
        override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
    }
    private val screenR = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (Store.bool(c, "locked")) PolicyController.showLockScreen(c)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("locker", "EMI Locker", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "locker")
            .setContentTitle("EMI Locker Protected")
            .setContentText("EMI security & monitoring active")
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, n)
        }
        registerReceiver(screenR, IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_USER_PRESENT) })
        startLocation()
        WallpaperManagerHolder.watch(this)
        ex.scheduleWithFixedDelay({
            try { tick() } catch (e: Exception) { Log.e(tag, "tick", e) }
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
            try { lm.getLastKnownLocation(it) } catch (_: Exception) { null }
        }.maxByOrNull { it.time }
    }

    private fun batteryPct(): Int {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val lvl = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (lvl in 0..100) lvl else -1
    }

    private fun networkLabel(): String {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return "OFFLINE"
        val caps = cm.getNetworkCapabilities(net) ?: return "CONNECTED"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
            else -> "Online"
        }
    }

    private fun tick() {
        if (!Store.isSetupComplete(this)) return
        poll()
        if (ticks++ % 4 == 0) telemetry()
    }

    private fun poll() {
        val r = Api.post(this, "device_poll.php", JSONObject()) ?: return
        if (!r.optBoolean("ok")) return
        val st = r.optJSONObject("state")
        if (st != null) {
            val emiStatus = st.optString("emi_status")
            val lockState = st.optString("lock_state")
            Store.put(this, "emi_status", emiStatus)
            Store.put(this, "lock_state", lockState)
            Store.put(this, "shop_phone", st.optString("shop_phone"))
            Store.put(this, "lock_message", st.optString("lock_message"))
            Store.put(this, "due_amount", st.optString("due_amount"))
            Store.put(this, "next_due_date", st.optString("next_due_date"))

            if (lockState == "LOCKED") {
                Store.putBool(this, "locked", true)
                PolicyController.showLockScreen(this)
            } else if (lockState == "UNLOCKED" && emiStatus != "LOCKED") {
                Store.putBool(this, "locked", false)
            }
        }
        val cmds = r.optJSONArray("commands") ?: return
        for (i in 0 until cmds.length()) {
            val c = cmds.getJSONObject(i)
            val id = c.getString("command_id")
            val done = Store.get(this, "done_ids").split(",").filter { it.isNotEmpty() }
            val (ok, msg) = if (id in done) {
                true to "duplicate ignored (already executed)"
            } else {
                PolicyController.execute(this, c.getString("type"), c.optJSONObject("payload") ?: JSONObject(), st)
            }
            val ack = JSONObject()
                .put("command_id", id)
                .put("status", if (ok) "EXECUTED" else "FAILED")
                .put("message", msg)
            if (Api.post(this, "device_ack.php", ack) != null && id !in done) {
                Store.put(this, "done_ids", (done + id).takeLast(50).joinToString(","))
            }
        }
    }

    private fun telemetry() {
        val tm = getSystemService(TelephonyManager::class.java)
        val carrier = tm.simOperatorName ?: ""
        var serial = "UNAVAILABLE"
        try {
            if (granted(Manifest.permission.READ_PHONE_STATE)) {
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
            .put("network_type", networkLabel())
        val bat = batteryPct()
        if (bat >= 0) b.put("battery_level", bat)
        (loc ?: lastKnown())?.let {
            b.put("lat", it.latitude)
            b.put("lng", it.longitude)
        }
        val r = Api.post(this, "device_telemetry.php", b)
        if (r?.optBoolean("ok") == true) {
            Store.put(this, "sim_fp", fp)
        }
    }
}

object WallpaperManagerHolder {
    fun watch(c: Context) {
        val wm = android.app.WallpaperManager.getInstance(c)
        wm.addOnColorsChangedListener({ _, _ ->
            if (Store.bool(c, "wallpaper_locked") && System.currentTimeMillis() - PolicyController.lastApply > 8000) {
                PolicyController.applyFile(c)
            }
        }, Handler(Looper.getMainLooper()))
    }
}
