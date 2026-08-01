package com.local.stzb.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LegacyToolsScreen(
    openCaptureConsole: () -> Unit,
    openLegacyDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("更多工具")
        Button(
            onClick = openCaptureConsole,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Icon(Icons.Outlined.NetworkCheck, contentDescription = null)
            Text("经典抓包控制台", modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedButton(
            onClick = openLegacyDashboard,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Icon(Icons.Outlined.Dashboard, contentDescription = null)
            Text("经典数据页面", modifier = Modifier.padding(start = 8.dp))
        }
    }
}
