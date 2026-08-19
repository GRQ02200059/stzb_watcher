package com.local.stzb.feature.capture

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.local.stzb.StzbApplication
import com.local.stzb.data.capture.AndroidCaptureConsoleController
import hev.sockstun.Preferences
import hev.sockstun.TProxyService
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealGameCaptureIntegrationTest {
    @Test
    fun capturesKnownProtocolPersistsRowsAndRestoresNetwork() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("仅在显式 realCapture=true 时运行", InstrumentationRegistry.getArguments().getString("realCapture") == "true")
        val context = instrumentation.targetContext
        val app = context.applicationContext as StzbApplication
        val targetPackage = "com.netease.stzb.netease"
        context.packageManager.getApplicationInfo(targetPackage, 0)
        assertTrue("native hev-socks5-tunnel 未加载", TProxyService.isNativeReady())
        app.authAccessGuard.grant()
        authorizeVpnIfNeeded(instrumentation, context)
        val controller = AndroidCaptureConsoleController(context)
        try {
            controller.start(targetPackage)
            withTimeout(30_000) {
                controller.observe().first { it.running && it.evidence.vpnEstablished }
            }

            val launch = context.packageManager.getLaunchIntentForPackage(targetPackage)
                ?: Intent().setClassName(targetPackage, "com.netease.ntunisdk.external.protocol.ProtocolLauncher")
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(launch)
            delay(12_000)

            // Pixel_6 AVD is 1080x2400. The existing role-selection screen's Start button is centered near y=2020.
            shell(instrumentation, "input tap 540 2020")

            val captured = withTimeout(180_000) {
                controller.observe().first { runtime ->
                    runtime.evidence.socksConnections > 0 &&
                        runtime.evidence.knownProtocolCount > 0 &&
                        runtime.evidence.databaseRowDelta > 0
                }
            }
            assertTrue(captured.evidence.protocolCounts.isNotEmpty())
            assertTrue(captured.evidence.databaseRowDelta > 0)

            controller.stop()
            val complete = withTimeout(30_000) { controller.observe().first { it.evidence.complete } }
            val export = controller.prepareExport(CaptureExportKind.EVIDENCE)
            checkNotNull(export)
            val text = export.bytes.toString(Charsets.UTF_8)
            assertTrue(text.contains("complete=true"))
            assertTrue(complete.evidence.networkRestored)
        } finally {
            runCatching { controller.stop() }
            Preferences(context).enable = false
        }
    }

    private fun shell(instrumentation: Instrumentation, command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return descriptor.use { fd -> BufferedReader(InputStreamReader(android.os.ParcelFileDescriptor.AutoCloseInputStream(fd))).readText() }
    }

    private fun authorizeVpnIfNeeded(instrumentation: Instrumentation, context: Context) {
        val prepare = VpnService.prepare(context) ?: return
        prepare.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(prepare)
        val device = UiDevice.getInstance(instrumentation)
        device.waitForIdle()
        val allow = listOf("允许", "确定", "OK", "Allow")
            .firstNotNullOfOrNull { text -> device.findObject(By.text(text)) }
            ?: device.findObject(By.res("android", "button1"))
            ?: error("未找到 VPN 授权按钮")
        allow.click()
        device.waitForIdle()
        check(VpnService.prepare(context) == null) { "VPN 授权未生效" }
    }
}
