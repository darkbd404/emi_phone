package com.emilocker.customer

import android.content.Context

object Store {
    private fun p(c: Context) = c.getSharedPreferences("emi", Context.MODE_PRIVATE)

    fun get(c: Context, k: String, d: String = ""): String = p(c).getString(k, d) ?: d
    fun put(c: Context, k: String, v: String) = p(c).edit().putString(k, v).apply()
    fun bool(c: Context, k: String): Boolean = p(c).getBoolean(k, false)
    fun putBool(c: Context, k: String, v: Boolean) = p(c).edit().putBoolean(k, v).apply()

    fun isSetupComplete(c: Context): Boolean =
        bool(c, "setup_locked") &&
            get(c, "device_uid").isNotEmpty() &&
            get(c, "device_token").isNotEmpty() &&
            get(c, "server_url").isNotEmpty()
}
