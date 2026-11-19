package com.emilocker.customer

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Api {
    fun post(c: Context, path: String, body: JSONObject): JSONObject? = try {
        val rawBase = Store.get(c, "server_url").trim().trimEnd('/')
        val base = if (rawBase.endsWith("/api")) rawBase.removeSuffix("/api") else rawBase
        body.put("device_uid", Store.get(c, "device_uid").trim())
        body.put("device_token", Store.get(c, "device_token").trim())
        val cn = URL("$base/api/$path").openConnection() as HttpURLConnection
        cn.requestMethod = "POST"
        cn.connectTimeout = 15000
        cn.readTimeout = 15000
        cn.doOutput = true
        cn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        cn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = cn.responseCode
        val stream = if (code < 400) cn.inputStream else cn.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.readText() ?: "{}"
        JSONObject(text)
    } catch (e: Exception) {
        Log.e("Api", "$path failed: ${e.message}")
        null
    }
}
