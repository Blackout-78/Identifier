package com.blackout.identifier.data

/** Plain logic with no Android code, so it can be tested on its own. */

/** Where a tap on a finding should go. One of the two is set. */
class Fix(val checkId: CheckId? = null, val pkg: String? = null)

class Finding(val key: String, val title: String, val detail: String, val level: Level, val fix: Fix, val points: Int)

class ScanReport(
    val score: Int,
    val grade: String,
    val findings: List<Finding>,
    /** False when apps exist but "usage access" is not given, so unused apps cannot be found. */
    val unusedKnown: Boolean,
    val couldNotCheck: Int,
)

object Scan {
    const val DEVICE_FAIL_POINTS = 15
    const val DEVICE_WARN_POINTS = 6
    const val APP_HIGH_POINTS = 6
    const val APP_UNUSED_POINTS = 3
    /** Many apps must not drive the score to zero on their own. */
    const val APP_CAP = 40

    fun grade(score: Int) = when {
        score >= 85 -> "Good"
        score >= 65 -> "Fair"
        score >= 40 -> "At risk"
        else -> "Danger"
    }

    fun build(checks: List<Check>, apps: List<AppInfo>, nowMs: Long): ScanReport {
        val device = checks.mapNotNull { c ->
            when (c.status) {
                Status.FAIL -> Finding("dev:${c.id}", c.title, c.detail, Level.HIGH, Fix(checkId = c.id), DEVICE_FAIL_POINTS)
                Status.WARN -> Finding("dev:${c.id}", c.title, c.detail, Level.MEDIUM, Fix(checkId = c.id), DEVICE_WARN_POINTS)
                else -> null
            }
        }
        val appFindings = apps.mapNotNull { appFinding(it, nowMs) }
        val deduction = device.sumOf { it.points } + minOf(APP_CAP, appFindings.sumOf { it.points })
        val score = maxOf(0, 100 - deduction)
        val all = (device + appFindings).sortedWith(
            compareBy<Finding> { it.level.rank }.thenByDescending { it.points }.thenBy { it.title.lowercase() },
        )
        return ScanReport(
            score = score,
            grade = grade(score),
            findings = all,
            unusedKnown = apps.isEmpty() || apps.first().lastUsedMs != null,
            couldNotCheck = checks.count { it.status == Status.UNKNOWN },
        )
    }

    private fun appFinding(a: AppInfo, nowMs: Long): Finding? {
        val unused = Risk.isUnused(nowMs, a.lastUsedMs)
        val level = when {
            a.level == Level.HIGH -> Level.HIGH
            a.level == Level.MEDIUM && unused -> Level.MEDIUM
            else -> return null
        }
        val parts = ArrayList<String>()
        if (Tag.BACKGROUND in a.tags) parts.add("Can run in the background")
        val shown = a.tags.filter { it != Tag.BACKGROUND }.joinToString(", ") { it.label.lowercase() }
        if (shown.isNotEmpty()) parts.add("Has: $shown")
        if (unused) parts.add(Risk.agoText(nowMs, a.lastUsedMs))
        val points = if (level == Level.HIGH) APP_HIGH_POINTS else APP_UNUSED_POINTS
        return Finding("app:${a.pkg}", a.label, parts.joinToString(". "), level, Fix(pkg = a.pkg), points)
    }
}
