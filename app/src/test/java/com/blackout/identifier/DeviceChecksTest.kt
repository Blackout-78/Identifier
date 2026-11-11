package com.blackout.identifier

import com.blackout.identifier.data.CheckId
import com.blackout.identifier.data.DeviceChecks
import com.blackout.identifier.data.DeviceFacts
import com.blackout.identifier.data.Status
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceChecksTest {
    private val today = LocalDate.of(2026, 11, 10).toEpochDay()

    private fun facts(
        lock: Boolean = true, enc: Int? = DeviceChecks.ENC_ACTIVE, patch: String? = "2026-10-05",
        verifier: Int? = 1, dev: Boolean = false, adb: Boolean = false, wifi: Int? = 2,
    ) = DeviceFacts(lock, enc, patch, verifier, dev, adb, wifi)

    private fun status(id: CheckId, f: DeviceFacts) = DeviceChecks.evaluate(f, today).first { it.id == id }.status

    @Test fun healthyPhonePassesAllSix() {
        val list = DeviceChecks.evaluate(facts(), today)
        assertEquals(6, list.size)
        assertEquals(6, DeviceChecks.passed(list))
        assertEquals("6 of 6 checks passed", DeviceChecks.summary(list))
    }

    @Test fun noScreenLockFails() = assertEquals(Status.FAIL, status(CheckId.LOCK, facts(lock = false)))

    @Test fun encryptionStates() {
        assertEquals(Status.PASS, status(CheckId.ENCRYPTION, facts(enc = DeviceChecks.ENC_ACTIVE_PER_USER)))
        assertEquals(Status.PASS, status(CheckId.ENCRYPTION, facts(enc = DeviceChecks.ENC_ACTIVE_DEFAULT_KEY)))
        assertEquals(Status.FAIL, status(CheckId.ENCRYPTION, facts(enc = DeviceChecks.ENC_INACTIVE)))
        assertEquals(Status.WARN, status(CheckId.ENCRYPTION, facts(enc = DeviceChecks.ENC_ACTIVATING)))
        assertEquals(Status.UNKNOWN, status(CheckId.ENCRYPTION, facts(enc = DeviceChecks.ENC_UNSUPPORTED)))
        assertEquals(Status.UNKNOWN, status(CheckId.ENCRYPTION, facts(enc = null)))
    }

    @Test fun patchAgeBands() {
        assertEquals(Status.PASS, status(CheckId.PATCH, facts(patch = "2026-11-10")))
        assertEquals(Status.PASS, status(CheckId.PATCH, facts(patch = "2026-08-12"))) // 90 days
        assertEquals(Status.WARN, status(CheckId.PATCH, facts(patch = "2026-08-11"))) // 91 days
        assertEquals(Status.WARN, status(CheckId.PATCH, facts(patch = "2025-11-10"))) // 365 days
        assertEquals(Status.FAIL, status(CheckId.PATCH, facts(patch = "2025-11-09"))) // 366 days
    }

    @Test fun patchDateInTheFutureIsFine() = assertEquals(Status.PASS, status(CheckId.PATCH, facts(patch = "2026-12-01")))

    @Test fun unreadablePatchIsUnknownNotBad() {
        assertEquals(Status.UNKNOWN, status(CheckId.PATCH, facts(patch = null)))
        assertEquals(Status.UNKNOWN, status(CheckId.PATCH, facts(patch = "garbage")))
        assertNull(DeviceChecks.patchAgeDays("2026-13-45", today))
    }

    @Test fun playProtect() {
        assertEquals(Status.PASS, status(CheckId.PLAY_PROTECT, facts(verifier = 1)))
        assertEquals(Status.FAIL, status(CheckId.PLAY_PROTECT, facts(verifier = 0)))
        assertEquals(Status.UNKNOWN, status(CheckId.PLAY_PROTECT, facts(verifier = null)))
    }

    @Test fun developerOptions() {
        assertEquals(Status.PASS, status(CheckId.DEV_OPTIONS, facts()))
        assertEquals(Status.WARN, status(CheckId.DEV_OPTIONS, facts(dev = true)))
        assertEquals(Status.WARN, status(CheckId.DEV_OPTIONS, facts(dev = true, adb = true)))
    }

    @Test fun wifiStates() {
        assertEquals(Status.FAIL, status(CheckId.WIFI, facts(wifi = DeviceChecks.WIFI_OPEN)))
        assertEquals(Status.FAIL, status(CheckId.WIFI, facts(wifi = DeviceChecks.WIFI_WEP)))
        assertEquals(Status.PASS, status(CheckId.WIFI, facts(wifi = 4))) // WPA3
        assertEquals(Status.PASS, status(CheckId.WIFI, facts(wifi = DeviceChecks.WIFI_NONE)))
        assertEquals(Status.UNKNOWN, status(CheckId.WIFI, facts(wifi = null)))
    }

    @Test fun summaryMatchesTheApprovedDesign() {
        // Design: lock, encryption, patch, Play Protect pass; developer options warns; open Wi-Fi fails.
        val list = DeviceChecks.evaluate(facts(dev = true, wifi = DeviceChecks.WIFI_OPEN), today)
        assertEquals("4 of 6 checks passed", DeviceChecks.summary(list))
    }

    @Test fun summaryCountsUnknowns() {
        val list = DeviceChecks.evaluate(facts(verifier = null, wifi = null), today)
        assertEquals("4 of 6 checks passed, 2 could not be checked", DeviceChecks.summary(list))
    }
}
