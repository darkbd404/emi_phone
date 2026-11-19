package com.emilocker.customer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.WallpaperManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.io.File
import java.net.URL

object PolicyController {
    private const val TAG = "Policy"
    @Volatile var lastApply = 0L

    private fun dpm(c: Context) = c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    fun admin(c: Context) = ComponentName(c, MyDeviceAdminReceiver::class.java)
    fun isAdmin(c: Context) = dpm(c).isAdminActive(admin(c))

    fun execute(c: Context, type: String, p: JSONObject, st: JSONObject?): Pair<Boolean, String> = try {
        when (type) {
            "LOCK" -> {
                val msg = p.optString("message", "")
                val phone = p.optString("support_phone", "")
                if (msg.isNotEmpty()) Store.put(c, "lock_message", msg)
                if (phone.isNotEmpty()) Store.put(c, "shop_phone", phone)
                lock(c)
                true to "locked"
            }
            "UNLOCK" -> {
                unlock(c)
                true to "unlocked"
            }
            "SET_WALLPAPER" -> setWallpaper(c, p.optString("url"), p.optBoolean("lock_wallpaper"))
            "SEND_NOTIFICATION" -> {
                val title = p.optString("title", "EMI Notice")
                val message = p.optString("message", "")
                sendCustomerNotification(c, title, message)
                true to "notification shown"
            }
            "SET_LOCK_MSG" -> {
                val msg = p.optString("message", "")
                val phone = p.optString("support_phone", "")
                if (msg.isNotEmpty()) Store.put(c, "lock_message", msg)
                if (phone.isNotEmpty()) Store.put(c, "shop_phone", phone)
                if (Store.bool(c, "locked")) showLockScreen(c)
                true to "lock message updated"
            }
            "RING_ALARM" -> {
                ringAlarm(c)
                true to "alarm rung"
            }
            "SET_LIVE_VIEW" -> {
                Store.putBool(c, "live_view_enabled", p.optBoolean("enabled", false))
                true to "live view state updated"
            }
            "BLOCK_DEV_OPT" -> devOptions(c, true)
            "UNBLOCK_DEV_OPT" -> devOptions(c, false)
            "SYNC_STATE" -> {
                sync(c, st)
                true to "synced"
            }
            "FACTORY_RESET" -> {
                val d = dpm(c)
                if (isAdmin(c)) {
                    d.wipeData(0)
                    true to "factory reset triggered"
                } else {
                    false to "Device Admin not active"
                }
            }
            else -> false to "unknown command $type"
        }
    } catch (e: Exception) {
        Log.e(TAG, "execute $type", e)
        false to (e.message ?: "error")
    }

    fun lock(c: Context) {
        Store.putBool(c, "locked", true)
        if (isAdmin(c)) {
            try {
                dpm(c).lockNow()
            } catch (e: Exception) {
                Log.w(TAG, "lockNow failed: ${e.message}")
            }
        }
        showLockScreen(c)
    }

    fun unlock(c: Context) {
        Store.putBool(c, "locked", false)
    }

    fun showLockScreen(c: Context) {
        try {
            c.startActivity(
                Intent(c, LockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (e: Exception) {
            Log.e(TAG, "lock screen: ${e.message}")
        }
    }

    fun sendCustomerNotification(c: Context, title: String, message: String) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "emi_customer_alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "EMI Alerts & Reminders", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(ch)
        }
        val n = NotificationCompat.Builder(c, channelId)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle(title.ifEmpty { "EMI Notice" })
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify((System.currentTimeMillis() % 90000 + 1000).toInt(), n)
    }

    fun ringAlarm(c: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val ringtone = RingtoneManager.getRingtone(c.applicationContext, uri)
            ringtone?.play()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(4000, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vib = c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createOneShot(4000, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(4000)
                }
            }

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    if (ringtone?.isPlaying == true) ringtone.stop()
                } catch (_: Exception) {
                }
            }, 8000L)
        } catch (e: Exception) {
            Log.w(TAG, "ringAlarm failed: ${e.message}")
        }
    }

    fun setWallpaper(c: Context, url: String, lock: Boolean): Pair<Boolean, String> {
        val bytes = URL(url).openStream().use { it.readBytes() }
        File(c.filesDir, "wallpaper.bin").writeBytes(bytes)
        Store.putBool(c, "wallpaper_locked", lock)
        applyFile(c)
        return true to "wallpaper applied, locked=$lock"
    }

    fun applyFile(c: Context) {
        val f = File(c.filesDir, "wallpaper.bin")
        if (!f.exists()) return
        val bmp = BitmapFactory.decodeFile(f.path) ?: return
        lastApply = System.currentTimeMillis()
        WallpaperManager.getInstance(c).setBitmap(
            bmp,
            null,
            true,
            WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        )
        lastApply = System.currentTimeMillis()
    }

    private fun devOptions(c: Context, block: Boolean): Pair<Boolean, String> {
        val d = dpm(c)
        if (!d.isDeviceOwnerApp(c.packageName)) {
            return false to "Requires Device Owner provisioning"
        }
        if (block) d.addUserRestriction(admin(c), UserManager.DISALLOW_DEBUGGING_FEATURES)
        else d.clearUserRestriction(admin(c), UserManager.DISALLOW_DEBUGGING_FEATURES)
        return true to "developer options ${if (block) "blocked" else "unblocked"}"
    }

    private fun sync(c: Context, st: JSONObject?) {
        st ?: return
        Store.putBool(c, "wallpaper_locked", st.optBoolean("wallpaper_locked"))
        val msg = st.optString("lock_message", "")
        val dueAmt = st.optString("due_amount", "")
        val nextDue = st.optString("next_due_date", "")
        val phone = st.optString("shop_phone", "")
        if (msg.isNotEmpty()) Store.put(c, "lock_message", msg)
        if (dueAmt.isNotEmpty()) Store.put(c, "due_amount", dueAmt)
        if (nextDue.isNotEmpty()) Store.put(c, "next_due_date", nextDue)
        if (phone.isNotEmpty()) Store.put(c, "shop_phone", phone)

        when (st.optString("lock_state")) {
            "LOCKED" -> lock(c)
            "UNLOCKED" -> if (Store.bool(c, "locked")) unlock(c)
        }
    }
}
