package com.hometalk.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = OnTalk,
    primaryContainer = GreenContainer,
    onPrimaryContainer = GreenDark,
    secondary = GreenDark,
    onSecondary = OnTalk,
    tertiary = BusyOrange,
    onTertiary = OnTalk,
    tertiaryContainer = BusyContainer,
    onTertiaryContainer = Color(0xFF4A2500),
    background = SurfaceTintLight,
    onBackground = Color(0xFF1A1C1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1A),
    surfaceVariant = Color(0xFFE2EDE6),
    onSurfaceVariant = Color(0xFF404944),
    outline = Color(0xFF707974),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = GreenLight,
    onPrimary = GreenDark,
    primaryContainer = GreenDark,
    onPrimaryContainer = GreenContainer,
    secondary = GreenLight,
    onSecondary = GreenDark,
    tertiary = Color(0xFFFFB77A),
    onTertiary = Color(0xFF4A2500),
    background = SurfaceTintDark,
    onBackground = Color(0xFFE2E3DE),
    surface = Color(0xFF1A211D),
    onSurface = Color(0xFFE2E3DE),
    surfaceVariant = Color(0xFF404944),
    onSurfaceVariant = Color(0xFFC0C9C3),
    outline = Color(0xFF8A938D),
    error = Color(0xFFFFB4AB),
)

@Composable
fun HomeTalkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = HomeTalkTypography,
        content = content,
    )
}
