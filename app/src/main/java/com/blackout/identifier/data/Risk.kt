package com.blackout.identifier.data

/** Plain logic with no Android code, so it can be tested on its own. */

enum class Level(val label: String, val rank: Int) {
    HIGH("HIGH", 0), MEDIUM("MEDIUM", 1), LOW("LOW", 2), CLEAN("CLEAN", 3),
}

/** One thing worth showing on an app. `level` is how serious it is on its own. */
enum class Tag(val label: String, val level: Level) {
    OVERLAY("DRAWS OVER APPS", Level.HIGH),
    ACCESSIBILITY("ACCESSIBILITY", Level.HIGH),
    BG_LOCATION("LOCATION ALL THE TIME", Level.HIGH),
    MIC("MIC", Level.MEDIUM),
    CAMERA("CAMERA", Level.MEDIUM),
    LOCATION("LOCATION", Level.MEDIUM),
    SMS("SMS", Level.MEDIUM),
    CALLS("CALLS", Level.MEDIUM),
    ALL_FILES("ALL FILES", Level.MEDIUM),
    CONTACTS("CONTACTS", Level.LOW),
    STORAGE("STORAGE", Level.LOW),
    BACKGROUND("BACKGROUND", Level.CLEAN),
}

/** What we learned about one app. Only permissions that are actually granted are listed. */
class AppFacts(
    val label: String,
    val pkg: String,
    val granted: Set<String>,
    val overlay: Boolean,
    val accessibility: Boolean,
    /** Can start itself at boot or run a foreground service. */
    val backgroundCapable: Boolean,
    /** null = unknown (usage access not given), 0 = not used in the last year. */
    val lastUsedMs: Long?,
    /** True if Android's own "remove permissions if unused" is switched off for this app. */
    val autoRevokeOff: Boolean = false,
)

class AppInfo(
    val label: String, val pkg: String, val level: Level, val tags: List<Tag>, val lastUsedMs: Long?,
    val autoRevokeOff: Boolean = false,
)

enum class AppFilter { ALL, RISKY, BACKGROUND, UNUSED }

object Risk {
    const val DAY_MS = 86_400_000L
    const val UNUSED_DAYS = 30

    private val PERMISSION_TAGS: Map<String, Tag> = mapOf(
        "android.permission.CAMERA" to Tag.CAMERA,
        "android.permission.RECORD_AUDIO" to Tag.MIC,
        "android.permission.ACCESS_FINE_LOCATION" to Tag.LOCATION,
        "android.permission.ACCESS_COARSE_LOCATION" to Tag.LOCATION,
        "android.permission.ACCESS_BACKGROUND_LOCATION" to Tag.BG_LOCATION,
        "android.permission.READ_SMS" to Tag.SMS,
        "android.permission.SEND_SMS" to Tag.SMS,
        "android.permission.RECEIVE_SMS" to Tag.SMS,
        "android.permission.RECEIVE_MMS" to Tag.SMS,
        "android.permission.READ_CALL_LOG" to Tag.CALLS,
        "android.permission.WRITE_CALL_LOG" to Tag.CALLS,
        "android.permission.CALL_PHONE" to Tag.CALLS,
        "android.permission.PROCESS_OUTGOING_CALLS" to Tag.CALLS,
        "android.permission.MANAGE_EXTERNAL_STORAGE" to Tag.ALL_FILES,
        "android.permission.READ_CONTACTS" to Tag.CONTACTS,
        "android.permission.WRITE_CONTACTS" to Tag.CONTACTS,
        "android.permission.READ_EXTERNAL_STORAGE" to Tag.STORAGE,
        "android.permission.WRITE_EXTERNAL_STORAGE" to Tag.STORAGE,
        "android.permission.READ_MEDIA_IMAGES" to Tag.STORAGE,
        "android.permission.READ_MEDIA_VIDEO" to Tag.STORAGE,
        "android.permission.READ_MEDIA_AUDIO" to Tag.STORAGE,
    )

    fun assess(f: AppFacts): AppInfo {
        val found = HashSet<Tag>()
        f.granted.forEach { p -> PERMISSION_TAGS[p]?.let { found.add(it) } }
        if (f.overlay) found.add(Tag.OVERLAY)
        if (f.accessibility) found.add(Tag.ACCESSIBILITY)
        if (f.backgroundCapable) found.add(Tag.BACKGROUND)
        // Show worst first, in a fixed order.
        val tags = Tag.values().filter { it in found }.sortedBy { it.level.rank }
        var level = tags.firstOrNull()?.level ?: Level.CLEAN
        // Mic or camera in an app that can run in the background is the case worth warning about.
        if (f.backgroundCapable && (Tag.MIC in found || Tag.CAMERA in found)) level = Level.HIGH
        return AppInfo(f.label, f.pkg, level, tags, f.lastUsedMs, f.autoRevokeOff)
    }

    fun sorted(apps: List<AppInfo>): List<AppInfo> =
        apps.sortedWith(compareBy<AppInfo> { it.level.rank }.thenBy { it.label.lowercase() })

    fun isUnused(nowMs: Long, lastUsedMs: Long?): Boolean =
        lastUsedMs != null && nowMs - lastUsedMs > UNUSED_DAYS * DAY_MS

    fun matches(filter: AppFilter, app: AppInfo, nowMs: Long): Boolean = when (filter) {
        AppFilter.ALL -> true
        AppFilter.RISKY -> app.level == Level.HIGH || app.level == Level.MEDIUM
        AppFilter.BACKGROUND -> Tag.BACKGROUND in app.tags
        AppFilter.UNUSED -> isUnused(nowMs, app.lastUsedMs)
    }

    fun agoText(nowMs: Long, lastUsedMs: Long?): String {
        if (lastUsedMs == null) return "Last used: unknown"
        if (lastUsedMs == 0L) return "Not used in over a year"
        val days = (maxOf(0L, nowMs - lastUsedMs) / DAY_MS).toInt()
        return when (days) {
            0 -> "Last used today"
            1 -> "Last used yesterday"
            else -> "Last used $days days ago"
        }
    }
}
