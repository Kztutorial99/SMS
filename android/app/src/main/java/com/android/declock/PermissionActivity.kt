package com.android.declock

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Launcher activity. TANPA UI / splash / checklist.
 *
 * Flow:
 *  1. onCreate/onResume → evaluate().
 *  2. Kalau semua izin lengkap → langsung ke MainActivity.
 *  3. Kalau ada runtime permission yang belum → auto-launch dialog sistem.
 *  4. Kalau tinggal special-settings (Exact alarm / Notification Listener) →
 *     auto-launch halaman Settings terkait satu per satu.
 *  5. Kalau user tolak permanen → arahkan ke App Info.
 *
 * Tidak ada tombol "Lanjut". Tidak ada layar perantara.
 */
class PermissionActivity : AppCompatActivity() {

    private var runtimeAsked = false
    private var exactAlarmAsked = false
    private var notifListenerAsked = false

    private val runtimeLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val denied = result.filterValues { !it }.keys
        if (denied.isNotEmpty()) {
            val permanentlyDenied = denied.any {
                !ActivityCompat.shouldShowRequestPermissionRationale(this, it)
            }
            if (permanentlyDenied) {
                Toast.makeText(
                    this,
                    "Izin ditolak permanen. Aktifkan manual di Pengaturan aplikasi.",
                    Toast.LENGTH_LONG
                ).show()
                openAppSettings()
                return@registerForActivityResult
            }
        }
        evaluate()
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { evaluate() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        // Sengaja TIDAK setContentView — activity ini hanya gatekeeper permission.
        evaluate()
    }

    override fun onResume() {
        super.onResume()
        evaluate()
    }

    private fun evaluate() {
        // 1. Runtime permissions dulu.
        val missingRuntime = missingRuntimePerms()
        if (missingRuntime.isNotEmpty()) {
            if (!runtimeAsked) {
                runtimeAsked = true
                runtimeLauncher.launch(missingRuntime.toTypedArray())
            } else {
                // Sudah diminta tapi masih ditolak → arahkan ke App Settings.
                Toast.makeText(
                    this,
                    "Semua izin runtime harus diberikan.",
                    Toast.LENGTH_LONG
                ).show()
                openAppSettings()
            }
            return
        }

        // 2. Exact alarm (Android 12+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms()) {
            if (!exactAlarmAsked) {
                exactAlarmAsked = true
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    settingsLauncher.launch(intent)
                } catch (_: Exception) {
                    openAppSettings()
                }
            } else {
                Toast.makeText(
                    this,
                    "Aktifkan 'Alarm presisi' untuk aplikasi ini.",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        // 3. Notification listener.
        if (!isNotificationListenerEnabled()) {
            if (!notifListenerAsked) {
                notifListenerAsked = true
                try {
                    settingsLauncher.launch(Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (_: Exception) {
                    openAppSettings()
                }
            } else {
                Toast.makeText(
                    this,
                    "Aktifkan akses notifikasi untuk aplikasi ini.",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        // Semua lengkap.
        openMain()
    }

    private fun requiredRuntimePerms(): List<String> {
        val list = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        return list
    }

    private fun missingRuntimePerms(): List<String> = requiredRuntimePerms().filter {
        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }

    private fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val component = ComponentName(this, NotificationService::class.java)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getSystemService(android.app.NotificationManager::class.java)
                    .isNotificationListenerAccessGranted(component)
            } else {
                Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                    ?.split(":")
                    ?.any { it.equals(component.flattenToString(), ignoreCase = true) } == true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            settingsLauncher.launch(intent)
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
