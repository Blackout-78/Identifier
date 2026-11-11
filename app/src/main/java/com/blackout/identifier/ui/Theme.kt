package com.blackout.identifier.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Identifier look: near-black, red accent. Same layout style as the other apps. */
object C {
    val Bg = Color(0xFF0B0B0F)
    val Bar = Color(0xFF101016)
    val Card = Color(0xFF15151C)
    val Line = Color(0xFF26262F)
    val Text = Color(0xFFF2F2F5)
    val Sub = Color(0xFFA8A8B6)
    val Accent = Color(0xFFE5484D)
    val AccentSoft = Color(0xFFFF8A8E)
    val Warn = Color(0xFFF5A524)
    val Good = Color(0xFF3DD68C)
    val Info = Color(0xFF5B8DEF)
}

@Composable
fun IdentifierTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Accent, onPrimary = Color.White, background = C.Bg, onBackground = C.Text,
            surface = C.Card, onSurface = C.Text, surfaceVariant = C.Card, onSurfaceVariant = C.Sub,
            surfaceContainer = C.Card, surfaceContainerHigh = C.Card, surfaceContainerHighest = C.Card,
            outline = C.Line, outlineVariant = C.Line,
        ),
        content = content,
    )
}
