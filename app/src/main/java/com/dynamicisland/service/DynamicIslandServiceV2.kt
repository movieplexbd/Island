package com.dynamicisland.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dynamicisland.MainActivity
import com.dynamicisland.layout.DeviceFormFactor
import com.dynamicisland.layout.buildOverlayParams
import com.dynamicisland.layout.layoutSpecFor
import com.dynamicisland.layout.rememberFormFactor
import com.dynamicisland.model.IslandEvent
import com.dynamicisland.ui.DynamicIslandOverlayV2
import com.dynamicisland.viewmodel.IslandViewModelV2
import com.dynamicisland.widgets.BatteryHealthMonitor
import com.dynamicisland.widgets.PerformanceMonitor
import kotlinx.coroutines.*
import androidx.compose.ui.platform.ComposeView

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║    DYNAMIC ISLAND SERVICE V2 — Upgraded Wiring       ║
 * ║  Integrates all V2 systems into a single service     ║
 * ╚══════════════════════════════════════════════════════╝
 */
class DynamicIslandServiceV2 : Service(),
    ViewModelStoreOwner,
    SavedStateRegistryOwner,
    androidx.lifecycle.LifecycleOwner {

    companion object {
        const val CHANNEL_ID      = "dynamic_island_v2"
        const val NOTIFICATION_ID = 2001
        const val ACTION_STOP     = "com.dynamicisland.v2.STOP"

        @Volatile var instance: DynamicIslandServiceV2? = null

        fun sendEvent(event: IslandEvent, pkg: String = "") {
            instance?.viewModel?.onEvent(event, pkg)
        }
        fun sendCriticalEvent(event: IslandEvent) {
            instance?.viewModel?.onCriticalEvent(event)
        }
    }

    // ── Lifecycle boilerplate ─────────────────────────────────────────────────
    private val lifecycleOwner = ServiceLifecycleOwner()
    override val lifecycle: Lifecycle get() = lifecycleOwner.lifecycle
    private val vmStore = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = vmStore
    private val stateRegCtrl = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = stateRegCtrl.savedStateRegistry

    // ── V2 components ─────────────────────────────────────────────────────────
    lateinit var viewModel: IslandViewModelV2
        private set

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var performanceMonitor: PerformanceMonitor? = null
    private var batteryMonitor: BatteryHealthMonitor? = null
    private var mediaManager: IslandMediaSessionManager? = null
    private var phoneReceiver: PhoneStateReceiver? = null

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        instance  = this
        viewModel = IslandViewModelV2(applicationContext)

        stateRegCtrl.performRestore(null)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        startForeground(NOTIFICATION_ID, buildNotification())
        setupSubsystems()
        createOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        teardownSubsystems()
        removeOverlay()
        vmStore.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Subsystem setup ───────────────────────────────────────────────────────

    private fun setupSubsystems() {
        // Performance monitor (FPS, RAM, CPU, network)
        performanceMonitor = PerformanceMonitor(applicationContext).also {
            it.start()
            serviceScope.launch {
                it.data.collect { data -> viewModel.updatePerfData(data) }
            }
        }

        // Battery health monitor
        batteryMonitor = BatteryHealthMonitor(applicationContext).also {
            it.refresh()
        }

        // Media session manager
        mediaManager = IslandMediaSessionManager(applicationContext).also { it.start() }

        // Phone state receiver
        phoneReceiver = PhoneStateReceiver().also { receiver ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    receiver,
                    IntentFilter(android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED),
                    RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(
                    receiver,
                    IntentFilter(android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED)
                )
            }
        }
    }

    private fun teardownSubsystems() {
        performanceMonitor?.stop()
        mediaManager?.stop()
        try { phoneReceiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
    }

    // ── Overlay ───────────────────────────────────────────────────────────────

    private fun createOverlay() {
        val spec   = layoutSpecFor(DeviceFormFactor.PHONE_PORTRAIT)
        val params = buildOverlayParams(this, spec)

        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@DynamicIslandServiceV2)
            setViewTreeViewModelStoreOwner(this@DynamicIslandServiceV2)
            setViewTreeSavedStateRegistryOwner(this@DynamicIslandServiceV2)

            setContent {
                val state           by viewModel.islandState.collectAsState()
                val expansion       by viewModel.expansion.collectAsState()
                val stack           by viewModel.stack.collectAsState()
                val theme           by viewModel.activeTheme.collectAsState()
                val animSettings    by viewModel.animSettings.collectAsState()
                val islandShape     by viewModel.islandShape.collectAsState()
                val showCC          by viewModel.showControlCenter.collectAsState()
                val showQR          by viewModel.showQuickReply.collectAsState()
                val quickReplySender by viewModel.quickReplySender.collectAsState()
                val adaptiveAlpha   by viewModel.adaptiveAlpha.collectAsState()

                // Collect particle triggers
                var lastParticle by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf<String?>(null)
                }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    viewModel.particleTrigger.collect { lastParticle = it }
                }

                DynamicIslandOverlayV2(
                    state               = state,
                    expansion           = expansion,
                    stack               = stack,
                    theme               = theme,
                    animSettings        = animSettings,
                    shape               = islandShape,
                    gestureEngine       = viewModel.gestureEngine,
                    showControlCenter   = showCC,
                    showQuickReply      = showQR,
                    quickReplySender    = quickReplySender,
                    particleTrigger     = lastParticle,
                    adaptiveAlpha       = adaptiveAlpha,
                    onTap               = { viewModel.onTap() },
                    onLongPress         = { viewModel.onLongPress() },
                    onSwipeUp           = { viewModel.onSwipeUp() },
                    onSwipeDown         = { viewModel.onSwipeDown() },
                    onDotTap            = { viewModel.onDotTap(it) },
                    onPlayPause         = { viewModel.onPlayPause() },
                    onNextTrack         = { viewModel.onNextTrack() },
                    onPreviousTrack     = { viewModel.onPreviousTrack() },
                    onControlCenterDismiss = { viewModel.dismissControlCenter() },
                    onQuickReplySend    = { /* send via notification intent */ viewModel.dismissQuickReply() },
                    onQuickReplyDismiss = { viewModel.dismissQuickReply() }
                )
            }
        }

        windowManager.addView(overlayView, params)
    }

    private fun removeOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID, "Dynamic Island V2", NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false); description = "Overlay active" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, DynamicIslandServiceV2::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Dynamic Island V2")
            .setContentText("AI-powered overlay active")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setOngoing(true).setSilent(true).build()
    }
}
