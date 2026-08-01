package com.local.stzb.feature.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object OverlayServiceState {
    private val mutableRunning = MutableStateFlow(false)
    val running = mutableRunning.asStateFlow()
    fun setRunning(value: Boolean) { mutableRunning.value = value }
}

data class OverlayPosition(val x: Int, val y: Int)
data class OverlaySize(val width: Int, val height: Int)

fun clampOverlayPosition(x: Int, y: Int, window: OverlaySize, screen: OverlaySize) = OverlayPosition(
    x.coerceIn(0, (screen.width - window.width).coerceAtLeast(0)),
    y.coerceIn(0, (screen.height - window.height).coerceAtLeast(0)),
)
