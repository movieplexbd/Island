package com.dynamicisland.service

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.savedstate.*
import com.dynamicisland.MainActivity
import com.dynamicisland.layout.buildOverlayParams
import com.dynamicisland.model.IslandEvent
import com.dynamicisland.ui.DynamicIslandOverlayV3
import com.dynamicisland.viewmodel.IslandViewModelV3
import kotlinx.coroutines.*
import androidx.compose.ui.platform.ComposeView

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║     DYNAMIC ISLAND SERVICE V3 — Wiring Layer        ║
 * ║  Auto-restart · Offline-first · Sensor subscription ║
 * ╚══════════════════════════════════════════════════════╝
 */
class DynamicIslandServiceV3 : Service(),
    ViewModelStoreOwner,
    SavedStateRegistryOwner,
    LifecycleOwner {

    companion object {
        const val CHANNEL_ID      = "dynamic_island_v3"
        const val NOTIFICATION_ID = 3001
        const val ACTION_STOP     = "com.dynamicisland.v3.STOP"
        const val ACTION_THEME    = "com.dynamicisland.v3.THEME"
        const val ACTION_WEATHER  = "com.dynamicisland.v3.WEATHER"
        const val EXTRA_THEME_ID  = "theme_id"

        @Volatile var instance: DynamicIslandServiceV3? = null

        fun sendEvent(event: IslandEvent, pkg: String = "") {
            instance?.viewModel?.onEvent(event, pkg)
        }
        fun sendCriticalEvent(event: IslandEvent) {
            instance?.viewModel?.onCriticalEvent(event)
        }
    }

    // ── Lifecycle boilerplate ─────────────────────────────────────────────────
    private val lifecycleOwner  = ServiceLifecycleOwner()
    override val lifecycle: Lifecycle get() = lifecycleOwner.lifecycle
    private val vmStore         = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = vmStore
    private val stateRegCtrl    = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = stateRegCtrl.savedStateRegistry

    lateinit var viewModel: IslandViewModelV3
        private set

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        instance = this
        stateRegCtrl.performAttach()
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        viewModel      = IslandViewModelV3(applicationContext)
        windowManager  = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, buildNotification())
        createOverlay()
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_THEME -> {
                val themeId = intent.getStringExtra(EXTRA_THEME_ID)
                if (themeId != null) {
                    val theme = com.dynamicisland.theme.ThemeCatalog.byId(themeId)
                    viewModel.setTheme(theme)
                }
            }
            ACTION_WEATHER -> viewModel.showWeatherFromCache()
        }
        return START_STICKY   // V3: auto-restart if killed
    }

    private fun createOverlay() {
        val params = buildOverlayParams()
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@DynamicIslandServiceV3)
            setViewTreeViewModelStoreOwner(this@DynamicIslandServiceV3)
            setViewTreeSavedStateRegistryOwner(this@DynamicIslandServiceV3)
            setContent {
                val state          by viewModel.islandState.collectAsState()
                val expansion      by viewModel.expansion.collectAsState()
                val secondaryState by viewModel.secondaryState.collectAsState()
                val stack          by viewModel.stack.collectAsState()
                val theme          by viewModel.activeTheme.collectAsState()
                val animSettings   by viewModel.animSettings.collectAsState()
                val shape          by viewModel.islandShape.collectAsState()
                val showCC         by viewModel.showControlCenter.collectAsState()
                val showQR         by viewModel.showQuickReply.collectAsState()
                val qrSender       by viewModel.quickReplySender.collectAsState()
                val adaptiveAlpha  by viewModel.adaptiveAlpha.collectAsState()
                val isOffline      by viewModel.isOffline.collectAsState()
                val brightnessHint by viewModel.autoBrightnessHint.collectAsState()
                val particleTrig   by viewModel.particleTrigger.collectAsState(null)

                DynamicIslandOverlayV3(
                    state                 = state,
                    expansion             = expansion,
                    secondaryState        = secondaryState,
                    stack                 = stack,
                    theme                 = theme,
                    animSettings          = animSettings,
                    shape                 = shape,
                    gestureEngine         = viewModel.gestureEngine,
                    showControlCenter     = showCC,
                    showQuickReply        = showQR,
                    quickReplySender      = qrSender,
                    particleTrigger       = particleTrig,
                    adaptiveAlpha         = adaptiveAlpha,
                    isOffline             = isOffline,
                    autoBrightnessHint    = brightnessHint,
                    onTap                 = viewModel::onTap,
                    onLongPress           = viewModel::onLongPress,
                    onSwipeUp             = viewModel::onSwipeUp,
                    onSwipeDown           = viewModel::onSwipeDown,
                    onDotTap              = viewModel::onDotTap,
                    onPlayPause           = viewModel::onPlayPause,
                    onNextTrack           = viewModel::onNextTrack,
                    onPreviousTrack       = viewModel::onPreviousTrack,
                    onControlCenterDismiss = viewModel::dismissControlCenter,
                    onQuickReplySend      = viewModel::sendQuickReply,
                    onQuickReplyDismiss   = viewModel::dismissQuickReply,
                    onAlarmSnooze         = viewModel::onAlarmSnooze,
                    onAlarmDismiss        = viewModel::onAlarmDismiss,
                )
            }
        }
        overlayView = composeView
        windowManager.addView(composeView, params)
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Dynamic Island V3",
                NotificationManager.IMPORTANCE_LOW).apply {
                description    = "Dynamic Island overlay service"
                setShowBadge(false)
            }
        )
        val stopIntent = PendingIntent.getService(this, 0,
            Intent(this, DynamicIslandServiceV3::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE)
        val openIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Dynamic Island V3 Active")
            .setContentText("Tap to open settings")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        serviceScope.cancel()
        vmStore.clear()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

// ── Simple lifecycle owner for service ────────────────────────────────────────
class ServiceLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
    fun handleLifecycleEvent(event: Lifecycle.Event) { registry.handleLifecycleEvent(event) }
}
