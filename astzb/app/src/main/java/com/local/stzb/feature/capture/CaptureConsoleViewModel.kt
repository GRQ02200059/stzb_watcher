package com.local.stzb.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CaptureConsoleViewModel(
    private val controller: CaptureConsoleController,
    private val worker: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CaptureConsoleUiState())
    val state: StateFlow<CaptureConsoleUiState> = mutableState.asStateFlow()
    private var logs = emptyList<String>()

    init {
        viewModelScope.launch {
            controller.observe().collectLatest { runtime ->
                logs = runtime.logs
                val current = mutableState.value
                mutableState.value = current.copy(
                    running = runtime.running,
                    nativeReady = runtime.nativeReady,
                    socksHost = runtime.socksHost,
                    socksPort = runtime.socksPort,
                    packetCount = runtime.packetCount,
                    selectedApp = current.selectedApp ?: runtime.targetPackage.takeIf(String::isNotBlank)?.let { InstalledApp(it, it) },
                    visibleLogs = filterParsedLogs(runtime.logs, current.protocolFilter),
                )
            }
        }
    }

    fun onIntent(intent: CaptureConsoleIntent) {
        when (intent) {
            is CaptureConsoleIntent.SetProtocolFilter -> mutableState.value = mutableState.value.copy(
                protocolFilter = intent.value,
                visibleLogs = filterParsedLogs(logs, intent.value),
            )
            is CaptureConsoleIntent.SelectApp -> mutableState.value = mutableState.value.copy(selectedApp = intent.app, message = null)
            CaptureConsoleIntent.LoadApps -> perform { mutableState.value = mutableState.value.copy(apps = controller.installedApps()) }
            CaptureConsoleIntent.RequestStart -> {
                if (mutableState.value.selectedApp == null) mutableState.value = mutableState.value.copy(message = "请先选择目标 App")
                else if (!mutableState.value.nativeReady) mutableState.value = mutableState.value.copy(message = "抓包 native 组件不可用")
                else mutableState.value = mutableState.value.copy(requestVpnPermission = true, message = null)
            }
            CaptureConsoleIntent.StartApproved -> perform("抓包启动请求已发送") {
                controller.start(requireNotNull(mutableState.value.selectedApp).packageName)
                mutableState.value = mutableState.value.copy(requestVpnPermission = false)
            }
            CaptureConsoleIntent.VpnPermissionHandled -> mutableState.value = mutableState.value.copy(requestVpnPermission = false)
            CaptureConsoleIntent.Stop -> perform("已请求停止抓包") { controller.stop() }
            CaptureConsoleIntent.Clear -> perform("已清空内存日志与监控状态") { controller.clear() }
            is CaptureConsoleIntent.Message -> mutableState.value = mutableState.value.copy(message = intent.value, requestVpnPermission = false)
        }
    }

    suspend fun prepareExport(kind: CaptureExportKind): CaptureExport? = withContext(worker) { controller.prepareExport(kind) }

    private fun perform(success: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, message = null)
            runCatching { withContext(worker) { block() } }
                .onSuccess { mutableState.value = mutableState.value.copy(busy = false, message = success) }
                .onFailure { mutableState.value = mutableState.value.copy(busy = false, message = it.message ?: "操作失败") }
        }
    }
}
