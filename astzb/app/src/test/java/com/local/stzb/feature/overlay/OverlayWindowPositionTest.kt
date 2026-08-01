package com.local.stzb.feature.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayWindowPositionTest {
    @Test fun clampsExpandedAndCollapsedWindowsInsideScreen() {
        assertEquals(OverlayPosition(0, 0), clampOverlayPosition(-20, -30, OverlaySize(370, 600), OverlaySize(1080, 2400)))
        assertEquals(OverlayPosition(710, 1800), clampOverlayPosition(900, 2200, OverlaySize(370, 600), OverlaySize(1080, 2400)))
        assertEquals(OverlayPosition(1008, 2328), clampOverlayPosition(1200, 2500, OverlaySize(72, 72), OverlaySize(1080, 2400)))
    }
}
