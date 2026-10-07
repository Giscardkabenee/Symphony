package com.symphony.music.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF0A0A0C)
private val Card = Color(0xFF1C1C1F)
private val Line = Color(0xFF2A2A2E)
private val Muted = Color(0xFFA0A0A8)

private val DarkColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Ink,
    primaryContainer = Color(0xFF2E2E33),
    onPrimaryContainer = Color.White,
    secondary = Color.White,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF2E2E33),
    onSecondaryContainer = Color.White,
    background = Ink,
    onBackground = Color.White,
    surface = Ink,
    onSurface = Color.White,
    surfaceVariant = Card,
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Card,
    surfaceContainer = Card,
    surfaceContainerHigh = Color(0xFF242428),
    surfaceContainerHighest = Color(0xFF2E2E33),
    outline = Color(0xFF5A5A62),
    outlineVariant = Line,
)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E4E9),
    onPrimaryContainer = Ink,
    secondary = Ink,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E4E9),
    onSecondaryContainer = Ink,
    background = Color(0xFFF5F5F7),
    onBackground = Ink,
    surface = Color(0xFFF5F5F7),
    onSurface = Ink,
    surfaceVariant = Color.White,
    onSurfaceVariant = Color(0xFF5A5A62),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEDEDF1),
    surfaceContainerHighest = Color(0xFFE4E4E9),
    outline = Color(0xFF8A8A92),
    outlineVariant = Color(0xFFD8D8DE),
)

/** themeMode: 0 = follow the system, 1 = light, 2 = dark. */
@Composable
fun isDark(themeMode: Int): Boolean = when (themeMode) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun SymphonyTheme(themeMode: Int, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isDark(themeMode)) DarkColors else LightColors,
        content = content,
    )
}
