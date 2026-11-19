package com.emilocker.customer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.WallpaperManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.URL

object PolicyController {
    private const val TAG = "Policy"
    private const val ALERT_CHANNEL = "emi_customer_alerts"

    @Volatile
    var lastApply = 0L

    private var ringtone: Ringtone? = null
    private val main = Handler(Looper.getMainLooper())

    private fun dpm(c: Context) = c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    fun admin(c: Context) = ComponentName(c, MyDeviceAdminReceiver::class.java)
    fun isAdmin(c: Context): Boolean = dpm(c).isAdminActive(admin(c))

    /** Executes a server command. Returns (success, message), which is sent back as the ACK. */
    fun execute(c: Context, type: String, p: JSONObject, st: JSONObject?): Pair<Boolean, String> {
        return try {
            when (type) {
                "LOCK" -> {
                    lock(c)
                    true to "locked"
                }
                "UNLOCK" -> {
                    unlock(c)
                    true to "unlocked"
                }
                "SET_WALLPAPER" -> setWallpaper(c, p.optString("url"), p.optBoolean("lock_wallpaper"))
                "SEND_NOTIFICATION" -> {
                    notifyUser(c, p.optString("title", "EMI Locker"), p.optString("message"))
                    true to "notification shown"
                }
                "SET_LOCK_MSG" -> {
                    Store.put(c, "lock_message", p.optString("message"))
                    true to "lock message updated"
                }
                "RING_ALARM" -> {
                    ringAlarm(c, p.optInt("duration_seconds", 20))
                    true to "alarm ringing"
                }
                "SET_LIVE_VIEW" -> {
                    val on = p.optBoolean("enabled", true)
                    Store.putBool(c, "live_view", on)
                    true to "live view ${if (on) "on" else "off"}"
                }
                "BLOCK_DEV_OPT" -> devOptions(c, true)
                "UNBLOCK_DEV_OPT" -> devOptions(c, false)
                "SYNC_STATE" -> {
                    sync(c, st)
                    true to "synced"
                }
                "FACTORY_RESET" -> factoryReset(c)
                else -> false to "unknown command $type"
            }
        } catch (e: Exception) {
            Log.e(TAG, "execute $type", e)
            false to (e.message ?: "error")
        }
    }

    /** Saves the "state" object returned by the server (only keys that are present). */
    fun saveState(c: Context, st: JSONObject?) {
        if (st == null) return
        for (k in listOf("emi_status", "lock_state", "shop_phone", "lock_message", "due_amount", "due_date")) {
            if (st.has(k) && !st.isNull(k)) Store.put(c, k, st.optString(k))
        }
    }

    /**
     * LOCK without Device Owner: lockNow() only turns the screen off, so LockActivity is also
     * shown full-screen and re-shown on every screen-on / user-present (see the service).
     */
    fun lock(c: Context) {
        Store.putBool(c, "locked", true)
        if (isAdmin(c)) dpm(c).lockNow() else Log.w(TAG, "Device admin not enabled: overlay lock only")
        showLockScreen(c)
    }

    fun unlock(c: Context) {
        Store.putBool(c, "locked", false) // LockActivity polls this flag and closes itself
    }

    fun showLockScreen(c: Context) {
        try {
            c.startActivity(
                Intent(c, LockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        } catch (e: Exception) {
            Log.e(TAG, "lock screen: ${e.message} (grant 'display over other apps')")
        }
    }

    private fun factoryReset(c: Context): Pair<Boolean, String> {
        if (!isAdmin(c)) return false to "Device admin not enabled"
        Log.w(TAG, "FACTORY_RESET executing")
        dpm(c).wipeData(0)
        return true to "wipe started"
    }

    fun notifyUser(c: Context, title: String, text: String) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL, "EMI Alerts", NotificationManager.IMPORTANCE_HIGH)
        )
        val n = Notification.Builder(c, ALERT_CHANNEL)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        nm.notify((System.currentTimeMillis() % 100000).toInt(), n)
    }

    @Suppress("DEPRECATION")
    private fun ringAlarm(c: Context, seconds: Int) {
        val secs = seconds.coerceIn(3, 120)
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        main.post {
            try {
                ringtone?.stop()
                val r = RingtoneManager.getRingtone(c.applicationContext, uri)
                r.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= 28) r.isLooping = true
                r.play()
                ringtone = r
                val v = c.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
                main.postDelayed({
                    try { ringtone?.stop() } catch (_: Exception) {}
                    v?.cancel()
                }, secs * 1000L)
            } catch (e: Exception) {
                Log.e(TAG, "alarm: ${e.message}")
            }
        }
    }

    fun setWallpaper(c: Context, url: String, lock: Boolean): Pair<Boolean, String> {
        if (url.isEmpty()) return false to "missing url"
        val bytes = URL(url).openStream().use { it.readBytes() }
        File(c.filesDir, "wallpaper.bin").writeBytes(bytes)
        Store.putBool(c, "wallpaper_locked", lock)
        applyFile(c)
        return true to "wallpaper applied, locked=$lock"
    }

    /** Re-applies the cached admin wallpaper (home + lock screen). */
    fun applyFile(c: Context) {
        val f = File(c.filesDir, "wallpaper.bin")
        if (!f.exists()) return
        val bmp = BitmapFactory.decodeFile(f.path) ?: return
        lastApply = System.currentTimeMillis()
        WallpaperManager.getInstance(c)
            .setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
        lastApply = System.currentTimeMillis()
    }

    /** DISALLOW_DEBUGGING_FEATURES is only honoured for a Device Owner app. */
    private fun devOptions(c: Context, block: Boolean): Pair<Boolean, String> {
        val d = dpm(c)
        if (!d.isDeviceOwnerApp(c.packageName)) {
            return false to "Requires Device Owner: adb shell dpm set-device-owner com.emilocker.customer/.MyDeviceAdminReceiver"
        }
        if (block) d.addUserRestriction(admin(c), UserManager.DISALLOW_DEBUGGING_FEATURES)
        else d.clearUserRestriction(admin(c), UserManager.DISALLOW_DEBUGGING_FEATURES)
        return true to "developer options ${if (block) "blocked" else "unblocked"}"
    }

    private fun sync(c: Context, st: JSONObject?) {
        if (st == null) return
        saveState(c, st)
        if (st.has("wallpaper_locked")) Store.putBool(c, "wallpaper_locked", st.optBoolean("wallpaper_locked"))
        when (st.optString("lock_state")) {
            "LOCKED" -> lock(c)
            "UNLOCKED" -> if (Store.bool(c, "locked")) unlock(c)
        }
    }
}
