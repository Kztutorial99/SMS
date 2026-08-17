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
 * Permission gatekeeper.
 *
 * Runtime permissions are requested with the Android permission dialog.
 * Special permissions use their dedicated Android Settings pages.
 * App Info is only used as a last-resort fallback when Android has made a
 * runtime permission permanently non-requestable.
 */
class PermissionActivity : AppCompatActivity() {

    private var runtimeRequestInFlight = false
    private var exactAlarmRequestInFlight = false
    private var notifListenerRequestInFlight = false

    private val runtimeLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        runtimeRequestInFlight = false
        // Always re-evaluate the real permission state. Do not redirect to
        // App Info merely because one request was denied.
        if (result.values.any { !it }) {
            val permanentlyDenied = result.keys.any { permission ->
                ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
            }
            if (permanentlyDenied) {
                Toast.makeText(
                    this,
                    "Izin ditolak permanen. Silakan aktifkan izin yang diperlukan di Pengaturan.",
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
    ) {
        exactAlarmRequestInFlight = false
        notifListenerRequestInFlight = false
        evaluate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        evaluate()
    }

    override fun onResume() {
        super.onResume()
        evaluate()
    }

    private fun evaluate() {
        if (isFinishing || isDestroyed) return

        // 1. Runtime permissions: Android shows the native permission dialog.
        val missingRuntime = missingRuntimePerms()
        if (missingRuntime.isNotEmpty()) {
            if (!runtimeRequestInFlight) {
                val permanentlyDenied = missingRuntime.any { permission ->
                    !ActivityCompat.shouldShowRequestPermissionRationale(this, permission) &&
                        hasRequestedBefore(permission)
                }

                if (permanentlyDenied) {
                    Toast.makeText(
                        this,
                        "Izin ini sudah ditolak permanen. Buka Pengaturan untuk mengaktifkannya.",
                        Toast.LENGTH_LONG
                    ).show()
                    openAppSettings()
                } else {
                    runtimeRequestInFlight = true
                    markRequested(missingRuntime)
                    runtimeLauncher.launch(missingRuntime.toTypedArray())
                }
            }
            return
        }

        // 2. Exact alarm is a special access and cannot be requested with
        // requestPermissions(). Use Android's dedicated page, not App Info.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms()) {
            if (!exactAlarmRequestInFlight) {
                exactAlarmRequestInFlight = true
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    settingsLauncher.launch(intent)
                } catch (_: Exception) {
                    exactAlarmRequestInFlight = false
                    openAppSettings()
                }
            }
            return
        }

        // 3. Notification Listener is another special access. Android does
        // not provide a normal runtime popup for it.
        if (!isNotificationListenerEnabled()) {
            if (!notifListenerRequestInFlight) {
                notifListenerRequestInFlight = true
                try {
                    settingsLauncher.launch(Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (_: Exception) {
                    notifListenerRequestInFlight = false
                    openAppSettings()
                }
            }
            return
        }

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

    private fun hasRequestedBefore(permission: String): Boolean =
        getPreferences(Context.MODE_PRIVATE).getBoolean("requested_$permission", false)

    private fun markRequested(permissions: List<String>) {
        getPreferences(Context.MODE_PRIVATE).edit().apply {
            permissions.forEach { putBoolean("requested_$it", true) }
        }.apply()
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            settingsLauncher.launch(intent)
        } catch (_: Exception) {
            // No further fallback is available on this device.
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
