package com.local.stzb.feature.overlay

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.local.stzb.StzbAppActivity
import com.local.stzb.StzbApplication
import com.local.stzb.auth.AuthEntryGuard
import com.local.stzb.core.designsystem.AstzbTheme
import kotlinx.coroutines.*

class BattlefieldOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = OverlayTeamStore()
    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var collapsed by mutableStateOf(false)

    override fun onCreate() {
        super.onCreate()
        if (AuthEntryGuard.stopServiceIfDenied(this)) return
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WindowManager::class.java)
        OverlayServiceState.setRunning(true)
        startForegroundCompat()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        addOverlay()
        registry.currentState = Lifecycle.State.STARTED
        val repository = (application as StzbApplication).battlefieldRepository
        scope.launch { repository.observeSnapshot().collect(store::accept) }
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                runCatching { repository.refresh() }.onFailure { store.reportError(it.message ?: "刷新失败") }
                delay(2_000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        OverlayServiceState.setRunning(false)
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun addOverlay() {
        val density = resources.displayMetrics.density
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@BattlefieldOverlayService)
            setViewTreeSavedStateRegistryOwner(this@BattlefieldOverlayService)
            setViewTreeViewModelStoreOwner(this@BattlefieldOverlayService)
        }
        val layout = WindowManager.LayoutParams(
            (370 * density).toInt(), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 180 }
        params = layout
        view.setContent {
            val state by store.state.collectAsState()
            val drag = Modifier.pointerInput(collapsed) {
                detectDragGestures { change, amount ->
                    change.consume()
                    moveBy(amount.x.toInt(), amount.y.toInt())
                }
            }
            AstzbTheme {
                BattlefieldOverlayContent(
                    state, collapsed,
                    onCollapse = { collapsed = true; resize(true) },
                    onExpand = { collapsed = false; resize(false) },
                    onClose = ::stopSelf,
                    dragHandleModifier = drag,
                )
            }
        }
        overlayView = view
        windowManager.addView(view, layout)
    }

    private fun moveBy(dx: Int, dy: Int) {
        val layout = params ?: return
        val bounds = windowManager.currentWindowMetrics.bounds
        val size = if (collapsed) OverlaySize(dp(72), dp(72)) else OverlaySize(dp(370), dp(600))
        val position = clampOverlayPosition(layout.x + dx, layout.y + dy, size, OverlaySize(bounds.width(), bounds.height()))
        layout.x = position.x; layout.y = position.y
        overlayView?.let { windowManager.updateViewLayout(it, layout) }
    }

    private fun resize(toCollapsed: Boolean) {
        val layout = params ?: return
        layout.width = if (toCollapsed) dp(72) else dp(370)
        layout.height = if (toCollapsed) dp(72) else WindowManager.LayoutParams.WRAP_CONTENT
        moveBy(0, 0)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun startForegroundCompat() {
        val channelId = "battlefield_overlay"
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channelId, "战场悬浮监控", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(this, 20, Intent(this, StzbAppActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 21, Intent(this, BattlefieldOverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("战场悬浮监控运行中")
            .setContentText("队伍状态将在其他应用上方显示")
            .setContentIntent(open).addAction(0, "停止", stop).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_START = "com.local.stzb.overlay.START"
        const val ACTION_STOP = "com.local.stzb.overlay.STOP"
        private const val NOTIFICATION_ID = 30

        fun start(context: android.content.Context) = ContextCompat.startForegroundService(
            context, Intent(context, BattlefieldOverlayService::class.java).setAction(ACTION_START),
        )
        fun stop(context: android.content.Context) = context.startService(
            Intent(context, BattlefieldOverlayService::class.java).setAction(ACTION_STOP),
        )
    }
}
