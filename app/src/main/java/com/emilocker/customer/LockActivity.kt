package com.emilocker.customer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class LockActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private val check = object : Runnable {
        override fun run() {
            if (!Store.bool(this@LockActivity, "locked")) finish() else h.postDelayed(this, 1200)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val shop = Store.get(this, "shop_phone")
        val msg = Store.get(
            this, "lock_message",
            "Your EMI payment is overdue. Please pay your installment and contact the store to unlock this phone."
        )
        val dueAmount = Store.get(this, "due_amount")
        val dueDate = Store.get(this, "due_date")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(56), dp(24), dp(32))
            setBackgroundColor(0xFF151A24.toInt())
        }

        // badge
        root.addView(label("SECURITY LOCK", 12f, Color.WHITE, true).apply {
            setPadding(dp(16), dp(6), dp(16), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(0xFFEF4444.toInt())
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        root.addView(label("\uD83D\uDD12", 72f, Color.WHITE).apply { setPadding(0, dp(24), 0, dp(8)) })
        root.addView(label("Device Locked", 28f, Color.WHITE, true))
        root.addView(label(msg, 16f, 0xFFB8C0D0.toInt()).apply { setPadding(0, dp(16), 0, dp(16)) })

        // info card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(0xFF1E2533.toInt())
            }
        }
        fun row(k: String, v: String) {
            card.addView(TextView(this).apply {
                text = "$k:  $v"
                textSize = 15f
                setTextColor(Color.WHITE)
                setPadding(0, dp(4), 0, dp(4))
            })
        }
        row("Device ID", Store.get(this, "device_uid", "-"))
        if (dueAmount.isNotEmpty()) row("Due Amount", dueAmount)
        if (dueDate.isNotEmpty()) row("Due Date", dueDate)
        row("Support Line", if (shop.isNotEmpty()) shop else "-")
        root.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        root.addView(Button(this).apply {
            text = "Call Store Support"
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(0xFF4F8CFF.toInt())
            }
            setOnClickListener {
                if (shop.isEmpty()) {
                    Toast.makeText(this@LockActivity, "Support number not available", Toast.LENGTH_SHORT).show()
                } else {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$shop")))
                }
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(20) })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFF151A24.toInt())
            isFillViewport = true
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        h.post(check)
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacks(check)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (!Store.bool(this, "locked")) super.onBackPressed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && Store.bool(this, "locked")) return true
        return super.onKeyDown(keyCode, event)
    }
}
