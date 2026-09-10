package com.pailer.localtune.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta Pailer FM: cinza escuro, prateado, vermelho e bege claro.
// Cinzas escurecidos um pouco (pedido do usuario 09/09/2026) - mesma escala relativa entre os
// 5 tons, so ~7-9 pontos mais escuro por canal.
val PailerCharcoal = Color(0xFF15161A)
val PailerSurface = Color(0xFF1C1E23)
val PailerSurfaceHigh = Color(0xFF23252B)
val PailerSurfaceHighest = Color(0xFF2A2C33)
val PailerGunmetal = Color(0xFF3A3D43)
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
