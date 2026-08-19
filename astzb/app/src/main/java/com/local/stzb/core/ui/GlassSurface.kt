package com.local.stzb.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.local.stzb.core.designsystem.AstzbColors

/**
 * App 全局 macOS 毛玻璃背景。
 *
 * Jetpack Compose 在普通 Android View 层没有稳定的全屏实时 backdrop blur，
 * 所以这里用“浅雾渐变 + 彩色环境光斑”提供玻璃可透出的底色。
 */
@Composable
fun MacGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        AstzbColors.BackgroundTop,
                        AstzbColors.BackgroundMiddle,
                        AstzbColors.BackgroundBottom,
                    ),
                ),
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(AstzbColors.AuroraBlue, Color.Transparent),
                        radius = 620f,
                        center = androidx.compose.ui.geometry.Offset(120f, 150f),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(AstzbColors.AuroraViolet, Color.Transparent),
                        radius = 690f,
                        center = androidx.compose.ui.geometry.Offset(900f, 220f),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(AstzbColors.AuroraMint, Color.Transparent),
                        radius = 660f,
                        center = androidx.compose.ui.geometry.Offset(760f, 1850f),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(AstzbColors.AuroraPeach, Color.Transparent),
                        radius = 560f,
                        center = androidx.compose.ui.geometry.Offset(80f, 1850f),
                    ),
                ),
        )
        content()
    }
}

/**
 * 玻璃表面基础容器（一级）。
 * 半透明雾白 + 白色高光边 + 蓝灰阴影，模拟 macOS 浮层玻璃厚度。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(28.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .shadow(
                elevation = 18.dp,
                shape = shape,
                ambientColor = Color(0x24325A8A),
                spotColor = Color(0x24325A8A),
            )
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        AstzbColors.GlassFloating,
                        AstzbColors.GlassFrost,
                        AstzbColors.GlassVeil,
                    ),
                ),
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = AstzbColors.Outline,
                shape = shape,
            ),
    ) {
        GlassInnerHighlight(shape)
        content()
    }
}

/**
 * 玻璃卡片（二级）。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = Color(0x17325A8A),
                spotColor = Color(0x17325A8A),
            )
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(AstzbColors.GlassFrost, AstzbColors.GlassVeil),
                ),
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = AstzbColors.OutlineLow,
                shape = shape,
            ),
    ) {
        GlassInnerHighlight(shape)
        content()
    }
}

/**
 * 玻璃分区（容器内分组，较薄）。
 */
@Composable
fun GlassSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = AstzbColors.GlassVeil),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(if (title == null) 0.dp else 12.dp),
        ) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            content()
        }
    }
}

/**
 * 玻璃工具栏，带无障碍语义标签。
 */
@Composable
fun GlassToolbar(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable () -> Unit = {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    },
) {
    GlassSurface(
        modifier = modifier
            .semantics {
                if (title != null) contentDescription = "工具栏：$title"
            },
        shape = RoundedCornerShape(30.dp),
    ) {
        content()
    }
}

/**
 * 页面顶部标题玻璃条。
 */
@Composable
fun MacGlassHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing()
        }
    }
}

@Composable
private fun BoxScope.GlassInnerHighlight(shape: RoundedCornerShape) {
    Box(
        Modifier
            .matchParentSize()
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        AstzbColors.GlassHighlight,
                        Color.Transparent,
                        Color(0x1A426AD2),
                    ),
                ),
                shape = shape,
            ),
    )
}

/**
 * 状态胶囊。
 */
@Composable
fun GlassStatusPill(
    text: String,
    status: GlassStatus,
    modifier: Modifier = Modifier,
) {
    val bg = when (status) {
        GlassStatus.SUCCESS -> AstzbColors.StatusSuccessBg
        GlassStatus.WARNING -> AstzbColors.StatusWarningBg
        GlassStatus.ERROR -> AstzbColors.StatusErrorBg
        GlassStatus.INFO -> AstzbColors.StatusInfoBg
    }
    val fg = when (status) {
        GlassStatus.SUCCESS -> AstzbColors.Success
        GlassStatus.WARNING -> AstzbColors.Warning
        GlassStatus.ERROR -> AstzbColors.Error
        GlassStatus.INFO -> AstzbColors.Info
    }
    Surface(
        modifier = modifier.semantics { contentDescription = "状态：$text" },
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(999.dp),
        tonalElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

enum class GlassStatus {
    SUCCESS, WARNING, ERROR, INFO
}
