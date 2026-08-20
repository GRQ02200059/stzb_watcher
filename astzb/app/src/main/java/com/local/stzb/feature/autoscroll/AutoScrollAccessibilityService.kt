package com.local.stzb.feature.autoscroll

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class AutoScrollAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loopJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        AutoScrollState.setServiceReady(true)
        observeRunning()
    }

    private fun observeRunning() {
        scope.launch {
            AutoScrollState.running.collect { running ->
                if (running) startLoop() else stopLoop()
            }
        }
    }

    private fun startLoop() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            while (isActive) {
                doSwipe()
                delay(800)
            }
        }
    }

    private fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
    }

    private fun doSwipe() {
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val x = w / 2f
        val startY = h * 0.78f
        val endY = h * 0.22f

        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 350)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stopLoop() }

    override fun onDestroy() {
        AutoScrollState.setServiceReady(false)
        AutoScrollState.setRunning(false)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start() { AutoScrollState.setRunning(true) }
        fun stop() { AutoScrollState.setRunning(false) }
    }
}
