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
 * Runtime permissions use Android's native permission dialog.
 * Special accesses use their dedicated Settings pages.
 * App Info is NOT used as part of the normal permission flow.
 */
class PermissionActivity : AppCompatActivity() {

    private var runtimeRequestInFlight = false
    private var specialAccessInFlight = false

    private val runtimeLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        runtimeRequestInFlight = false

        val denied = result.filterValues { granted -> !granted }.keys
        if (denied.isNotEmpty()) {
            val permanentlyDenied = denied.any { permission ->
                ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(this, permission) &&
                    hasRequestedBefore(permission)
            }

            if (permanentlyDenied) {
                Toast.makeText(
                    this,
                    "Sebagian izin ditolak permanen. Aktifkan izin tersebut di Pengaturan jika ingin menggunakan semua fitur.",
                    Toast.LENGTH_LONG
                ).show()
                // Do not force App Info immediately. User can continue to the
                // app; a later attempt can open Settings when truly necessary.
            }
        }

        evaluate()
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        specialAccessInFlight = false
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
        // If a launcher is already active, do not start another request or
        // accidentally redirect to Settings while Android is showing a dialog.
        if (!runtimeRequestInFlight && !specialAccessInFlight) {
            evaluate()
        }
    }

    private fun evaluate() {
        if (isFinishing || isDestroyed || runtimeRequestInFlight || specialAccessInFlight) return

        // 1. Runtime permissions -> native Android permission dialog.
        val missingRuntime = missingRuntimePerms()
        if (missingRuntime.isNotEmpty()) {
            val requestable = missingRuntime.filter { permission ->
                !hasRequestedBefore(permission) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
            }

            if (requestable.isNotEmpty()) {
                runtimeRequestInFlight = true
                markRequested(requestable)
                runtimeLauncher.launch(requestable.toTypedArray())
                return
            }

            // Android has stopped offering a runtime dialog for these
            // permissions. Do NOT automatically open App Info. The user can
            // still use the app, and Settings can be opened from an explicit
            // action elsewhere in the UI if needed.
        }

        // 2. Exact alarm is a special access. Never treat it as a runtime
        // permission and never send the user to App Info for it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms()) {
            if (!specialAccessInFlight) {
                specialAccessInFlight = true
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    settingsLauncher.launch(intent)
                } catch (_: Exception) {
                    specialAccessInFlight = false
                    openExactAlarmSettingsFallback()
                }
            }
            return
        }

        // 3. Notification Listener is a special access. Android provides no
        // normal runtime permission dialog for it.
        if (!isNotificationListenerEnabled()) {
            if (!specialAccessInFlight) {
                specialAccessInFlight = true
                try {
                    settingsLauncher.launch(Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (_: Exception) {
                    specialAccessInFlight = false
                    openAppSettingsAsLastResort()
                }
            }
            return
        }

        openMain()
    }

    private fun requiredRuntimePerms(): List<String> {
        val permissions = mutableListOf<String>()

        permissions += Manifest.permission.READ_PHONE_STATE

        // READ_PHONE_NUMBERS was introduced in API 26. Keep the declaration
        // in the manifest, but only request it where Android supports it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissions += Manifest.permission.READ_PHONE_NUMBERS
        }

        // Notification runtime permission exists only on Android 13+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        return permissions
    }

    private fun missingRuntimePerms(): List<String> = requiredRuntimePerms().filter {
        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }

    private fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
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

    private fun openExactAlarmSettingsFallback() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (_: Exception) {
            // No generic App Info redirect here: this is a special-access flow.
        }
    }

    private fun openAppSettingsAsLastResort() {
        // Only used when Android does not expose the dedicated Notification
        // Listener settings activity. This is not part of the normal flow.
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (_: Exception) {
            // Nothing else to do on this device.
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
