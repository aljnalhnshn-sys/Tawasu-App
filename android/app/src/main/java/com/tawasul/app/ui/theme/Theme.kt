package com.tawasul.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val LightColors = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldSoftLight,
    onPrimaryContainer = EmeraldPrimary,
    secondary = EmeraldSecondary,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outlineVariant = LightDivider,
    error = DangerRed,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = EmeraldAccent,
    onPrimary = Color(0xFF042F2E),
    primaryContainer = EmeraldSoftDark,
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = EmeraldAccent,
    onSecondary = Color(0xFF042F2E),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outlineVariant = DarkDivider,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

@Composable
fun TawasulTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
