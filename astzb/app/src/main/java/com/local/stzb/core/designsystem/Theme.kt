package com.local.stzb.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AstzbLightScheme = lightColorScheme(
    primary = AstzbColors.Primary,
    onPrimary = AstzbColors.OnPrimary,
    primaryContainer = AstzbColors.PrimaryContainer,
    onPrimaryContainer = AstzbColors.TextPrimary,
    background = AstzbColors.BackgroundBottom,
    onBackground = AstzbColors.TextPrimary,
    surface = AstzbColors.GlassMedium,
    onSurface = AstzbColors.TextPrimary,
    surfaceVariant = AstzbColors.GlassLow,
    onSurfaceVariant = AstzbColors.TextSecondary,
    outline = AstzbColors.Outline,
    outlineVariant = AstzbColors.OutlineLow,
    error = AstzbColors.Error,
    onError = AstzbColors.OnPrimary,
)

@Composable
fun AstzbTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AstzbLightScheme,
        typography = AstzbTypography,
        content = content,
    )
}
