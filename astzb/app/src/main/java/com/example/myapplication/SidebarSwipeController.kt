package com.example.myapplication

import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs

class SidebarSwipeController(
    private val context: Context,
    private val root: ViewGroup,
    private val drawer: View,
    private val scrim: View,
    private val toggleButton: TextView,
    private val onStateChanged: (Boolean) -> Unit,
) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val edgeWidth = dp(64)
    private val animationDuration = 220L
    private var startX = 0f
    private var startY = 0f
    private var dragging = false
    private var open = false

    val isOpen: Boolean get() = open

    init {
        drawer.post {
            drawer.visibility = View.VISIBLE
            drawer.translationX = -drawerWidth()
            scrim.visibility = View.GONE
            scrim.alpha = 0f
            toggleButton.text = "菜单"
            toggleButton.contentDescription = "点击打开侧栏"
        }
        installScrimGesture()
    }

    fun toggle() {
        setOpen(!open)
    }

    fun setOpen(value: Boolean, animate: Boolean = true) {
        val width = drawerWidth()
        open = value
        onStateChanged(value)
        toggleButton.text = if (value) "收起" else "菜单"
        toggleButton.contentDescription = if (value) "点击或左滑收起侧栏" else "点击打开侧栏"
        drawer.visibility = View.VISIBLE
        if (value) {
            scrim.visibility = View.VISIBLE
        }
        val targetX = if (value) 0f else -width
        val targetAlpha = if (value) 1f else 0f
        if (!animate) {
            drawer.translationX = targetX
            scrim.alpha = targetAlpha
            if (!value) scrim.visibility = View.GONE
            return
        }
        drawer.animate()
            .translationX(targetX)
            .setDuration(animationDuration)
            .setInterpolator(DecelerateInterpolator())
            .start()
        scrim.animate()
            .alpha(targetAlpha)
            .setDuration(animationDuration)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (!value) scrim.visibility = View.GONE
            }
            .start()
    }

    private fun installScrimGesture() {
        scrim.setOnClickListener { setOpen(false) }
        scrim.setOnTouchListener { _, event -> handleCloseGesture(event) }
    }

    private fun handleCloseGesture(event: MotionEvent): Boolean {
        if (!open) return false
        val width = drawerWidth()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                dragging = false
                drawer.animate().cancel()
                scrim.animate().cancel()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - startX
                val dy = event.rawY - startY
                if (!dragging && abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                    dragging = true
                }
                if (dragging) {
                    setDrawerOffset((width + dx).coerceIn(0f, width), width)
                }
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (!dragging) {
                    setOpen(false)
                } else {
                    val offset = (width + event.rawX - startX).coerceIn(0f, width)
                    setOpen(offset > width * 0.62f)
                }
                dragging = false
                return true
            }
        }
        return true
    }

    private fun setDrawerOffset(offset: Float, width: Float) {
        drawer.translationX = -width + offset
        scrim.alpha = (offset / width).coerceIn(0f, 1f)
    }

    private fun drawerWidth(): Float {
        val measured = drawer.width.takeIf { it > 0 } ?: dp(252)
        return measured.toFloat()
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
