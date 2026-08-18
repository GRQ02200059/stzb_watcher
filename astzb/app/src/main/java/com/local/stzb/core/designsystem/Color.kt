package com.local.stzb.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * D 冰川银蓝 设计色板
 * 参考设计文档：astzb/docs/superpowers/specs/2026-08-17-ice-glass-android-ui-design.md
 */
object AstzbColors {
    // 页面背景：雾白 → 冰川蓝渐变
    val BackgroundTop = Color(0xFFF7FAFF)
    val BackgroundBottom = Color(0xFFE5EDF8)

    // 玻璃表面：半透明白（层级）
    val GlassLow = Color(0x8FFFFFFFF)       // 58% 透明白
    val GlassMedium = Color(0xCCFFFFFF)     // 80% 透明白
    val GlassHigh = Color(0xD1FFFFFF)        // 82% 透明白

    // 主色
    val Primary = Color(0xFF5275CF)
    val PrimaryContainer = Color(0xFFDCE6F6)
    val OnPrimary = Color(0xFFFFFFFF)
    val Secondary = Color(0xFF64738C)

    // 深蓝文字
    val TextPrimary = Color(0xFF16243D)
    val TextSecondary = Color(0xFF64738C)

    // 线框：蓝灰 10–16%
    val Outline = Color(0x295275CF)          // ~16% primary
    val OutlineLow = Color(0x1A5275CF)       // ~10% primary

    // 语义色
    val Success = Color(0xFF1E9B83)
    val Warning = Color(0xFFBE7A18)
    val Error = Color(0xFFC75862)
    val Info = Color(0xFF4C78C8)

    // 状态胶囊
    val StatusSuccessBg = Color(0xFFE0F4EE)
    val StatusWarningBg = Color(0xFFFDECD4)
    val StatusErrorBg = Color(0xFFF9E2E5)
    val StatusInfoBg = Color(0xFFE3ECF9)
}
