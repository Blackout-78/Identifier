package com.blackout.identifier.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.blackout.identifier.data.AppFilter
import com.blackout.identifier.data.AppInfo
import com.blackout.identifier.data.AppScanner
import com.blackout.identifier.data.Level
import com.blackout.identifier.data.Risk
import com.blackout.identifier.data.Tag

private fun levelColor(l: Level) = when (l) {
    Level.HIGH -> C.Accent
    Level.MEDIUM -> C.Warn
    Level.LOW -> C.Info
    Level.CLEAN -> C.Good
}

private fun levelText(l: Level) = when (l) {
    Level.HIGH -> C.AccentSoft
    Level.MEDIUM -> C.Warn
    Level.LOW -> Color(0xFF8FB2FF)
    Level.CLEAN -> C.Good
}

@Composable
fun AppsScreen() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var usageAccess by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    val filter = AppFilter.values()[filterIndex.coerceIn(0, AppFilter.values().size - 1)]

    // Look again every time you come back to the app (for example after changing a permission).
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    androidx.compose.runtime.LaunchedEffect(refresh) {
        if (refresh == 0) return@LaunchedEffect
        val app = context.applicationContext
        Thread {
            runCatching { AppScanner.scan(app) to AppScanner.hasUsageAccess(app) }
                .onSuccess { (list, access) -> apps = list; usageAccess = access }
                .onFailure { if (apps == null) apps = emptyList() }
        }.start()
    }

    val now = remember(apps) { System.currentTimeMillis() }
    val all = apps
    val counts = AppFilter.values().associateWith { f -> all?.count { Risk.matches(f, it, now) } ?: 0 }
    val shown = all?.filter { Risk.matches(filter, it, now) } ?: emptyList()

    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding()) {
        Text("Apps", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppFilter.values().forEachIndexed { i, f ->
                val name = when (f) { AppFilter.ALL -> "All"; AppFilter.RISKY -> "Risky"; AppFilter.BACKGROUND -> "Background"; AppFilter.UNUSED -> "Unused" }
                Chip("$name ${counts[f]}", selected = f == filter) { filterIndex = i }
            }
        }
        if (!usageAccess && all != null) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card)
                    .border(BorderStroke(1.dp, C.Line), RoundedCornerShape(14.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Last-used dates are off", color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Allow usage access to see when each app was last used. Stays on this phone.", color = C.Sub, fontSize = 12.sp)
                }
                Box(
                    Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(C.Accent)
                        .clickable { runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Allow", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }
        when {
            all == null -> Message("Scanning your apps...")
            filter == AppFilter.UNUSED && !usageAccess -> Message("Allow usage access to find apps you have not used in ${Risk.UNUSED_DAYS} days.")
            shown.isEmpty() -> Message("Nothing here.")
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 16.dp),
            ) {
                items(shown, key = { it.pkg }) { a -> AppCard(a, now) }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = C.Sub, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.heightIn(min = 44.dp).clip(shape).background(if (selected) Color(0xFF2A1416) else C.Card)
            .border(BorderStroke(1.dp, if (selected) C.Accent else C.Line), shape).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Color(0xFFFFB3B5) else Color(0xFFD6D6DF), fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
private fun AppCard(a: AppInfo, now: Long) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(14.dp)
    var menu by remember { mutableStateOf(false) }
    if (menu) AppMenu(a) { menu = false }
    Column(
        Modifier.fillMaxWidth().clip(shape).background(C.Card).border(BorderStroke(1.dp, C.Line), shape)
            .clickable { menu = true }.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(levelColor(a.level)), contentAlignment = Alignment.Center) {
                Text(a.label.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?", color = C.Bg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(a.label, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Risk.agoText(now, a.lastUsedMs), color = C.Sub, fontSize = 12.sp)
            }
            Text(a.level.label, color = levelText(a.level), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        if (a.tags.isEmpty()) {
            Text("No risky access", color = C.Sub, fontSize = 12.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                a.tags.take(3).forEach { TagPill(it) }
                if (a.tags.size > 3) Text("+${a.tags.size - 3}", color = C.Sub, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun TagPill(t: Tag) {
    val (bg, fg) = when (t.level) {
        Level.HIGH -> Color(0xFF2A1416) to Color(0xFFFFB3B5)
        Level.MEDIUM -> Color(0xFF2B2210) to Color(0xFFF5C65A)
        else -> Color(0xFF1F1F28) to Color(0xFFD6D6DF)
    }
    Text(t.label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 4.dp))
}

/** One tap to the screen where you take permissions away, turn on auto-remove, or uninstall. */
@Composable
private fun AppMenu(a: AppInfo, onClose: () -> Unit) {
    val context = LocalContext.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        containerColor = C.Card,
        title = { Text(a.label, color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MenuButton("Remove permissions", "Opens this app's permission list") { AppActions.openPermissions(context, a.pkg); onClose() }
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    MenuButton(
                        "Auto-remove when unused",
                        if (a.autoRevokeOff) "Currently off. Turn it on" else "Android removes its permissions if you stop using it",
                    ) { AppActions.openAutoRevoke(context, a.pkg); onClose() }
                }
                MenuButton("Uninstall", "Removes the app") { AppActions.uninstall(context, a.pkg); onClose() }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onClose) { Text("Cancel", color = C.Sub) } },
    )
}

@Composable
private fun MenuButton(title: String, sub: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp)) {
        Text(title, color = C.AccentSoft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(sub, color = C.Sub, fontSize = 12.sp)
    }
}
