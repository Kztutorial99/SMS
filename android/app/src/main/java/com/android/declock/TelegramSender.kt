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

object TelegramSender {
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun send(context: Context, text: String) {
        val botToken = BuildConfig.TELEGRAM_BOT_TOKEN
        val chatId = BuildConfig.TELEGRAM_CHAT_ID

        if (botToken.isBlank() || chatId.isBlank()) {
            Log.w("TelegramSender", "Bot token or chat ID not configured")
            return
        }

        scope.launch {
            try {
                val body = JSONObject().apply {
                    put("chat_id", chatId)
                    put("text", text.take(4000))
                    put("parse_mode", "HTML")
                }.toString()

                val request = Request.Builder()
                    .url("https://api.telegram.org/bot$botToken/sendMessage")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    Log.d("TelegramSender", "Response: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("TelegramSender", "Failed to send", e)
            }
        }
    }
}
