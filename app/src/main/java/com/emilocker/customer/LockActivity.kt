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
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class LockActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private val check = object : Runnable {
        override fun run() {
            if (!Store.bool(this@LockActivity, "locked")) {
                finish()
            } else {
                h.postDelayed(this, 1200)
            }
        }
    }

    private fun cardBg(fillColor: Int, strokeColor: Int, radiusPx: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(fillColor)
            setStroke(2, strokeColor)
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val shop = Store.get(this, "shop_phone").ifEmpty { "01620230864" }
        val uid = Store.get(this, "device_uid").ifEmpty { "UNASSIGNED" }
        val customMsg = Store.get(this, "lock_message").trim().ifEmpty {
            "Your EMI installment is overdue. Please clear your pending due to restore full device access."
        }
        val dueAmt = Store.get(this, "due_amount").trim()
        val nextDue = Store.get(this, "next_due_date").trim()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(44, 64, 44, 64)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF141822.toInt(), 0xFF1E1318.toInt(), 0xFF11151E.toInt())
            )
        }

        val mainCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 44, 40, 44)
            background = cardBg(0xFF1B2230.toInt(), 0x55EF4444.toInt(), 36f)
        }

        val badge = TextView(this).apply {
            text = "EMI SECURITY LOCK"
            textSize = 11.5f
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.14f
            setTextColor(0xFFFCA5A5.toInt())
            setPadding(24, 10, 24, 10)
            background = cardBg(0x33EF4444.toInt(), 0x66EF4444.toInt(), 999f)
            gravity = Gravity.CENTER
        }
        mainCard.addView(badge)

        val iconBadge = TextView(this).apply {
            text = "🔒"
            textSize = 42f
            gravity = Gravity.CENTER
            setPadding(0, 22, 0, 8)
        }
        mainCard.addView(iconBadge)

        val titleTv = TextView(this).apply {
            text = "Device Locked"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 12)
        }
        mainCard.addView(titleTv)

        val msgTv = TextView(this).apply {
            text = customMsg
            textSize = 15f
            setLineSpacing(6f, 1.15f)
            setTextColor(0xFFE2E8F0.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 22)
        }
        mainCard.addView(msgTv)

        val metaBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 22, 28, 22)
            background = cardBg(0xFF121722.toInt(), 0x22FFFFFF.toInt(), 24f)
        }

        fun addInfoRow(label: String, value: String, highlightColor: Int = 0xFFF8FAFC.toInt()) {
            val row = TextView(this).apply {
                text = "$label:  $value"
                textSize = 13.5f
                setTextColor(highlightColor)
                setTypeface(typeface, if (highlightColor != 0xFFF8FAFC.toInt()) Typeface.BOLD else Typeface.NORMAL)
                setPadding(0, 5, 0, 5)
            }
            metaBox.addView(row)
        }

        addInfoRow("Device ID", uid, 0xFF93C5FD.toInt())
        if (dueAmt.isNotEmpty() && dueAmt != "0" && dueAmt != "0.00") {
            addInfoRow("Due Amount", dueAmt, 0xFFFBBF24.toInt())
        }
        if (nextDue.isNotEmpty()) {
            addInfoRow("Due Date", nextDue, 0xFFFCA5A5.toInt())
        }
        addInfoRow("Support Line", shop, 0xFF6EE7B7.toInt())

        val metaParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 26 }
        mainCard.addView(metaBox, metaParams)

        val callBtn = Button(this).apply {
            text = "📞  Call Store Support ($shop)"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(28, 28, 28, 28)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFF2563EB.toInt(), 0xFF1D4ED8.toInt())
            ).apply { cornerRadius = 24f }
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$shop")))
                } catch (_: Exception) {
                }
            }
        }
        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 14 }
        mainCard.addView(callBtn, btnParams)

        val hintTv = TextView(this).apply {
            text = "Once payment is verified by the store, your device will unlock automatically."
            textSize = 12f
            setTextColor(0xFF94A3B8.toInt())
            gravity = Gravity.CENTER
            setPadding(8, 4, 8, 0)
        }
        mainCard.addView(hintTv)

        root.addView(
            mainCard,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(root)
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        h.post(check)
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacks(check)
    }

    @Deprecated("Back disabled while locked")
    override fun onBackPressed() {
    }
}
