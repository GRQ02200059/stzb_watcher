package com.local.stzb.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * macOS Liquid Glass 方向色板。
 *
 * 重点不是“蓝白卡片”，而是浅雾背景 + 彩色环境光 + 半透明玻璃厚度。
 */
object AstzbColors {
    // 页面背景：接近 macOS 桌面雾白，给玻璃留下可透出的底色。
    val BackgroundTop = Color(0xFFFBFCFF)
    val BackgroundMiddle = Color(0xFFF1F6FF)
    val BackgroundBottom = Color(0xFFE9F1FC)

    // 环境光斑：模拟 macOS 壁纸穿过玻璃后的蓝 / 紫 / 青色泛光。
    val AuroraBlue = Color(0x668CC8FF)
    val AuroraViolet = Color(0x66C99BFF)
    val AuroraMint = Color(0x5272E4D6)
    val AuroraPeach = Color(0x3DFFD2B8)

    // 玻璃表面：半透明白（层级）。保留旧名兼容现有页面。
    val GlassFrost = Color(0xB8FFFFFF)
    val GlassFloating = Color(0xD9FFFFFF)
    val GlassVeil = Color(0x73FFFFFF)
    val GlassLow = GlassVeil
    val GlassMedium = GlassFrost
    val GlassHigh = GlassFloating
    val GlassHighlight = Color(0xD9FFFFFF)

    // 主色
    val Primary = Color(0xFF426AD2)
    val PrimaryContainer = Color(0xFFEAF1FF)
    val OnPrimary = Color(0xFFFFFFFF)
    val Secondary = Color(0xFF65748D)
    val Tertiary = Color(0xFF25A88E)

    // 深蓝文字
    val TextPrimary = Color(0xFF14223A)
    val TextSecondary = Color(0xFF60708B)

    // 线框：外层白色高光 + 低透明蓝灰线。
    val Outline = Color(0xBFFFFFFF)
    val OutlineLow = Color(0x8CFFFFFF)
    val OutlineBlue = Color(0x24426AD2)

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
