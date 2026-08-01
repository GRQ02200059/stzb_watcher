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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun LoadingPanel(modifier: Modifier = Modifier) {
    StatePanelContainer(modifier) {
        CircularProgressIndicator(
            modifier = Modifier.semantics { contentDescription = "正在加载" },
        )
        Text("正在加载战场动态", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun EmptyPanel(
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatePanelContainer(modifier) {
        Icon(Icons.Outlined.Inbox, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        if (actionLabel != null) {
            Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
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
    StatePanelContainer(modifier) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        if (retryable) {
            Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun StatePanelContainer(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        content()
    }
}
