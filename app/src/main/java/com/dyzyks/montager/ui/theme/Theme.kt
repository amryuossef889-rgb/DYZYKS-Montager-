package com.dyzyks.montager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DaVinciDarkColorScheme = darkColorScheme(
    primary = ResolveAccent,
    onPrimary = ResolveBackground,
    primaryContainer = ResolveAccentPressed,
    onPrimaryContainer = ResolveTextPrimary,
    secondary = ResolveAccent,
    onSecondary = ResolveBackground,
    background = ResolveBackground,
    onBackground = ResolveTextPrimary,
    surface = ResolveSurface,
    onSurface = ResolveTextPrimary,
    surfaceVariant = ResolveSurfaceVariant,
    onSurfaceVariant = ResolveTextSecondary,
    outline = ResolveBorder,
    error = ResolveError,
    onError = ResolveTextPrimary
)

@Composable
fun DyzyksMontagerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DaVinciDarkColorScheme,
        typography = Typography,
        content = content
    )
}
