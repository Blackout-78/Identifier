package com.blackout.identifier.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings

/** Reads the installed apps and their permissions. Everything stays on this phone. */
object AppScanner {
    private const val OVERLAY = "android.permission.SYSTEM_ALERT_WINDOW"
    private const val BOOT = "android.permission.RECEIVE_BOOT_COMPLETED"
    private const val FOREGROUND = "android.permission.FOREGROUND_SERVICE"

    /** Whether you gave Identifier "usage access", which is needed to see when apps were last used. */
    @Suppress("DEPRECATION")
    fun hasUsageAccess(context: Context): Boolean = runCatching {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun installed(pm: PackageManager): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= 33) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        else pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

    private fun accessibilityPackages(context: Context): Set<String> = runCatching {
        val raw = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        raw.split(':').filter { it.contains('/') }.map { it.substringBefore('/') }.toSet()
    }.getOrDefault(emptySet())

    private fun lastUsed(context: Context): Map<String, Long>? {
        if (!hasUsageAccess(context)) return null
        return runCatching {
            val now = System.currentTimeMillis()
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val result = HashMap<String, Long>()
            usm.queryUsageStats(UsageStatsManager.INTERVAL_YEARLY, now - 365 * Risk.DAY_MS, now).forEach { s ->
                if (s.lastTimeUsed > (result[s.packageName] ?: 0L)) result[s.packageName] = s.lastTimeUsed
            }
            result
        }.getOrNull()
    }

    /** Android 11+ only. Apps on the exempt list keep their permissions even when unused. */
    private fun autoRevokeOff(pm: PackageManager, pkg: String): Boolean =
        Build.VERSION.SDK_INT >= 30 && runCatching { pm.isAutoRevokeWhitelisted(pkg) }.getOrDefault(false)

    @Suppress("DEPRECATION")
    fun scan(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val accessibility = accessibilityPackages(context)
        val usage = lastUsed(context)
        val out = ArrayList<AppInfo>()
        for (p in installed(pm)) {
            val ai = p.applicationInfo ?: continue
            if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue // phone's own apps are not listed
            if (p.packageName == context.packageName) continue
            val requested = p.requestedPermissions ?: emptyArray()
            val flags = p.requestedPermissionsFlags
            val granted = HashSet<String>()
            for (i in requested.indices) {
                if (flags != null && i < flags.size && flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0) granted.add(requested[i])
            }
            val overlay = OVERLAY in requested &&
                runCatching { ops.checkOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, ai.uid, p.packageName) == AppOpsManager.MODE_ALLOWED }.getOrDefault(false)
            val label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(p.packageName)
            out.add(
                Risk.assess(
                    AppFacts(
                        label = label,
                        pkg = p.packageName,
                        granted = granted,
                        overlay = overlay,
                        accessibility = p.packageName in accessibility,
                        backgroundCapable = BOOT in requested || FOREGROUND in requested,
                        lastUsedMs = if (usage == null) null else (usage[p.packageName] ?: 0L),
                        autoRevokeOff = autoRevokeOff(pm, p.packageName),
                    ),
                ),
            )
        }
        return Risk.sorted(out)
    }
}
