package com.pailer.localtune.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta Pailer FM: cinza escuro, prateado, vermelho e bege claro.
val PailerCharcoal = Color(0xFF1C1E23)
val PailerSurface = Color(0xFF24262C)
val PailerSurfaceHigh = Color(0xFF2C2F36)
val PailerSurfaceHighest = Color(0xFF343740)
val PailerGunmetal = Color(0xFF474A50)
val PailerSilver = Color(0xFFBFC0C2)
val PailerIvory = Color(0xFFF0E1CF)
val PailerRed = Color(0xFFB53A2E)

private val LocalTuneColors = darkColorScheme(
    primary = PailerRed,
    onPrimary = Color.White,
    secondary = PailerSilver,
    onSecondary = PailerCharcoal,
    tertiary = PailerIvory,
    onTertiary = PailerCharcoal,
    background = PailerCharcoal,
    surface = PailerSurface,
    surfaceVariant = PailerGunmetal,
    onBackground = PailerIvory,
    onSurface = PailerIvory,
    onSurfaceVariant = PailerSilver,
)

@Composable
fun LocalTuneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LocalTuneColors,
        content = content,
    )
}
