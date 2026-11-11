package com.blackout.identifier.ui

import android.content.Intent
import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import java.text.DateFormat
import java.util.Date
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.blackout.identifier.data.AppScanner
import com.blackout.identifier.data.CheckId
import com.blackout.identifier.data.DeviceScanner
import com.blackout.identifier.data.Finding
import com.blackout.identifier.data.Level
import com.blackout.identifier.data.Scan
import com.blackout.identifier.data.ScanReport
import com.blackout.identifier.data.EventStore
import com.blackout.identifier.data.Monitor
import com.blackout.identifier.data.Prefs
import com.blackout.identifier.data.SensorEvent
import com.blackout.identifier.MonitorService

private fun levelColor(l: Level) = when (l) {
    Level.HIGH -> C.Accent
    Level.MEDIUM -> C.Warn
    Level.LOW -> C.Info
    Level.CLEAN -> C.Good
}

private fun scoreColor(score: Int) = when {
    score >= 85 -> C.Good
    score >= 65 -> C.Warn
    else -> C.Accent
}

private fun deviceAction(id: CheckId): String = when (id) {
    CheckId.LOCK, CheckId.ENCRYPTION, CheckId.PLAY_PROTECT -> Settings.ACTION_SECURITY_SETTINGS
    CheckId.PATCH -> Settings.ACTION_DEVICE_INFO_SETTINGS
    CheckId.DEV_OPTIONS -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
    CheckId.WIFI -> Settings.ACTION_WIFI_SETTINGS
}

@Composable
fun ScanScreen() {
    val context = LocalContext.current
    var report by remember { mutableStateOf<ScanReport?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var events by remember { mutableStateOf<List<SensorEvent>>(emptyList()) }

    // Scan again every time you come back, for example after fixing something in Settings.
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    LaunchedEffect(refresh) {
        if (refresh == 0) return@LaunchedEffect
        val app = context.applicationContext
        Thread {
            runCatching { Scan.build(DeviceScanner.check(app), AppScanner.scan(app), System.currentTimeMillis()) }
                .onSuccess { report = it }
            events = EventStore.read(app, 5)
        }.start()
    }

    val r = report
    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding()) {
        Text("Scan", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp))
        if (r == null) {
            Text("Scanning...", color = C.Sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp))
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item { ScoreCard(r) }
            item { MonitorCard(events) }
            if (!r.unusedKnown) item { UsageHint() }
            if (r.findings.isEmpty()) {
                item { Text("Nothing to fix.", color = C.Good, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp)) }
            } else {
                item {
                    Text("${r.findings.size} to review, worst first", color = C.Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
                items(r.findings, key = { it.key }) { FindingRow(it) }
            }
        }
    }
}

@Composable
private fun ScoreCard(r: ScanReport) {
    val shape = RoundedCornerShape(16.dp)
    val color = scoreColor(r.score)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(C.Card).border(BorderStroke(1.dp, C.Line), shape).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(96.dp)) {
                val w = 10.dp.toPx()
                val inset = w / 2
                val arcSize = Size(size.width - w, size.height - w)
                drawArc(C.Line, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
                drawArc(color, -90f, 360f * r.score / 100f, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            }
            Text("${r.score}", color = C.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(r.grade, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Security score out of 100", color = C.Sub, fontSize = 12.sp)
            if (r.couldNotCheck > 0) {
                Text("${r.couldNotCheck} device check(s) could not be read on this phone", color = C.Sub, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun UsageHint() {
    val context = LocalContext.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(C.Card).border(BorderStroke(1.dp, C.Line), shape)
            .clickable { runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } }.padding(12.dp),
    ) {
        Text("Unused apps are not checked", color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("Tap, then allow Identifier under usage access. It stays on this phone.", color = C.Sub, fontSize = 12.sp, textAlign = TextAlign.Start)
    }
}

@Composable
private fun FindingRow(f: Finding) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(14.dp)
    val color = levelColor(f.level)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(shape).background(C.Card).border(BorderStroke(1.dp, C.Line), shape)
            .clickable {
                val intent = when {
                    f.fix.checkId != null -> Intent(deviceAction(f.fix.checkId))
                    f.fix.pkg != null -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", f.fix.pkg, null))
                    else -> null
                }
                if (intent != null) runCatching { context.startActivity(intent) }
            }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Column(Modifier.weight(1f)) {
            Text(f.title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(f.detail, color = C.Sub, fontSize = 12.sp)
        }
        Text(f.level.label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MonitorCard(events: List<SensorEvent>) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(Prefs.monitorOn) }
    val shape = RoundedCornerShape(14.dp)

    fun turnOn() { Prefs.monitorOn = true; on = true; MonitorService.start(context) }
    fun turnOff() { Prefs.monitorOn = false; on = false; MonitorService.stop(context) }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { turnOn() }
    // If it was on, make sure it is still running after the phone cleaned things up.
    LaunchedEffect(Unit) { if (Prefs.monitorOn) MonitorService.start(context) }

    Column(
        Modifier.fillMaxWidth().clip(shape).background(C.Card).border(BorderStroke(1.dp, C.Line), shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Live monitoring", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (on) "On. A notification shows camera and mic use." else "Off. Turn on to watch camera and mic.",
                    color = C.Sub, fontSize = 12.sp,
                )
            }
            Switch(
                checked = on,
                onCheckedChange = { want ->
                    if (!want) turnOff()
                    else if (Build.VERSION.SDK_INT >= 33 && !MonitorService.canNotify(context)) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else turnOn()
                },
                colors = SwitchDefaults.colors(checkedTrackColor = C.Accent, checkedThumbColor = Color.White, uncheckedTrackColor = C.Line, uncheckedThumbColor = C.Sub),
            )
        }
        if (events.isEmpty()) {
            Text("No camera or mic events yet.", color = C.Sub, fontSize = 12.sp)
        } else {
            val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
            events.forEach { e ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (e.suspicious) C.Accent else if (e.active) C.Warn else C.Good))
                    Column {
                        Text(Monitor.describe(e), color = if (e.suspicious) C.AccentSoft else C.Text, fontSize = 12.sp)
                        Text(fmt.format(Date(e.atMs)), color = C.Sub, fontSize = 11.sp)
                    }
                }
            }
        }
        Text("Android does not say which app used the camera or mic. The app named is the one on screen then (needs usage access).", color = C.Sub, fontSize = 11.sp)
    }
}
