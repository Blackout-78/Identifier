package com.blackout.identifier.data

import android.content.Context
import java.io.File

/** The event log. One small file on this phone, newest 200 events. Never leaves the phone. */
object EventStore {
    private fun file(context: Context) = File(context.filesDir, "events.log")

    @Synchronized
    fun append(context: Context, e: SensorEvent) {
        runCatching {
            val f = file(context)
            val lines = if (f.exists()) f.readLines() else emptyList()
            f.writeText(Monitor.trim(lines + Monitor.encode(e)).joinToString("\n", postfix = "\n"))
        }
    }

    @Synchronized
    fun clear(context: Context) { runCatching { file(context).delete() } }

    /** Newest first. */
    @Synchronized
    fun read(context: Context, limit: Int = Monitor.LOG_CAP): List<SensorEvent> = runCatching {
        val f = file(context)
        if (!f.exists()) emptyList() else f.readLines().mapNotNull { Monitor.decode(it) }.asReversed().take(limit)
    }.getOrDefault(emptyList())
}
