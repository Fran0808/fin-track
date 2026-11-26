package com.financemanager.listener.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = Muted,
    tertiary = Positive,
    background = Canvas,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    onSurfaceVariant = Muted,
    surfaceVariant = Canvas,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    outline = Muted,
    outlineVariant = Line,
    error = Negative
)

@Composable
fun FinTrackTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColorScheme, typography = Typography, content = content)
}