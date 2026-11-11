package com.blackout.identifier.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.blackout.identifier.data.Check
import com.blackout.identifier.data.CheckId
import com.blackout.identifier.data.DeviceChecks
import com.blackout.identifier.data.DeviceScanner
import com.blackout.identifier.data.Status

private fun icon(id: CheckId): ImageVector = when (id) {
    CheckId.LOCK -> Icons.Outlined.Lock
    CheckId.ENCRYPTION -> Icons.Outlined.Security
    CheckId.PATCH -> Icons.Outlined.SystemUpdate
    CheckId.PLAY_PROTECT -> Icons.Outlined.VerifiedUser
    CheckId.DEV_OPTIONS -> Icons.Outlined.Code
    CheckId.WIFI -> Icons.Outlined.Wifi
}

/** Where a tap on each row takes you, so you can fix it right there. */
private fun settingsAction(id: CheckId): String = when (id) {
    CheckId.LOCK, CheckId.ENCRYPTION, CheckId.PLAY_PROTECT -> Settings.ACTION_SECURITY_SETTINGS
    CheckId.PATCH -> Settings.ACTION_DEVICE_INFO_SETTINGS
    CheckId.DEV_OPTIONS -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
    CheckId.WIFI -> Settings.ACTION_WIFI_SETTINGS
}

private fun statusColor(s: Status) = when (s) {
    Status.PASS -> C.Good
    Status.WARN -> C.Warn
    Status.FAIL -> C.Accent
    Status.UNKNOWN -> C.Sub
}

private fun statusTile(s: Status) = when (s) {
    Status.PASS -> Color(0xFF1B2B22)
    Status.WARN -> Color(0xFF2B2210)
    Status.FAIL -> Color(0xFF2A1416)
    Status.UNKNOWN -> Color(0xFF1F1F28)
}

@Composable
fun DeviceScreen() {
    val context = LocalContext.current
    var checks by remember { mutableStateOf<List<Check>?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    // Look again every time you come back, for example after fixing something in Settings.
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    LaunchedEffect(refresh) {
        if (refresh == 0) return@LaunchedEffect
        val app = context.applicationContext
        Thread { runCatching { DeviceScanner.check(app) }.onSuccess { checks = it } }.start()
    }

    val list = checks
    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding()) {
        Text("Device", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp))
        Text(
            list?.let { DeviceChecks.summary(it) } ?: "Checking...",
            color = C.Sub, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        )
        if (list != null) {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(list, key = { it.id }) { CheckRow(it) }
            }
        }
    }
}

@Composable
private fun CheckRow(c: Check) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(shape).background(C.Card).border(BorderStroke(1.dp, C.Line), shape)
            .clickable { runCatching { context.startActivity(Intent(settingsAction(c.id))) } }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(statusTile(c.status)), contentAlignment = Alignment.Center) {
            Icon(icon(c.id), contentDescription = null, tint = statusColor(c.status), modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(c.title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(c.detail, color = C.Sub, fontSize = 12.sp)
        }
        Text(c.status.label, color = statusColor(c.status), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
