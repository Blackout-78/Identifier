package com.blackout.identifier.data

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import java.time.LocalDate

/** Reads the phone's own settings. Needs no internet and nothing is stored or sent. */
object DeviceScanner {
    fun read(context: Context): DeviceFacts {
        val cr = context.contentResolver
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        return DeviceFacts(
            lockSet = keyguard?.isDeviceSecure == true,
            encryptionStatus = runCatching { dpm?.storageEncryptionStatus }.getOrNull(),
            patch = Build.VERSION.SECURITY_PATCH.takeIf { it.isNotBlank() },
            verifierOn = runCatching { Settings.Global.getInt(cr, "package_verifier_enable") }.getOrNull(),
            devOptionsOn = Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1,
            usbDebuggingOn = Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) == 1,
            wifiSecurity = wifiSecurity(context),
        )
    }

    fun check(context: Context): List<Check> =
        DeviceChecks.evaluate(read(context), LocalDate.now().toEpochDay())

    @Suppress("DEPRECATION")
    private fun wifiSecurity(context: Context): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        return runCatching {
            val type = wm.connectionInfo.currentSecurityType
            if (type == WifiInfo.SECURITY_TYPE_UNKNOWN) DeviceChecks.WIFI_NONE else type
        }.getOrNull()
    }
}
