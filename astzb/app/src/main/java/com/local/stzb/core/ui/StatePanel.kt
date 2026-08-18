package com.local.stzb.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.local.stzb.core.designsystem.AstzbColors

@Composable
fun LoadingPanel(modifier: Modifier = Modifier) {
    StatePanelContainer(
        modifier = modifier.semantics { contentDescription = "正在加载战场动态" },
        title = "加载中",
    ) {
        CircularProgressIndicator(
            modifier = Modifier.semantics { contentDescription = "加载进度指示器" },
        )
        Text(
            text = "正在加载战场动态",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun EmptyPanel(
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatePanelContainer(
        modifier = modifier.semantics { contentDescription = "空状态：$message" },
        title = "空状态",
    ) {
        Icon(
            Icons.Outlined.Inbox,
            contentDescription = null,
            tint = AstzbColors.Info,
        )
        Text(
            text = message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (actionLabel != null) {
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = actionLabel },
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun ErrorPanel(
    message: String,
    retryable: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatePanelContainer(
        modifier = modifier.semantics { contentDescription = "错误：$message" },
        title = "错误",
    ) {
        Icon(
            Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = AstzbColors.Error,
        )
        Text(
            text = message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (retryable) {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "重试操作" },
            ) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun StatePanelContainer(
    modifier: Modifier,
    title: String,
    content: @Composable () -> Unit,
) {
    GlassSurface(
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}
