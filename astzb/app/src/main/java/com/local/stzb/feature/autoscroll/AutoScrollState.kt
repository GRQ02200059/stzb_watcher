package com.local.stzb.feature.autoscroll

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AutoScrollState {
    private val mutableRunning = MutableStateFlow(false)
    val running = mutableRunning.asStateFlow()

    private val mutableServiceReady = MutableStateFlow(false)
    val serviceReady = mutableServiceReady.asStateFlow()

    fun setRunning(value: Boolean) { mutableRunning.value = value }
    fun setServiceReady(value: Boolean) { mutableServiceReady.value = value }
}
