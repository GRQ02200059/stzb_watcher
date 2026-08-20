package com.local.stzb.feature.autoscroll

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
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
import com.local.stzb.core.designsystem.AstzbTheme

class AutoScrollOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore = ViewModelStore()
    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WindowManager::class.java)
        startForegroundCompat()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        addOverlay()
        registry.currentState = Lifecycle.State.STARTED
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        AutoScrollState.setRunning(false)
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun addOverlay() {
        val density = resources.displayMetrics.density
        val size = (64 * density).toInt()
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@AutoScrollOverlayService)
            setViewTreeSavedStateRegistryOwner(this@AutoScrollOverlayService)
            setViewTreeViewModelStoreOwner(this@AutoScrollOverlayService)
        }
        val layout = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 16; y = 300 }
        view.setContent {
            val running by AutoScrollState.running.collectAsState()
            AstzbTheme {
                Surface(
                    shape = CircleShape,
                    color = if (running) Color(0xFFEF4444) else Color(0xFF6366F1),
                    shadowElevation = 10.dp,
                    modifier = Modifier.size(64.dp).clickable {
                        AutoScrollState.setRunning(!running)
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = if (running) "停止自动滑动" else "开始自动滑动",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }
        }
        overlayView = view
        windowManager.addView(view, layout)
    }

    private fun startForegroundCompat() {
        val channelId = "auto_scroll_overlay"
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channelId, "自动滑动", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(this, 30, Intent(this, StzbAppActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 31, Intent(this, AutoScrollOverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_slideshow)
            .setContentTitle("自动滑动悬浮按钮运行中")
            .setContentText("点击悬浮按钮开始或停止自动滑动")
            .setContentIntent(open).addAction(0, "停止", stop).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    companion object {
        const val ACTION_START = "com.local.stzb.autoscroll.START"
        const val ACTION_STOP = "com.local.stzb.autoscroll.STOP"
        private const val NOTIFICATION_ID = 40

        fun start(context: android.content.Context) = ContextCompat.startForegroundService(
            context, Intent(context, AutoScrollOverlayService::class.java).setAction(ACTION_START),
        )
        fun stop(context: android.content.Context) = context.startService(
            Intent(context, AutoScrollOverlayService::class.java).setAction(ACTION_STOP),
        )
    }
}
