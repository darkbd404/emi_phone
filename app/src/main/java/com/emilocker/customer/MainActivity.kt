package com.emilocker.customer

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

class MainActivity : Activity() {
    private val fields = LinkedHashMap<String, EditText>()
    private lateinit var status: TextView
    private lateinit var setupFormBox: LinearLayout
    private lateinit var lockedHomeBox: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(0xFF1E2533.toInt())
        }
    }

    private fun lp(top: Int = 12) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun styledButton(t: String, color: Int = 0xFF4F8CFF.toInt(), onClick: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(color)
        }
        setOnClickListener { onClick() }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(40), dp(16), dp(24))
            setBackgroundColor(0xFF151A24.toInt())
        }

        root.addView(TextView(this).apply {
            text = "EMI Locker"
            textSize = 26f
            setTextColor(Color.WHITE)
        })

        // Status card
        val statusCard = card()
        status = TextView(this).apply {
            textSize = 15f
            setTextColor(0xFFE5E9F2.toInt())
            setLineSpacing(0f, 1.2f)
        }
        statusCard.addView(status)
        root.addView(statusCard, lp())

        // Setup form (hidden once setup is complete)
        setupFormBox = card()
        setupFormBox.addView(TextView(this).apply {
            text = "Initial Setup"
            textSize = 17f
            setTextColor(Color.WHITE)
        })
        val defaults = mapOf("server_url" to "https://salam.whf.bz/EMI")
        listOf(
            "server_url" to "Server URL",
            "device_uid" to "Device UID",
            "device_token" to "Device Token",
            "customer_name" to "Customer name",
            "customer_phone" to "Customer phone"
        ).forEach { (k, hint) ->
            val e = EditText(this).apply {
                this.hint = hint
                isSingleLine = true
                setTextColor(Color.WHITE)
                setHintTextColor(0xFF7B869A.toInt())
                setText(Store.get(this@MainActivity, k, defaults[k] ?: ""))
            }
            fields[k] = e
            setupFormBox.addView(e, lp(4))
        }
        setupFormBox.addView(styledButton("1. Register Device & Lock Setup") { register() }, lp(12))
        root.addView(setupFormBox, lp())

        // Protected home (shown after setup)
        lockedHomeBox = card()
        lockedHomeBox.addView(TextView(this).apply {
            text = "Your device is protected.\nSetup is locked and controlled by the store admin."
            textSize = 15f
            setTextColor(Color.WHITE)
        })
        lockedHomeBox.addView(styledButton("Call Store Support", 0xFF22C55E.toInt()) {
            val shop = Store.get(this, "shop_phone")
            if (shop.isEmpty()) Toast.makeText(this, "Support number not available yet", Toast.LENGTH_SHORT).show()
            else startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$shop")))
        }, lp())
        root.addView(lockedHomeBox, lp())

        // Permission / service actions
        val actions = card()
        actions.addView(styledButton("2. Enable Device Admin") {
            startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, PolicyController.admin(this))
                    .putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Lets the store lock this phone if your EMI payment is overdue."
                    )
            )
        }, lp(0))
        actions.addView(styledButton("3. Grant Location, Phone & Notification Permissions") {
            val p = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_PHONE_STATE
            )
            if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(p.toTypedArray(), 1)
        }, lp())
        actions.addView(styledButton("4. Allow Display Over Other Apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }, lp())
        actions.addView(styledButton("5. Start Protection Service") {
            saveFields()
            try {
                startForegroundService(Intent(this, LockerForegroundService::class.java))
                Toast.makeText(this, "Protection service started", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Grant location permission first: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, lp())
        root.addView(actions, lp())

        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFF151A24.toInt())
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun saveFields() {
        if (Store.isSetupComplete(this)) return // setup is locked
        fields.forEach { (k, e) -> Store.put(this, k, e.text.toString().trim()) }
    }

    private fun register() {
        saveFields()
        val missing = listOf("server_url", "device_uid", "device_token").filter { Store.get(this, it).isEmpty() }
        if (missing.isNotEmpty()) {
            Toast.makeText(this, "Fill in: ${missing.joinToString(", ")}", Toast.LENGTH_LONG).show()
            return
        }
        val b = JSONObject()
            .put("model_name", Build.MANUFACTURER + " " + Build.MODEL)
            .put("customer_name", Store.get(this, "customer_name"))
            .put("customer_phone", Store.get(this, "customer_phone"))
        Thread {
            val r = Api.post(this, "device_register.php", b)
            runOnUiThread {
                if (r?.optBoolean("ok") == true) {
                    PolicyController.saveState(this, r.optJSONObject("state"))
                    Store.putBool(this, "setup_locked", true)
                    Toast.makeText(this, "Registered. Setup locked.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this, "Registration failed: " + (r?.optString("error") ?: "no connection"), Toast.LENGTH_LONG
                    ).show()
                }
                refresh()
            }
        }.start()
    }

    private fun refresh() {
        val done = Store.isSetupComplete(this)
        setupFormBox.visibility = if (done) View.GONE else View.VISIBLE
        lockedHomeBox.visibility = if (done) View.VISIBLE else View.GONE
        val locked = Store.bool(this, "locked") || Store.get(this, "lock_state") == "LOCKED"
        status.text = "Device UID: ${Store.get(this, "device_uid", "-").ifEmpty { "-" }}\n" +
            "Setup Mode: ${if (done) "LOCKED (Admin Controlled)" else "OPEN (Setup Required)"}\n" +
            "EMI Status: ${Store.get(this, "emi_status", "?").ifEmpty { "?" }}\n" +
            "Lock State: ${if (locked) "LOCKED" else Store.get(this, "lock_state", "?").ifEmpty { "?" }}\n" +
            "Device Admin: ${if (PolicyController.isAdmin(this)) "ON" else "OFF"}\n" +
            "Overlay: ${if (Settings.canDrawOverlays(this)) "ON" else "OFF"}"
    }
}
