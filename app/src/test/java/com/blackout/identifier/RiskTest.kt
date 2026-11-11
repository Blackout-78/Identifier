package com.blackout.identifier

import com.blackout.identifier.data.AppFacts
import com.blackout.identifier.data.AppFilter
import com.blackout.identifier.data.Level
import com.blackout.identifier.data.Risk
import com.blackout.identifier.data.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskTest {
    private val day = Risk.DAY_MS
    private val now = 1_000 * day

    private fun facts(
        label: String = "App",
        granted: Set<String> = emptySet(),
        overlay: Boolean = false,
        accessibility: Boolean = false,
        background: Boolean = false,
        lastUsed: Long? = now,
    ) = AppFacts(label, "pkg.$label", granted, overlay, accessibility, background, lastUsed)

    private val cam = "android.permission.CAMERA"
    private val mic = "android.permission.RECORD_AUDIO"

    @Test fun appWithNothingIsClean() {
        val r = Risk.assess(facts())
        assertEquals(Level.CLEAN, r.level)
        assertTrue(r.tags.isEmpty())
    }

    @Test fun cameraAloneIsMedium() {
        assertEquals(Level.MEDIUM, Risk.assess(facts(granted = setOf(cam))).level)
    }

    @Test fun micInABackgroundAppIsHigh() {
        val r = Risk.assess(facts(granted = setOf(mic), background = true))
        assertEquals(Level.HIGH, r.level)
        assertTrue(Tag.MIC in r.tags)
        assertTrue(Tag.BACKGROUND in r.tags)
    }

    @Test fun cameraInABackgroundAppIsHigh() {
        assertEquals(Level.HIGH, Risk.assess(facts(granted = setOf(cam), background = true)).level)
    }

    @Test fun backgroundAloneIsNotARisk() {
        val r = Risk.assess(facts(background = true))
        assertEquals(Level.CLEAN, r.level)
        assertEquals(listOf(Tag.BACKGROUND), r.tags)
    }

    @Test fun overlayAndAccessibilityAreHigh() {
        assertEquals(Level.HIGH, Risk.assess(facts(overlay = true)).level)
        assertEquals(Level.HIGH, Risk.assess(facts(accessibility = true)).level)
    }

    @Test fun backgroundLocationIsHigh() {
        assertEquals(Level.HIGH, Risk.assess(facts(granted = setOf("android.permission.ACCESS_BACKGROUND_LOCATION"))).level)
    }

    @Test fun contactsAndStorageAreLow() {
        assertEquals(Level.LOW, Risk.assess(facts(granted = setOf("android.permission.READ_CONTACTS"))).level)
        assertEquals(Level.LOW, Risk.assess(facts(granted = setOf("android.permission.READ_MEDIA_IMAGES"))).level)
    }

    @Test fun unknownPermissionsAreIgnored() {
        assertEquals(Level.CLEAN, Risk.assess(facts(granted = setOf("android.permission.VIBRATE", "com.example.CUSTOM"))).level)
    }

    @Test fun worstTagComesFirst() {
        val r = Risk.assess(facts(granted = setOf("android.permission.READ_CONTACTS", cam), overlay = true))
        assertEquals(Tag.OVERLAY, r.tags.first())
        assertEquals(Tag.CONTACTS, r.tags.last())
    }

    @Test fun sameLocationPermissionsShowOnce() {
        val r = Risk.assess(facts(granted = setOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION")))
        assertEquals(1, r.tags.count { it == Tag.LOCATION })
    }

    @Test fun sortingPutsWorstFirstThenByName() {
        val list = listOf(
            Risk.assess(facts(label = "zeta")),
            Risk.assess(facts(label = "Beta", granted = setOf(cam))),
            Risk.assess(facts(label = "alpha", granted = setOf(cam))),
            Risk.assess(facts(label = "omega", overlay = true)),
        )
        assertEquals(listOf("omega", "alpha", "Beta", "zeta"), Risk.sorted(list).map { it.label })
    }

    @Test fun agoTextCoversEveryCase() {
        assertEquals("Last used: unknown", Risk.agoText(now, null))
        assertEquals("Not used in over a year", Risk.agoText(now, 0L))
        assertEquals("Last used today", Risk.agoText(now, now - day / 2))
        assertEquals("Last used yesterday", Risk.agoText(now, now - day))
        assertEquals("Last used 41 days ago", Risk.agoText(now, now - 41 * day))
        // A clock that moved backwards must never show a negative number.
        assertEquals("Last used today", Risk.agoText(now, now + 5 * day))
    }

    @Test fun unusedMeansOverThirtyDays() {
        assertFalse(Risk.isUnused(now, now - 30 * day))
        assertTrue(Risk.isUnused(now, now - 31 * day))
        assertTrue(Risk.isUnused(now, 0L))
        assertFalse(Risk.isUnused(now, null))
    }

    @Test fun filtersPickTheRightApps() {
        val risky = Risk.assess(facts(granted = setOf(cam)))
        val bg = Risk.assess(facts(background = true))
        val old = Risk.assess(facts(lastUsed = now - 90 * day))
        val low = Risk.assess(facts(granted = setOf("android.permission.READ_CONTACTS")))
        assertTrue(Risk.matches(AppFilter.RISKY, risky, now))
        assertFalse(Risk.matches(AppFilter.RISKY, low, now))
        assertTrue(Risk.matches(AppFilter.BACKGROUND, bg, now))
        assertFalse(Risk.matches(AppFilter.BACKGROUND, risky, now))
        assertTrue(Risk.matches(AppFilter.UNUSED, old, now))
        assertFalse(Risk.matches(AppFilter.UNUSED, risky, now))
        assertTrue(Risk.matches(AppFilter.ALL, low, now))
    }
}
