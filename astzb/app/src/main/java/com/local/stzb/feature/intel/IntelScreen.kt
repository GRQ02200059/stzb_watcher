package com.local.stzb.feature.intel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.local.stzb.core.ui.EmptyPanel
import com.local.stzb.domain.intel.IntelSnapshot

enum class IntelPage { MAP, ANNOUNCEMENTS }

@Composable
fun IntelScreen(page: IntelPage, initial: IntelSnapshot, onBack: () -> Unit, onSearch: (String) -> IntelSnapshot, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var snapshot by remember(initial) { mutableStateOf(initial) }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回更多") }
            Column {
                Text(if (page == IntelPage.MAP) "地图与城池" else "游戏公告", style = MaterialTheme.typography.headlineMedium)
                if (page == IntelPage.MAP) Text("地块 ${snapshot.totalCells} · 命名城池 ${snapshot.namedCities}")
            }
        }
        if (page == IntelPage.MAP) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; snapshot = onSearch(it) },
                label = { Text("搜索城池或坐标") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (snapshot.cells.isEmpty()) EmptyPanel("本机还没有地图格子数据", null, {})
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(snapshot.cells, key = { it.wid }) { cell ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                        Text(cell.cityName.ifBlank { cell.typeName }, style = MaterialTheme.typography.titleLarge)
                        Text("坐标 ${cell.coordinates} · WID ${cell.wid}")
                        Text("${cell.typeName} · ${cell.ownerName.ifBlank { "暂无归属" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                }
            }
        } else {
            if (snapshot.announcements.isEmpty()) EmptyPanel("本机还没有游戏公告", null, {})
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(snapshot.announcements, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleLarge)
                        Text(item.content.take(300), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                }
            }
        }
    }
}
