package com.blackout.identifier.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Placeholder pages. Each one is filled in by a later phase. */
@Composable
private fun Placeholder(title: String, note: String) {
    Column(
        Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(note, color = C.Sub, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable fun ScanScreen() = Placeholder("Scan", "Security score and findings arrive in the next phases.")
@Composable fun DeviceScreen() = Placeholder("Device", "Screen lock, encryption and patch checks will appear here.")
