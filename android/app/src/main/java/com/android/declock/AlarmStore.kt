package com.android.declock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar


data class AlarmItem(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val repeatMask: Int = 0,
    val label: String = "",
    val enabled: Boolean = true,
    val vibrate: Boolean = true
) {
    fun daysText(): String {
        if (repeatMask == 0) return "Hanya sekali"
        val names = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
        return names.indices.filter { repeatMask and (1 shl it) != 0 }.joinToString(", ") { names[it] }
    }
}

object AlarmStore {
    private const val PREF = "clock_alarms"
    private const val KEY = "items"

    fun load(context: Context): MutableList<AlarmItem> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        if (raw.isBlank()) return mutableListOf()
        return raw.split("|").mapNotNull { row ->
            val p = row.split("~")
            if (p.size < 7) return@mapNotNull null
            try {
                AlarmItem(p[0].toLong(), p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4].replace("%7E", "~"), p[5] == "1", p[6] == "1")
            } catch (_: Exception) { null }
        }.toMutableList()
    }

    fun save(context: Context, items: List<AlarmItem>) {
        val raw = items.joinToString("|") {
            "${it.id}~${it.hour}~${it.minute}~${it.repeatMask}~${it.label.replace("~", "%7E")}~${if (it.enabled) 1 else 0}~${if (it.vibrate) 1 else 0}"
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
    }

    fun find(context: Context, id: Long): AlarmItem? = load(context).firstOrNull { it.id == id }

    fun nextTrigger(item: AlarmItem, now: Calendar = Calendar.getInstance()): Calendar {
        val result = now.clone() as Calendar
        result.set(Calendar.SECOND, 0)
        result.set(Calendar.MILLISECOND, 0)
        result.set(Calendar.HOUR_OF_DAY, item.hour)
        result.set(Calendar.MINUTE, item.minute)
        if (item.repeatMask == 0) {
            if (!result.after(now)) result.add(Calendar.DAY_OF_YEAR, 1)
            return result
        }
        for (offset in 0..7) {
            val candidate = result.clone() as Calendar
            candidate.add(Calendar.DAY_OF_YEAR, offset)
            val dayIndex = (candidate.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Mon=0..Sun=6
            if (item.repeatMask and (1 shl dayIndex) != 0 && (offset > 0 || candidate.after(now))) return candidate
        }
        result.add(Calendar.DAY_OF_YEAR, 7)
        return result
    }

    fun schedule(context: Context, item: AlarmItem) {
        if (!item.enabled) return
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).putExtra("alarm_id", item.id)
        val pi = PendingIntent.getBroadcast(context, item.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val at = nextTrigger(item).timeInMillis
        try {
            if (Build.VERSION.SDK_INT >= 23) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else manager.setExact(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context, item: AlarmItem) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(context, item.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.cancel(pi)
    }
}
