package com.android.declock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Vibrator
import android.os.VibrationEffect
import androidx.core.app.NotificationCompat
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("alarm_id", -1L)
        val alarm = AlarmStore.find(context, id) ?: return
        if (!alarm.enabled) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "alarms"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alarm aplikasi Jam"
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                enableVibration(alarm.vibrate)
                setBypassDnd(true)
            }
            manager.createNotificationChannel(channel)
        }

        val openIntent = PendingIntent.getActivity(context, id.hashCode(), Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_alarm", id)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val title = String.format("%02d:%02d", alarm.hour, alarm.minute)
        val text = if (alarm.label.isBlank()) "Alarm" else alarm.label
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        manager.notify(id.hashCode(), notification)

        if (alarm.vibrate) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 300, 500), -1))
            else @Suppress("DEPRECATION") vibrator.vibrate(longArrayOf(0, 500, 300, 500), -1)
        }

        val items = AlarmStore.load(context)
        if (alarm.repeatMask != 0) {
            AlarmStore.schedule(context, alarm)
        } else {
            val updated = items.map { if (it.id == id) it.copy(enabled = false) else it }
            AlarmStore.save(context, updated)
        }
    }
}
