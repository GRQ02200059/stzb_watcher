package com.local.stzb.feature.battlefield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.local.stzb.core.ui.EmptyPanel
import com.local.stzb.core.ui.ErrorPanel
import com.local.stzb.core.ui.LoadState
import com.local.stzb.core.ui.LoadingPanel
import com.local.stzb.core.ui.GlassSurface
import com.local.stzb.domain.battlefield.BattlefieldEvent

@Composable
fun BattlefieldScreen(
    state: BattlefieldUiState,
    onIntent: (BattlefieldIntent) -> Unit,
    onEventClick: (BattlefieldEvent) -> Unit,
    overlayRunning: Boolean = false,
    onToggleOverlay: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LifecycleStartEffect(Unit) {
        onIntent(BattlefieldIntent.SetActive(true))
        onStopOrDispose { onIntent(BattlefieldIntent.SetActive(false)) }
    }

    Box(
        modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        when (val loadState = state.loadState) {
            LoadState.Loading -> BattlefieldStatePanel { LoadingPanel() }
            is LoadState.Empty -> BattlefieldStatePanel {
                EmptyPanel(
                    message = loadState.message,
                    actionLabel = loadState.actionLabel,
                    onAction = { onIntent(BattlefieldIntent.Refresh) },
                )
            }
            is LoadState.Error -> BattlefieldStatePanel {
                ErrorPanel(
                    message = loadState.message,
                    retryable = loadState.retryable,
                    onRetry = { onIntent(BattlefieldIntent.Refresh) },
                )
            }
            is LoadState.Content -> BattlefieldContent(
                snapshot = loadState.value,
                refreshing = loadState.refreshing,
                onIntent = onIntent,
                onEventClick = onEventClick,
                overlayRunning = overlayRunning,
                onToggleOverlay = onToggleOverlay,
            )
        }
    }
}

@Composable
private fun BattlefieldStatePanel(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp)) {
        GlassSurface(Modifier.fillMaxWidth()) {
            Text("实时战场", Modifier.padding(16.dp), style = MaterialTheme.typography.headlineMedium)
        }
        Box(Modifier.weight(1f).padding(top = 12.dp)) { content() }
    }
}

@Composable
private fun BattlefieldContent(
    snapshot: com.local.stzb.domain.battlefield.BattlefieldSnapshot,
    refreshing: Boolean,
    onIntent: (BattlefieldIntent) -> Unit,
    onEventClick: (BattlefieldEvent) -> Unit,
    overlayRunning: Boolean,
    onToggleOverlay: () -> Unit,
) {
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("header") { BattlefieldHeader(snapshot.capture, snapshot.paused, onIntent, overlayRunning, onToggleOverlay) }
            item("metrics") { BattlefieldMetricsGrid(snapshot.metrics) }
            item("filters") { EventCategoryFilters(snapshot.selectedCategories, onIntent) }
            if (snapshot.bufferedEventCount > 0) {
                item("buffered") {
                    NewEventsButton(snapshot.bufferedEventCount, onClick = {
                        onIntent(BattlefieldIntent.ConsumeBufferedEvents)
                    })
                }
            }
            items(snapshot.events, key = { it.id }) { event ->
                BattlefieldEventCard(event, onClick = { onEventClick(event) })
            }
        }
        if (refreshing) {
            LinearProgressIndicator(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .semantics { contentDescription = "正在刷新" },
            )
        }
    }
}
