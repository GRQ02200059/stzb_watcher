package com.example.myapplication

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.roundToInt

object StatusBarInsetHelper {

    fun applyTopSafeSpacing(view: View, extraTopDp: Int = 8) {
        val initialLeft = view.paddingLeft
        val initialTop = view.paddingTop
        val initialRight = view.paddingRight
        val initialBottom = view.paddingBottom
        val extraTopPx = (view.resources.displayMetrics.density * extraTopDp).roundToInt()

        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            target.setPadding(
                initialLeft,
                initialTop + statusBarTop + extraTopPx,
                initialRight,
                initialBottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }
}
