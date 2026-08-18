package com.local.stzb.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
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
 * 玻璃表面基础容器（一级）。
 * 雾白半透表面 + 细描边 + 柔和阴影。不依赖系统实时模糊。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color(0x14000000),
                spotColor = Color(0x14000000),
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(AstzbColors.GlassMedium, AstzbColors.GlassLow),
                ),
                shape = RoundedCornerShape(20.dp),
            )
            .border(
                width = 1.dp,
                color = AstzbColors.Outline,
                shape = RoundedCornerShape(20.dp),
            ),
        content = content,
    )
}

/**
 * 玻璃卡片（二级，16dp 圆角，较浅描边）。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .shadow(
                elevation = 1.dp,
                shape = shape,
                ambientColor = Color(0x0D000000),
                spotColor = Color(0x0D000000),
            )
            .clip(shape)
            .background(
                color = AstzbColors.GlassLow,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = AstzbColors.OutlineLow,
                shape = shape,
            ),
        content = content,
    )
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AstzbColors.GlassLow),
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
        modifier = modifier.semantics {
            if (title != null) contentDescription = "工具栏：$title"
        },
    ) {
        content()
    }
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
