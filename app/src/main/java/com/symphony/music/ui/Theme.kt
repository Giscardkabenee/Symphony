package com.symphony.music.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.symphony.music.R

/** Google Sans Flex, the Pixel system typeface, released under the SIL Open Font License (latin set; other letters fall back to the system font). */
val AppFont = FontFamily(
    Font(R.font.google_sans_flex_regular, FontWeight.Normal),
    Font(R.font.google_sans_flex_medium, FontWeight.Medium),
    Font(R.font.google_sans_flex_semibold, FontWeight.SemiBold),
    Font(R.font.google_sans_flex_bold, FontWeight.Bold),
    Font(R.font.google_sans_flex_extrabold, FontWeight.ExtraBold),
)

private val base = Typography()

private val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = AppFont),
    displayMedium = base.displayMedium.copy(fontFamily = AppFont),
    displaySmall = base.displaySmall.copy(fontFamily = AppFont),
    headlineLarge = base.headlineLarge.copy(fontFamily = AppFont, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = AppFont, letterSpacing = (-0.3).sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = AppFont, letterSpacing = (-0.2).sp),
    titleLarge = base.titleLarge.copy(fontFamily = AppFont, letterSpacing = (-0.2).sp),
    titleMedium = base.titleMedium.copy(fontFamily = AppFont),
    titleSmall = base.titleSmall.copy(fontFamily = AppFont),
    bodyLarge = base.bodyLarge.copy(fontFamily = AppFont, letterSpacing = 0.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = AppFont, letterSpacing = 0.sp),
    bodySmall = base.bodySmall.copy(fontFamily = AppFont, letterSpacing = 0.sp),
    labelLarge = base.labelLarge.copy(fontFamily = AppFont),
    labelMedium = base.labelMedium.copy(fontFamily = AppFont),
    labelSmall = base.labelSmall.copy(fontFamily = AppFont),
)

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
        typography = AppTypography,
        content = content,
    )
}
