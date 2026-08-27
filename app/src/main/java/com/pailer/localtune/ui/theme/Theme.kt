package com.pailer.localtune.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LocalTuneColors = darkColorScheme(
    primary = Color(0xFFFF7A00),
    onPrimary = Color.White,
    secondary = Color(0xFFFFB25A),
    tertiary = Color(0xFFFFE0B2),
    background = Color(0xFF080604),
    surface = Color(0xFF15100C),
    surfaceVariant = Color(0xFF2A1B10),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFFFF7ED),
    onSurfaceVariant = Color(0xFFD6C4B0),
)

@Composable
fun LocalTuneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LocalTuneColors,
        content = content,
    )
}
