package com.emilocker.customer

import android.content.Context

object Store {
    private fun p(c: Context) = c.getSharedPreferences("emi", Context.MODE_PRIVATE)
    fun get(c: Context, k: String, d: String = ""): String = p(c).getString(k, d) ?: d
    fun put(c: Context, k: String, v: String) = p(c).edit().putString(k, v).apply()
    fun bool(c: Context, k: String, d: Boolean = false): Boolean = p(c).getBoolean(k, d)
    fun putBool(c: Context, k: String, v: Boolean) = p(c).edit().putBoolean(k, v).apply()

    fun isSetupComplete(c: Context): Boolean {
        val uid = get(c, "device_uid").trim()
        val tok = get(c, "device_token").trim()
        val srv = get(c, "server_url").trim()
        return bool(c, "setup_locked", false) && uid.isNotEmpty() && tok.isNotEmpty() && srv.isNotEmpty()
    }
}
