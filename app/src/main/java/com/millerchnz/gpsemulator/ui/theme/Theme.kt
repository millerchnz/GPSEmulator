package com.millerchnz.gpsemulator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = TealLight,
    onPrimary = Navy,
    primaryContainer = NavySurface,
    onPrimaryContainer = TealLight,
    secondary = PinGold,
    onSecondary = Navy,
    tertiary = PinRed,
    onTertiary = Color.White,
    background = Navy,
    onBackground = Color(0xFFF0EBE3),
    surface = NavySurface,
    onSurface = Color(0xFFF0EBE3),
    surfaceVariant = Color(0xFF1A3A48),
    onSurfaceVariant = Color(0xFFA89F92),
    outline = Color(0xFF2A4A58),
)

private val LightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F3F0),
    onPrimaryContainer = Navy,
    secondary = Color(0xFFB8922A),
    onSecondary = Color.White,
    tertiary = PinRed,
    onTertiary = Color.White,
    background = Linen,
    onBackground = Navy,
    surface = Color.White,
    onSurface = Navy,
    surfaceVariant = Color(0xFFE8EEED),
    onSurfaceVariant = WarmSlate,
    outline = Color(0xFFD0D8D6),
)

@Composable
fun GpsDebugToolTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
