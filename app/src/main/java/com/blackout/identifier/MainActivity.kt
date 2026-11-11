package com.blackout.identifier

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.blackout.identifier.data.Prefs
import com.blackout.identifier.settings.AppIcon
import com.blackout.identifier.ui.AppsScreen
import com.blackout.identifier.ui.C
import com.blackout.identifier.ui.DeviceScreen
import com.blackout.identifier.ui.IdentifierTheme
import com.blackout.identifier.ui.ScanScreen
import com.blackout.identifier.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // After an update, put back the icon you chose. Done in the background so opening stays fast.
        val app = applicationContext
        Thread { runCatching { AppIcon.syncAfterUpdate(app, Prefs.iconStyle, Prefs.iconMain, Prefs.iconSecond) } }.start()
        setContent { IdentifierTheme { Root() } }
    }
}

private class Tab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Scan", Icons.Outlined.Shield),
    Tab("Apps", Icons.Outlined.Apps),
    Tab("Device", Icons.Outlined.PhoneAndroid),
    Tab("Settings", Icons.Outlined.Tune),
)

@Composable
private fun Root() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(C.Bg)) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> ScanScreen()
                1 -> AppsScreen()
                2 -> DeviceScreen()
                else -> SettingsScreen()
            }
        }
        NavigationBar(containerColor = C.Bar, contentColor = C.Text) {
            TABS.forEachIndexed { i, t ->
                NavigationBarItem(
                    selected = tab == i,
                    onClick = { tab = i },
                    icon = { Icon(t.icon, contentDescription = t.label) },
                    label = { Text(t.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = C.AccentSoft, selectedTextColor = C.AccentSoft, indicatorColor = Color.Transparent,
                        unselectedIconColor = C.Sub, unselectedTextColor = C.Sub,
                    ),
                )
            }
        }
    }
}
