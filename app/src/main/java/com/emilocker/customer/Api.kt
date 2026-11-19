package com.emilocker.customer

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Api {
    /** POST JSON to <base>/api/<path>. A trailing "/" and a trailing "/api" on server_url are stripped. */
    fun post(c: Context, path: String, body: JSONObject): JSONObject? = try {
        var base = Store.get(c, "server_url").trim().trimEnd('/')
        if (base.endsWith("/api")) base = base.removeSuffix("/api").trimEnd('/')
        body.put("device_uid", Store.get(c, "device_uid"))
        body.put("device_token", Store.get(c, "device_token"))
        val cn = URL("$base/api/$path").openConnection() as HttpURLConnection
        cn.requestMethod = "POST"
        cn.connectTimeout = 15000
        cn.readTimeout = 15000
        cn.doOutput = true
        cn.setRequestProperty("Content-Type", "application/json")
        cn.setRequestProperty("Accept", "application/json")
        cn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val stream = if (cn.responseCode < 400) cn.inputStream else cn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        cn.disconnect()
        JSONObject(text)
    } catch (e: Exception) {
        Log.e("Api", "$path failed: ${e.message}")
        null
    }
}
