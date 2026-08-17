package com.local.stzb.feature.overlay

import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BattlefieldOverlayServiceTest {
    @Test fun startsAndStopsFromTheApplicationContextWhenPermissionIsGranted() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("appops set ${context.packageName} android:system_alert_window allow")
            .close()
        delay(300)
        assertTrue(Settings.canDrawOverlays(context))
        BattlefieldOverlayService.start(context)
        repeat(20) { if (!OverlayServiceState.running.value) delay(100) }
        assertTrue(OverlayServiceState.running.value)
        BattlefieldOverlayService.stop(context)
        repeat(20) { if (OverlayServiceState.running.value) delay(100) }
        assertTrue(!OverlayServiceState.running.value)
    }
}
