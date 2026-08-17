package com.kztutorial.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

object DeviceInfo {

    data class SimInfo(
        val slot: Int,
        val carrier: String,
        val displayName: String,
        val number: String?,
    )

    fun deviceLabel(): String {
        val manufacturer = Build.MANUFACTURER?.replaceFirstChar { it.uppercase() } ?: ""
        val model = Build.MODEL ?: ""
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model".trim()
        }
    }

    fun androidVersion(): String = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"

    fun appLabel(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        } catch (_: Exception) {
            packageName
        }
    }

    fun activeSims(context: Context): List<SimInfo> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
            as? SubscriptionManager ?: return emptyList()
        return try {
            @Suppress("MissingPermission")
            val list = sm.activeSubscriptionInfoList ?: return emptyList()
            list.map { sub ->
                val number = try {
                    @Suppress("DEPRECATION", "MissingPermission")
                    sub.number
                } catch (_: SecurityException) {
                    null
                }
                SimInfo(
                    slot = sub.simSlotIndex + 1,
                    carrier = sub.carrierName?.toString() ?: "Unknown",
                    displayName = sub.displayName?.toString() ?: "SIM ${sub.simSlotIndex + 1}",
                    number = number?.ifBlank { null },
                )
            }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    fun formatSims(context: Context): String {
        val sims = activeSims(context)
        if (sims.isEmpty()) return "📡 SIM: (izin belum diberikan / tidak ada SIM)"
        return sims.joinToString("\n") { s ->
            val num = s.number ?: "-"
            "📡 SIM ${s.slot} · ${s.carrier} · $num"
        }
    }

    /**
     * Guess default data SIM. Full per-notification SIM attribution requires
     * MSG_TYPE from the source app, which notifications don't expose.
     */
    fun defaultSmsSim(context: Context): SimInfo? {
        val defaultId = try {
            SubscriptionManager.getDefaultSmsSubscriptionId()
        } catch (_: Exception) {
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
        val sims = activeSims(context)
        return sims.firstOrNull { s ->
            // slot index matching is a best-effort heuristic
            defaultId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
        } ?: sims.firstOrNull()
    }
}
