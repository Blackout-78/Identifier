package com.blackout.identifier.data

/** Plain logic with no Android code, so it can be tested on its own. */

enum class Sensor(val label: String) { CAMERA("Camera"), MIC("Microphone") }

class SensorEvent(val atMs: Long, val sensor: Sensor, val active: Boolean, val app: String?, val suspicious: Boolean)

object Monitor {
    const val LOG_CAP = 200

    /**
     * `moves` is oldest first: (package, true) = came on screen, (package, false) = left the screen.
     * Returns the app on screen now, or null if none is known.
     */
    fun foregroundFrom(moves: List<Pair<String, Boolean>>): String? {
        var current: String? = null
        for ((pkg, onScreen) in moves) {
            if (onScreen) current = pkg else if (current == pkg) current = null
        }
        return current
    }

    /**
     * Android does not say which app opened the camera or mic, so the app shown is the one on screen at that moment.
     * Only a start with the screen off is flagged, because then nothing on screen can explain it.
     */
    fun classify(atMs: Long, sensor: Sensor, active: Boolean, screenOn: Boolean, onScreenApp: String?): SensorEvent =
        SensorEvent(atMs, sensor, active, if (screenOn) onScreenApp else null, suspicious = active && !screenOn)

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    fun encode(e: SensorEvent): String =
        "${e.atMs}\t${e.sensor.name}\t${if (e.active) 1 else 0}\t${if (e.suspicious) 1 else 0}\t${clean(e.app ?: "")}"

    fun decode(line: String): SensorEvent? {
        val p = line.split('\t')
        if (p.size < 5) return null
        val at = p[0].toLongOrNull() ?: return null
        val sensor = Sensor.values().firstOrNull { it.name == p[1] } ?: return null
        return SensorEvent(at, sensor, p[2] == "1", p[4].ifEmpty { null }, p[3] == "1")
    }

    fun trim(lines: List<String>): List<String> = lines.takeLast(LOG_CAP)

    fun statusText(camera: Boolean, mic: Boolean): String =
        "Camera: ${if (camera) "IN USE" else "off"}  |  Mic: ${if (mic) "IN USE" else "off"}"

    fun describe(e: SensorEvent): String {
        val what = if (e.active) "started" else "stopped"
        val who = when {
            !e.active -> ""
            e.suspicious -> " with the screen off"
            e.app != null -> " while ${e.app} was on screen"
            else -> ""
        }
        return "${e.sensor.label} $what$who"
    }
}
