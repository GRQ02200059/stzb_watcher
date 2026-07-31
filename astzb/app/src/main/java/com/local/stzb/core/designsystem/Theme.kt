package com.local.stzb.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AstzbDarkScheme = darkColorScheme(
    primary = AstzbColors.Primary,
    onPrimary = AstzbColors.Background,
    secondary = AstzbColors.Secondary,
    background = AstzbColors.Background,
    onBackground = AstzbColors.TextPrimary,
    surface = AstzbColors.Surface,
    onSurface = AstzbColors.TextPrimary,
    surfaceContainerHigh = AstzbColors.SurfaceHigh,
    outline = AstzbColors.Outline,
    error = AstzbColors.Error,
)

@Composable
fun AstzbTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AstzbDarkScheme,
        typography = AstzbTypography,
        content = content,
    )
}
