package com.emilocker.customer

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject

class MainActivity : Activity() {
    private val fields = LinkedHashMap<String, EditText>()
    private lateinit var rootContainer: LinearLayout
    private lateinit var setupFormBox: LinearLayout
    private lateinit var lockedHomeBox: LinearLayout
    private lateinit var statusCardTv: TextView

    private fun panelDrawable(bgHex: Long, strokeHex: Long, radiusPx: Float = 24f): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(bgHex.toInt())
            setStroke(2, strokeHex.toInt())
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 40)
            setBackgroundColor(0xFF151A24.toInt())
        }

        val header = TextView(this).apply {
            text = "EMI Locker"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 12)
        }
        rootContainer.addView(header)

        statusCardTv = TextView(this).apply {
            textSize = 13.5f
            setLineSpacing(5f, 1.15f)
            setTextColor(0xFFE2E8F0.toInt())
            setPadding(24, 20, 24, 20)
            background = panelDrawable(0xFF1E2636.toInt(), 0x3360A5FA.toInt(), 20f)
        }
        val stParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 20 }
        rootContainer.addView(statusCardTv, stParams)

        setupFormBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            background = panelDrawable(0xFF1A202D.toInt(), 0x22FFFFFF.toInt(), 24f)
        }

        val defaultServer = Store.get(this, "server_url").ifEmpty { "https://salam.whf.bz/EMI" }
        val inputSpecs = listOf(
            "server_url" to "Server URL (e.g. https://salam.whf.bz/EMI)",
            "device_uid" to "EMI Device UID (e.g. DEVXXXXXX)",
            "device_token" to "Device Token",
            "customer_name" to "Customer Name",
            "customer_phone" to "Customer Phone"
        )

        inputSpecs.forEach { (key, hintText) ->
            val et = EditText(this).apply {
                hint = hintText
                isSingleLine = true
                setTextColor(Color.WHITE)
                setHintTextColor(0xFF8C9DB5.toInt())
                setPadding(24, 20, 24, 20)
                background = panelDrawable(0xFF121722.toInt(), 0x26FFFFFF.toInt(), 16f)
                setText(if (key == "server_url") defaultServer else Store.get(this@MainActivity, key))
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 14 }
            fields[key] = et
            setupFormBox.addView(et, lp)
        }

        fun addStepButton(label: String, accentHex: Long, action: () -> Unit) {
            val b = Button(this).apply {
                text = label
                textSize = 13.5f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(20, 22, 20, 22)
                background = panelDrawable(accentHex, 0x33FFFFFF.toInt(), 18f)
                setOnClickListener { action() }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
            setupFormBox.addView(b, lp)
        }

        addStepButton("1. Register Device & Lock Setup", 0xFF059669) { registerAndLockSetup() }
        addStepButton("2. Enable Device Admin", 0xFF2563EB) {
            startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, PolicyController.admin(this))
                    .putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Required so the shop can lock this device if EMI payments are overdue."
                    )
            )
        }
        addStepButton("3. Grant Location, Phone & Notification Permissions", 0xFF334155) {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_PHONE_STATE
            )
            if (Build.VERSION.SDK_INT >= 33) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            requestPermissions(perms.toTypedArray(), 1)
        }
        addStepButton("4. Allow Display Over Other Apps", 0xFF334155) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
            }
        }
        addStepButton("5. Start Protection Service", 0xFFD97706) {
            startProtectionServiceSafe()
        }

        rootContainer.addView(setupFormBox)

        lockedHomeBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 32, 28, 32)
            background = panelDrawable(0xFF1A202D.toInt(), 0x3310B981.toInt(), 24f)
        }

        val badgeTv = TextView(this).apply {
            text = "PROTECTED BY EMI LOCKER"
            textSize = 11.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF6EE7B7.toInt())
            setPadding(20, 8, 20, 8)
            background = panelDrawable(0x2210B981, 0x5510B981, 999f)
        }
        lockedHomeBox.addView(badgeTv)

        val infoTv = TextView(this).apply {
            text = "Device is enrolled under an active EMI plan.\nConfiguration is locked and managed remotely by the store."
            textSize = 14f
            setLineSpacing(5f, 1.15f)
            setTextColor(0xFFCBD5E1.toInt())
            gravity = Gravity.CENTER
            setPadding(12, 18, 12, 22)
        }
        lockedHomeBox.addView(infoTv)

        val callShopBtn = Button(this).apply {
            text = "📞  Contact Store Support"
            textSize = 14.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(24, 24, 24, 24)
            background = panelDrawable(0xFF2563EB, 0x33FFFFFF, 20f)
            setOnClickListener {
                val ph = Store.get(this@MainActivity, "shop_phone").ifEmpty { "01620230864" }
                try {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$ph")))
                } catch (_: Exception) {
                }
            }
        }
        lockedHomeBox.addView(
            callShopBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        rootContainer.addView(lockedHomeBox)
        setContentView(ScrollView(this).apply { addView(rootContainer) })
        updateModeVisibility()
    }

    override fun onResume() {
        super.onResume()
        if (Store.isSetupComplete(this)) {
            startProtectionServiceSafe()
            if (Store.bool(this, "locked")) {
                PolicyController.showLockScreen(this)
            }
        }
        updateModeVisibility()
        refreshStatus()
    }

    private fun updateModeVisibility() {
        val lockedSetup = Store.isSetupComplete(this)
        setupFormBox.visibility = if (lockedSetup) View.GONE else View.VISIBLE
        lockedHomeBox.visibility = if (lockedSetup) View.VISIBLE else View.GONE
    }

    private fun saveDraftFields() {
        fields.forEach { (k, e) ->
            Store.put(this, k, e.text.toString().trim())
        }
    }

    private fun startProtectionServiceSafe() {
        try {
            startForegroundService(Intent(this, LockerForegroundService::class.java))
        } catch (e: Exception) {
            Toast.makeText(this, "Grant permissions first: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun registerAndLockSetup() {
        saveDraftFields()
        val uid = Store.get(this, "device_uid").trim()
        val token = Store.get(this, "device_token").trim()
        val server = Store.get(this, "server_url").trim()
        if (uid.isEmpty() || token.isEmpty() || server.isEmpty()) {
            Toast.makeText(this, "Server URL, UID & Token required", Toast.LENGTH_SHORT).show()
            return
        }

        val body = JSONObject()
            .put("model_name", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("customer_name", Store.get(this, "customer_name"))
            .put("customer_phone", Store.get(this, "customer_phone"))

        Thread {
            val res = Api.post(this, "device_register.php", body)
            runOnUiThread {
                if (res?.optBoolean("ok") == true) {
                    res.optJSONObject("state")?.let { st ->
                        Store.put(this, "emi_status", st.optString("emi_status"))
                        Store.put(this, "lock_state", st.optString("lock_state"))
                        Store.put(this, "shop_phone", st.optString("shop_phone"))
                        Store.put(this, "lock_message", st.optString("lock_message"))
                        Store.put(this, "due_amount", st.optString("due_amount"))
                        Store.put(this, "next_due_date", st.optString("next_due_date"))
                    }
                    Store.putBool(this, "setup_locked", true)
                    startProtectionServiceSafe()
                    updateModeVisibility()
                    refreshStatus()
                    Toast.makeText(this, "Registered & Setup Locked ✔", Toast.LENGTH_SHORT).show()
                } else {
                    val err = res?.optString("error")?.ifEmpty { null } ?: "Cannot reach server"
                    Toast.makeText(this, "Registration failed: $err", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun refreshStatus() {
        val uid = Store.get(this, "device_uid").ifEmpty { "-" }
        val emi = Store.get(this, "emi_status").ifEmpty { "PENDING" }
        val lockSt = Store.get(this, "lock_state").ifEmpty { "UNLOCKED" }
        val setupState = if (Store.isSetupComplete(this)) "LOCKED (Admin Controlled)" else "OPEN (Setup Required)"
        val adminState = if (PolicyController.isAdmin(this)) "ON" else "OFF"
        val overlayState = if (Settings.canDrawOverlays(this)) "ON" else "OFF"

        statusCardTv.text =
            "Device UID: $uid\n" +
                "Setup Mode: $setupState\n" +
                "EMI Status: $emi  |  Lock State: $lockSt\n" +
                "Device Admin: $adminState  |  Overlay: $overlayState"
    }
}
