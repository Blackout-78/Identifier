package com.blackout.identifier.data

import java.time.LocalDate

enum class Status(val label: String) { PASS("PASS"), WARN("WARN"), FAIL("FAIL"), UNKNOWN("N/A") }

enum class CheckId { LOCK, ENCRYPTION, PATCH, PLAY_PROTECT, DEV_OPTIONS, WIFI }

class Check(val id: CheckId, val title: String, val detail: String, val status: Status)

/**
 * What the phone told us. A null means "could not read it", never "bad".
 * Kept free of Android classes so it can be tested on a plain computer.
 */
class DeviceFacts(
    val lockSet: Boolean,
    val encryptionStatus: Int?,   // DevicePolicyManager.ENCRYPTION_STATUS_* value
    val patch: String?,           // "YYYY-MM-DD"
    val verifierOn: Int?,         // Settings.Global package_verifier_enable (1 on, 0 off)
    val devOptionsOn: Boolean,
    val usbDebuggingOn: Boolean,
    val wifiSecurity: Int?,       // WifiInfo.SECURITY_TYPE_* value, -1 = not connected, null = cannot read
)

object DeviceChecks {
    const val ENC_UNSUPPORTED = 0
    const val ENC_INACTIVE = 1
    const val ENC_ACTIVATING = 2
    const val ENC_ACTIVE = 3
    const val ENC_ACTIVE_DEFAULT_KEY = 4
    const val ENC_ACTIVE_PER_USER = 5

    const val WIFI_NONE = -1
    const val WIFI_OPEN = 0
    const val WIFI_WEP = 1

    const val PATCH_OK_DAYS = 90
    const val PATCH_OLD_DAYS = 365

    fun evaluate(f: DeviceFacts, todayEpochDay: Long): List<Check> = listOf(
        lock(f), encryption(f), patch(f, todayEpochDay), playProtect(f), devOptions(f), wifi(f),
    )

    fun passed(list: List<Check>) = list.count { it.status == Status.PASS }

    fun summary(list: List<Check>): String {
        val unknown = list.count { it.status == Status.UNKNOWN }
        val base = "${passed(list)} of ${list.size} checks passed"
        return if (unknown > 0) "$base, $unknown could not be checked" else base
    }

    private fun lock(f: DeviceFacts) =
        if (f.lockSet) Check(CheckId.LOCK, "Screen lock", "A PIN, pattern or password is set", Status.PASS)
        else Check(CheckId.LOCK, "Screen lock", "No screen lock. Anyone holding the phone can open it", Status.FAIL)

    private fun encryption(f: DeviceFacts): Check {
        val (detail, s) = when (f.encryptionStatus) {
            ENC_ACTIVE, ENC_ACTIVE_DEFAULT_KEY, ENC_ACTIVE_PER_USER -> "Storage is encrypted" to Status.PASS
            ENC_INACTIVE -> "Storage is not encrypted" to Status.FAIL
            ENC_ACTIVATING -> "Encryption is still starting" to Status.WARN
            else -> "Could not be read on this phone" to Status.UNKNOWN
        }
        return Check(CheckId.ENCRYPTION, "Encryption", detail, s)
    }

    /** Age in days of the phone's security patch, or null if the date is not readable. */
    fun patchAgeDays(patch: String?, todayEpochDay: Long): Long? {
        val date = runCatching { LocalDate.parse(patch ?: return null) }.getOrNull() ?: return null
        return todayEpochDay - date.toEpochDay()
    }

    private fun patch(f: DeviceFacts, today: Long): Check {
        val age = patchAgeDays(f.patch, today)
            ?: return Check(CheckId.PATCH, "Security patch", "Could not be read on this phone", Status.UNKNOWN)
        val ago = if (age <= 0) "this month" else "$age days old"
        val s = when {
            age <= PATCH_OK_DAYS -> Status.PASS
            age <= PATCH_OLD_DAYS -> Status.WARN
            else -> Status.FAIL
        }
        val detail = when (s) {
            Status.PASS -> "Up to date (${f.patch}, $ago)"
            Status.WARN -> "Getting old (${f.patch}, $ago)"
            else -> "Over a year old (${f.patch}). Check for updates"
        }
        return Check(CheckId.PATCH, "Security patch", detail, s)
    }

    private fun playProtect(f: DeviceFacts): Check = when (f.verifierOn) {
        null -> Check(CheckId.PLAY_PROTECT, "Play Protect", "Could not be read on this phone", Status.UNKNOWN)
        0 -> Check(CheckId.PLAY_PROTECT, "Play Protect", "App scanning is off", Status.FAIL)
        else -> Check(CheckId.PLAY_PROTECT, "Play Protect", "App scanning is on", Status.PASS)
    }

    private fun devOptions(f: DeviceFacts): Check = when {
        f.usbDebuggingOn -> Check(CheckId.DEV_OPTIONS, "Developer options", "USB debugging is on. Tap to turn off", Status.WARN)
        f.devOptionsOn -> Check(CheckId.DEV_OPTIONS, "Developer options", "On. Tap to turn off", Status.WARN)
        else -> Check(CheckId.DEV_OPTIONS, "Developer options", "Off", Status.PASS)
    }

    private fun wifi(f: DeviceFacts): Check = when (val w = f.wifiSecurity) {
        null -> Check(CheckId.WIFI, "Wi-Fi safety", "Needs Android 12 or newer to check", Status.UNKNOWN)
        WIFI_NONE -> Check(CheckId.WIFI, "Wi-Fi safety", "Not connected to Wi-Fi", Status.PASS)
        WIFI_OPEN -> Check(CheckId.WIFI, "Wi-Fi safety", "Connected to an open network", Status.FAIL)
        WIFI_WEP -> Check(CheckId.WIFI, "Wi-Fi safety", "Old WEP protection. Easy to break", Status.FAIL)
        else -> Check(CheckId.WIFI, "Wi-Fi safety", "Connected to a protected network", Status.PASS)
    }
}
