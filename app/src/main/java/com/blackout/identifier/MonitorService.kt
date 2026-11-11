package com.blackout.identifier

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.blackout.identifier.data.AppScanner
import com.blackout.identifier.data.EventStore
import com.blackout.identifier.data.Monitor
import com.blackout.identifier.data.Prefs
import com.blackout.identifier.data.Sensor

/** Watches the camera and microphone while it runs, and keeps one quiet notification up so you always know. */
class MonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val state = HashMap<Sensor, Boolean>()
    private val unavailable = HashSet<String>()
    private lateinit var nm: NotificationManager

    private val cameraCb = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) { unavailable.remove(cameraId); update(Sensor.CAMERA, unavailable.isNotEmpty()) }
        override fun onCameraUnavailable(cameraId: String) { unavailable.add(cameraId); update(Sensor.CAMERA, true) }
    }

    private val micCb = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
            update(Sensor.MIC, !configs.isNullOrEmpty())
        }
    }

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH_WATCH, "Monitoring", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Alerts", NotificationManager.IMPORTANCE_HIGH))
        val n = watchNotification()
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID_WATCH, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(ID_WATCH, n)
        runCatching { (getSystemService(CAMERA_SERVICE) as CameraManager).registerAvailabilityCallback(cameraCb, handler) }
        runCatching { (getSystemService(AUDIO_SERVICE) as AudioManager).registerAudioRecordingCallback(micCb, handler) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { (getSystemService(CAMERA_SERVICE) as CameraManager).unregisterAvailabilityCallback(cameraCb) }
        runCatching { (getSystemService(AUDIO_SERVICE) as AudioManager).unregisterAudioRecordingCallback(micCb) }
        super.onDestroy()
    }

    private fun update(sensor: Sensor, active: Boolean) {
        if ((state[sensor] ?: false) == active) return
        state[sensor] = active
        val screenOn = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive
        val e = Monitor.classify(System.currentTimeMillis(), sensor, active, screenOn, if (active && screenOn) onScreenApp() else null)
        EventStore.append(this, e)
        nm.notify(ID_WATCH, watchNotification())
        if (e.suspicious) {
            val n = Notification.Builder(this, CH_ALERT)
                .setSmallIcon(R.drawable.ic_stat_shield)
                .setContentTitle("${sensor.label} turned on")
                .setContentText(Monitor.describe(e) + ". Tap to review your apps.")
                .setColor(Color.parseColor("#E5484D"))
                .setContentIntent(open())
                .setAutoCancel(true)
                .build()
            nm.notify(if (sensor == Sensor.CAMERA) ID_ALERT_CAM else ID_ALERT_MIC, n)
        }
    }

    private fun watchNotification(): Notification {
        val cam = state[Sensor.CAMERA] ?: false
        val mic = state[Sensor.MIC] ?: false
        return Notification.Builder(this, CH_WATCH)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle("Identifier is watching")
            .setContentText(Monitor.statusText(cam, mic))
            .setColor(Color.parseColor(if (cam || mic) "#E5484D" else "#3DD68C"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open())
            .build()
    }

    private fun open() = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    /** The app on screen right now. Needs usage access; returns null without it. */
    private fun onScreenApp(): String? {
        if (!AppScanner.hasUsageAccess(this)) return null
        return runCatching {
            val now = System.currentTimeMillis()
            val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usm.queryEvents(now - 6 * 3_600_000L, now)
            val moves = ArrayList<Pair<String, Boolean>>()
            val ev = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(ev)
                when (ev.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> moves.add(ev.packageName to true)
                    UsageEvents.Event.ACTIVITY_PAUSED -> moves.add(ev.packageName to false)
                }
            }
            val pkg = Monitor.foregroundFrom(moves) ?: return null
            runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        }.getOrNull()
    }

    companion object {
        private const val CH_WATCH = "monitor"
        private const val CH_ALERT = "alerts"
        private const val ID_WATCH = 1
        private const val ID_ALERT_CAM = 2
        private const val ID_ALERT_MIC = 3

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }

        fun canNotify(context: Context): Boolean =
            Build.VERSION.SDK_INT < 33 || context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED
    }
}

/** Puts monitoring back after the phone restarts, if you had it on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.monitorOn) MonitorService.start(context)
    }
}
