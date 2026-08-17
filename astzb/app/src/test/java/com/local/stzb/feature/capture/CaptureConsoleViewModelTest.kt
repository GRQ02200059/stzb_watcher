package com.local.stzb.feature.capture

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Before

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureConsoleViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun mapsRuntimeAndFiltersWholeProtocolIds() = runTest {
        val controller = FakeCaptureController().apply {
            runtime.value = CaptureRuntime(true, true, "127.0.0.1", 1080, 7, "com.netease.stzb.netease", listOf(
                "STZB 5026 行军专表入库",
                "STZB 15026 不应匹配",
                "STZB 5028 战场专表入库",
                "SOCKS 转发 5026",
            ))
        }
        val viewModel = viewModel(controller)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.running)
        assertEquals(7, viewModel.state.value.packetCount)
        viewModel.onIntent(CaptureConsoleIntent.SetProtocolFilter("5026, 5028"))
        assertEquals(listOf("STZB 5026 行军专表入库", "STZB 5028 战场专表入库"), viewModel.state.value.visibleLogs)
    }

    @Test fun requiresTargetBeforeRequestingVpnAndStartsOnlyAfterApproval() = runTest {
        val controller = FakeCaptureController()
        val viewModel = viewModel(controller)
        advanceUntilIdle()

        viewModel.onIntent(CaptureConsoleIntent.RequestStart)
        assertEquals("请先选择目标 App", viewModel.state.value.message)
        assertFalse(viewModel.state.value.requestVpnPermission)

        viewModel.onIntent(CaptureConsoleIntent.SelectApp(InstalledApp("率土之滨", "com.netease.stzb.netease")))
        viewModel.onIntent(CaptureConsoleIntent.RequestStart)
        assertTrue(viewModel.state.value.requestVpnPermission)
        viewModel.onIntent(CaptureConsoleIntent.StartApproved)
        advanceUntilIdle()
        assertEquals(listOf("com.netease.stzb.netease"), controller.starts)
    }

    @Test fun delegatesStopAndClear() = runTest {
        val controller = FakeCaptureController()
        val viewModel = viewModel(controller)
        advanceUntilIdle()
        viewModel.onIntent(CaptureConsoleIntent.Stop)
        viewModel.onIntent(CaptureConsoleIntent.Clear)
        advanceUntilIdle()
        assertEquals(1, controller.stops)
        assertEquals(1, controller.clears)
    }

    private fun TestScope.viewModel(controller: FakeCaptureController) = CaptureConsoleViewModel(controller, dispatcher)

    private class FakeCaptureController : CaptureConsoleController {
        val runtime = MutableStateFlow(CaptureRuntime())
        val starts = mutableListOf<String>()
        var stops = 0
        var clears = 0
        override fun observe(): Flow<CaptureRuntime> = runtime
        override suspend fun installedApps(): List<InstalledApp> = emptyList()
        override suspend fun start(targetPackage: String) { starts += targetPackage }
        override suspend fun stop() { stops++ }
        override suspend fun clear() { clears++ }
        override suspend fun prepareExport(kind: CaptureExportKind): CaptureExport? = null
    }
}
