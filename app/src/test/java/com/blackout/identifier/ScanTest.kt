package com.blackout.identifier

import com.blackout.identifier.data.AppInfo
import com.blackout.identifier.data.Check
import com.blackout.identifier.data.CheckId
import com.blackout.identifier.data.Level
import com.blackout.identifier.data.Risk
import com.blackout.identifier.data.Scan
import com.blackout.identifier.data.Status
import com.blackout.identifier.data.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanTest {
    private val now = 1_800_000_000_000L
    private val day = Risk.DAY_MS

    private fun check(id: CheckId, s: Status) = Check(id, id.name, "detail", s)
    private fun app(name: String, level: Level, tags: List<Tag>, last: Long? = now, autoOff: Boolean = false) =
        AppInfo(name, "pkg.$name", level, tags, last, autoOff)

    private val allPass = CheckId.values().map { check(it, Status.PASS) }

    @Test fun cleanPhoneScoresHundred() {
        val r = Scan.build(allPass, emptyList(), now)
        assertEquals(100, r.score)
        assertEquals("Good", r.grade)
        assertTrue(r.findings.isEmpty())
    }

    @Test fun deviceFailAndWarnCostPoints() {
        val checks = allPass.map {
            when (it.id) {
                CheckId.LOCK -> check(CheckId.LOCK, Status.FAIL)
                CheckId.PATCH -> check(CheckId.PATCH, Status.WARN)
                else -> it
            }
        }
        val r = Scan.build(checks, emptyList(), now)
        assertEquals(100 - 15 - 6, r.score)
        assertEquals(listOf(CheckId.LOCK, CheckId.PATCH), r.findings.map { it.fix.checkId })
    }

    @Test fun unknownChecksAreNotFindings() {
        val checks = allPass.map { if (it.id == CheckId.WIFI) check(CheckId.WIFI, Status.UNKNOWN) else it }
        val r = Scan.build(checks, emptyList(), now)
        assertEquals(100, r.score)
        assertEquals(1, r.couldNotCheck)
    }

    @Test fun highAppIsListedWorstFirstWithItsFix() {
        val apps = listOf(
            app("Calc", Level.CLEAN, emptyList()),
            app("Spy", Level.HIGH, listOf(Tag.MIC, Tag.BACKGROUND)),
        )
        val checks = allPass.map { if (it.id == CheckId.PATCH) check(CheckId.PATCH, Status.WARN) else it }
        val r = Scan.build(checks, apps, now)
        assertEquals(listOf("Spy", "PATCH"), r.findings.map { it.title })
        assertEquals("pkg.Spy", r.findings[0].fix.pkg)
        assertEquals("Can run in the background. Has: mic", r.findings[0].detail)
    }

    @Test fun unusedRiskyAppIsMediumButUsedOneIsNot() {
        val apps = listOf(
            app("Old", Level.MEDIUM, listOf(Tag.CAMERA), last = now - 45 * day),
            app("Fresh", Level.MEDIUM, listOf(Tag.CAMERA), last = now - 2 * day),
        )
        val r = Scan.build(allPass, apps, now)
        assertEquals(listOf("Old"), r.findings.map { it.title })
        assertEquals(Level.MEDIUM, r.findings[0].level)
        assertEquals(100 - Scan.APP_UNUSED_POINTS, r.score)
    }

    @Test fun appPointsAreCapped() {
        val apps = (1..30).map { app("A$it", Level.HIGH, listOf(Tag.OVERLAY)) }
        val r = Scan.build(allPass, apps, now)
        assertEquals(100 - Scan.APP_CAP, r.score)
        assertEquals(30, r.findings.size)
    }

    @Test fun scoreNeverGoesBelowZero() {
        val checks = CheckId.values().map { check(it, Status.FAIL) }
        val apps = (1..30).map { app("A$it", Level.HIGH, listOf(Tag.OVERLAY)) }
        assertEquals(0, Scan.build(checks, apps, now).score)
    }

    @Test fun usageAccessHintOnlyWhenUnknown() {
        assertFalse(Scan.build(allPass, listOf(app("X", Level.CLEAN, emptyList(), last = null)), now).unusedKnown)
        assertTrue(Scan.build(allPass, listOf(app("X", Level.CLEAN, emptyList(), last = 0L)), now).unusedKnown)
        assertTrue(Scan.build(allPass, emptyList(), now).unusedKnown)
    }

    @Test fun gradeBands() {
        assertEquals("Good", Scan.grade(85))
        assertEquals("Fair", Scan.grade(84))
        assertEquals("At risk", Scan.grade(40))
        assertEquals("Danger", Scan.grade(39))
    }

    @Test fun unusedAppWithAutoRemoveOffPointsToThatScreen() {
        val apps = listOf(app("Old", Level.MEDIUM, listOf(Tag.CAMERA), last = now - 45 * day, autoOff = true))
        val f = Scan.build(allPass, apps, now).findings.single()
        assertTrue(f.fix.autoRevoke)
        assertTrue(f.detail.contains("Auto-remove is off"))
    }

    @Test fun usedAppWithAutoRemoveOffIsLeftAlone() {
        val apps = listOf(app("Fresh", Level.HIGH, listOf(Tag.OVERLAY), last = now, autoOff = true))
        val f = Scan.build(allPass, apps, now).findings.single()
        assertFalse(f.fix.autoRevoke)
    }
}
