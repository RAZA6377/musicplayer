package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color.Black,
    secondary = CosmicPurple,
    onSecondary = Color.White,
    tertiary = EmeraldGlow,
    background = DeepObsidian,
    onBackground = Color.White,
    surface = MatteSlate,
    onSurface = Color.White,
    surfaceVariant = GlassGrey,
    onSurfaceVariant = Color.White,
    error = NeonPink
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    // Force Obsidian Dark Theme specifically for realistic luxury audio styling
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
