package com.blackout.identifier

import com.blackout.identifier.data.Monitor
import com.blackout.identifier.data.Sensor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorTest {
    @Test fun foregroundIsLastAppThatCameOnScreen() {
        val moves = listOf("a" to true, "a" to false, "b" to true)
        assertEquals("b", Monitor.foregroundFrom(moves))
    }

    @Test fun foregroundIsNullWhenNothingOnScreen() {
        assertNull(Monitor.foregroundFrom(listOf("a" to true, "a" to false)))
        assertNull(Monitor.foregroundFrom(emptyList()))
    }

    @Test fun oldAppLeavingDoesNotClearNewOne() {
        assertEquals("b", Monitor.foregroundFrom(listOf("a" to true, "b" to true, "a" to false)))
    }

    @Test fun startWithScreenOffIsSuspicious() {
        val e = Monitor.classify(1L, Sensor.MIC, active = true, screenOn = false, onScreenApp = "Maps")
        assertTrue(e.suspicious)
        assertNull(e.app)
        assertEquals("Microphone started with the screen off", Monitor.describe(e))
    }

    @Test fun startWithScreenOnIsNotSuspiciousAndNamesOnScreenApp() {
        val e = Monitor.classify(1L, Sensor.CAMERA, active = true, screenOn = true, onScreenApp = "Camera")
        assertFalse(e.suspicious)
        assertEquals("Camera started while Camera was on screen", Monitor.describe(e))
    }

    @Test fun stopIsNeverSuspicious() {
        val e = Monitor.classify(1L, Sensor.CAMERA, active = false, screenOn = false, onScreenApp = null)
        assertFalse(e.suspicious)
        assertEquals("Camera stopped", Monitor.describe(e))
    }

    @Test fun encodeDecodeRoundTrip() {
        val e = Monitor.classify(1234L, Sensor.MIC, active = true, screenOn = true, onScreenApp = "My\tApp")
        val d = Monitor.decode(Monitor.encode(e))!!
        assertEquals(1234L, d.atMs)
        assertEquals(Sensor.MIC, d.sensor)
        assertTrue(d.active)
        assertEquals("My App", d.app)
    }

    @Test fun badLinesAreSkipped() {
        assertNull(Monitor.decode("garbage"))
        assertNull(Monitor.decode("x\tMIC\t1\t0\t"))
        assertNull(Monitor.decode("1\tNOPE\t1\t0\t"))
    }

    @Test fun logKeepsNewest200() {
        val lines = (1..250).map { "l$it" }
        val t = Monitor.trim(lines)
        assertEquals(200, t.size)
        assertEquals("l51", t.first())
        assertEquals("l250", t.last())
    }

    @Test fun statusText() {
        assertEquals("Camera: off  |  Mic: IN USE", Monitor.statusText(false, true))
    }
}
