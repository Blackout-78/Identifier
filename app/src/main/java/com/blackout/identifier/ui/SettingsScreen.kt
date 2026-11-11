package com.blackout.identifier.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackout.identifier.MonitorService
import com.blackout.identifier.data.EventStore
import com.blackout.identifier.data.Prefs
import com.blackout.identifier.settings.AppIcon
import com.blackout.identifier.settings.IconShapes

@Composable
fun SettingsScreen() {
    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding()) {
        Text("Settings", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 12.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            Section("MONITORING") { MonitorSection() }
            Section("APP ICON") { IconSection() }
            Text("Identifier works fully offline. It has no internet permission and keeps everything on this phone.",
                color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(6.dp))
        }
    }
}

@Composable
private fun MonitorSection() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(Prefs.monitorOn) }
    var alert by remember { mutableStateOf(Prefs.alertScreenOff) }
    fun turnOn() { Prefs.monitorOn = true; on = true; MonitorService.start(context) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { turnOn() }

    Row2("Live monitoring", "Watches the camera and mic. Shows a notification while on.") {
        Toggle(on) { want ->
            if (!want) { Prefs.monitorOn = false; on = false; MonitorService.stop(context) }
            else if (android.os.Build.VERSION.SDK_INT >= 33 && !MonitorService.canNotify(context)) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
            else turnOn()
        }
    }
    Row2("Alert when screen is off", "Loud alert if the camera or mic starts while the screen is off.") {
        Toggle(alert) { alert = it; Prefs.alertScreenOff = it }
    }
    Row2("Clear event log", "Deletes the saved camera and mic events from this phone.", divider = false, onClick = {
        EventStore.clear(context)
        Toast.makeText(context, "Event log cleared", Toast.LENGTH_SHORT).show()
    }) { Text("›", color = C.Sub, fontSize = 20.sp) }
}

@Composable
private fun IconSection() {
    val context = LocalContext.current
    var picker by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        IconShapes.STYLES.keys.forEach { s ->
            val on = Prefs.iconStyle == s
            val bmp = remember(s, Prefs.iconMain, Prefs.iconSecond) { AppIcon.render(s, Prefs.iconMain, Prefs.iconSecond, 192).asImageBitmap() }
            Column(
                Modifier.clip(RoundedCornerShape(18.dp)).border(2.dp, if (on) C.Accent else Color.Transparent, RoundedCornerShape(18.dp))
                    .clickable { Prefs.iconStyle = s; AppIcon.sync(context, s, Prefs.iconMain, Prefs.iconSecond) }.padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(bmp, "Style $s", Modifier.size(56.dp).clip(CircleShape))
                Text("$s · ${IconShapes.NAMES[s]}", color = if (on) C.Accent else C.Sub, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    HorizontalDivider(color = C.Line)
    Row2("Main color", "Color wheel", onClick = { picker = "main" }) { Swatch(Color(Prefs.iconMain)) }
    Row2("Second color", "Color wheel", onClick = { picker = "second" }) { Swatch(Color(Prefs.iconSecond)) }
    Row2("App drawer icon", "Changes by itself. Tap to refresh if it looks old.", onClick = { AppIcon.sync(context, Prefs.iconStyle, Prefs.iconMain, Prefs.iconSecond) }) {
        Text("›", color = C.Sub, fontSize = 20.sp)
    }
    Row2("Add to home screen", "Exact colors. Updates by itself when you change them.", divider = false, onClick = {
        if (!AppIcon.pinOrUpdate(context, Prefs.iconStyle, Prefs.iconMain, Prefs.iconSecond, ask = true))
            Toast.makeText(context, "Your home screen doesn't allow adding icons this way", Toast.LENGTH_SHORT).show()
    }) { Text("›", color = C.Sub, fontSize = 20.sp) }
    picker?.let { which ->
        ColorPickerDialog(
            title = if (which == "main") "Main color" else "Second color",
            initial = if (which == "main") Prefs.iconMain else Prefs.iconSecond,
            onPick = {
                if (which == "main") Prefs.iconMain = it else Prefs.iconSecond = it
                picker = null
                AppIcon.sync(context, Prefs.iconStyle, Prefs.iconMain, Prefs.iconSecond)
            },
            onDismiss = { picker = null },
            resetLabel = "Default",
            onReset = {
                if (which == "main") Prefs.iconMain = AppIcon.DEFAULT_MAIN else Prefs.iconSecond = AppIcon.DEFAULT_SECOND
                picker = null
                AppIcon.sync(context, Prefs.iconStyle, Prefs.iconMain, Prefs.iconSecond)
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card).padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(title, color = C.Sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
        content()
    }
}

@Composable
private fun Row2(title: String, sub: String, divider: Boolean = true, onClick: (() -> Unit)? = null, end: @Composable () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(sub, color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            end()
        }
        if (divider) HorizontalDivider(color = C.Line)
    }
}

@Composable
private fun Toggle(on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Switch(
        checked = on, onCheckedChange = if (enabled) onChange else null, enabled = enabled,
        colors = SwitchDefaults.colors(checkedTrackColor = C.Accent, checkedThumbColor = Color.White,
            disabledCheckedTrackColor = C.Accent.copy(alpha = 0.6f), disabledCheckedThumbColor = Color.White),
    )
}
