package com.dynamicisland.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dynamicisland.ai.AIIslandBrain
import com.dynamicisland.ai.ScoredEvent
import com.dynamicisland.customization.AnimationSettings
import com.dynamicisland.customization.DefaultAnimationSettings
import com.dynamicisland.customization.IslandShape
import com.dynamicisland.gesture.GestureIntent
import com.dynamicisland.gesture.IslandGestureEngine
import com.dynamicisland.model.IslandEvent
import com.dynamicisland.model.IslandExpansion
import com.dynamicisland.model.IslandState
import com.dynamicisland.stack.IslandStackManager
import com.dynamicisland.stack.StackItem
import com.dynamicisland.stack.StackItemFactory
import com.dynamicisland.theme.IslandTheme
import com.dynamicisland.theme.ThemeCatalog
import com.dynamicisland.theme.ThemeEngine
import com.dynamicisland.widgets.PerformanceData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║       ISLAND VIEWMODEL V2 — Upgraded Core            ║
 * ║  Integrates AI Brain · Stack · Theme · Gestures      ║
 * ╚══════════════════════════════════════════════════════╝
 */
class IslandViewModelV2(private val context: Context) : ViewModel() {

    // ── Core state ────────────────────────────────────────────────────────────
    private val _islandState = MutableStateFlow<IslandState>(IslandState.Idle)
    val islandState: StateFlow<IslandState> = _islandState.asStateFlow()

    private val _expansion = MutableStateFlow(IslandExpansion.COLLAPSED)
    val expansion: StateFlow<IslandExpansion> = _expansion.asStateFlow()

    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    // ── V2: Stack system ──────────────────────────────────────────────────────
    val stackManager = IslandStackManager()
    val stack: StateFlow<List<StackItem>> = stackManager.stack
    val stackSize: StateFlow<Int> = stackManager.stackSize

    // ── V2: Theme engine ──────────────────────────────────────────────────────
    private val themeEngine = ThemeEngine(context)
    val activeTheme: StateFlow<IslandTheme> = themeEngine.activeTheme
    val adaptiveAlpha: StateFlow<Float> = themeEngine.adaptiveAlpha

    // ── V2: AI brain ──────────────────────────────────────────────────────────
    private val aiBrain = AIIslandBrain(context)

    // ── V2: Gesture engine ────────────────────────────────────────────────────
    val gestureEngine = IslandGestureEngine()

    // ── V2: Animation settings ────────────────────────────────────────────────
    private val _animSettings = MutableStateFlow(DefaultAnimationSettings)
    val animSettings: StateFlow<AnimationSettings> = _animSettings.asStateFlow()

    // ── V2: Island shape ──────────────────────────────────────────────────────
    private val _islandShape = MutableStateFlow(IslandShape.PILL)
    val islandShape: StateFlow<IslandShape> = _islandShape.asStateFlow()

    // ── V2: Control center visibility ─────────────────────────────────────────
    private val _showControlCenter = MutableStateFlow(false)
    val showControlCenter: StateFlow<Boolean> = _showControlCenter.asStateFlow()

    // ── V2: Quick reply ───────────────────────────────────────────────────────
    private val _showQuickReply = MutableStateFlow(false)
    val showQuickReply: StateFlow<Boolean> = _showQuickReply.asStateFlow()
    private val _quickReplySender = MutableStateFlow("")
    val quickReplySender: StateFlow<String> = _quickReplySender.asStateFlow()

    // ── V2: Developer mode ────────────────────────────────────────────────────
    private val _devModeEnabled = MutableStateFlow(false)
    val devModeEnabled: StateFlow<Boolean> = _devModeEnabled.asStateFlow()

    // ── V2: Performance data ──────────────────────────────────────────────────
    private val _perfData = MutableStateFlow(PerformanceData())
    val perfData: StateFlow<PerformanceData> = _perfData.asStateFlow()

    // ── V2: Particle trigger ──────────────────────────────────────────────────
    private val _particleTrigger = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val particleTrigger: SharedFlow<String> = _particleTrigger.asSharedFlow()

    // Auto-dismiss job
    private var autoDismissJob: Job? = null
    private var callTimerJob: Job? = null
    private var callDuration = 0L

    init {
        // Subscribe to AI-processed events
        viewModelScope.launch {
            aiBrain.processedEvent.collect { scored ->
                handleScoredEvent(scored)
            }
        }

        // Subscribe to gesture events
        viewModelScope.launch {
            gestureEngine.events.collect { gesture ->
                handleGesture(gesture)
            }
        }

        // Initialize wallpaper-aware theme
        themeEngine.extractWallpaperColors(autoApply = false)

        // Start AI brain
        // aiBrain is already active on creation
    }

    // ── Event submission ──────────────────────────────────────────────────────

    /**
     * Route event through AI brain for scoring + suppression.
     */
    fun onEvent(event: IslandEvent, sourcePackage: String = "") {
        aiBrain.submitEvent(event, sourcePackage)
    }

    /**
     * Bypass AI — for direct/critical events (calls).
     */
    fun onCriticalEvent(event: IslandEvent) {
        viewModelScope.launch { handleDirectEvent(event) }
    }

    // ── Gesture handling ──────────────────────────────────────────────────────

    fun onTap() {
        gestureEngine.onTap()
    }

    fun onLongPress() {
        gestureEngine.onLongPress()
    }

    fun onSwipeUp() {
        if (_showControlCenter.value) {
            _showControlCenter.value = false
        } else {
            dismiss()
        }
    }

    fun onSwipeDown() {
        when (_expansion.value) {
            IslandExpansion.EXPANDED -> {
                _showControlCenter.value = !_showControlCenter.value
            }
            else -> {
                _expansion.value = IslandExpansion.COMPACT
            }
        }
    }

    fun onDotTap(stackItemId: String) {
        stackManager.focusItem(stackItemId)
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    fun setTheme(themeId: String) = themeEngine.setTheme(themeId)

    fun setShape(shape: IslandShape) { _islandShape.value = shape }

    fun setAnimationSettings(settings: AnimationSettings) { _animSettings.value = settings }

    fun setDevMode(enabled: Boolean) { _devModeEnabled.value = enabled }

    fun dismissControlCenter() { _showControlCenter.value = false }

    fun dismissQuickReply() { _showQuickReply.value = false }

    fun updatePerfData(data: PerformanceData) { _perfData.value = data }

    // ── Music controls ────────────────────────────────────────────────────────

    fun onPlayPause() {
        val s = _islandState.value
        if (s is IslandState.NowPlaying) {
            _islandState.value = s.copy(isPlaying = !s.isPlaying)
        }
    }

    fun onNextTrack() { /* dispatched to MediaSessionManager */ }
    fun onPreviousTrack() { /* dispatched to MediaSessionManager */ }

    // ── Developer test events ─────────────────────────────────────────────────

    fun injectTestEvent(label: String) {
        val event = when {
            label.contains("Call")         -> IslandEvent.ShowCall("Test User", "+1234567890", true)
            label.contains("Music")        -> IslandEvent.MediaUpdate("Test Song", "Test Artist", true, 0.4f, null)
            label.contains("Notification") -> IslandEvent.NotificationReceived("WhatsApp", "Test Notification", "This is a test message", "com.whatsapp")
            label.contains("Charging")     -> IslandEvent.ChargingUpdate(78, true)
            else                           -> IslandEvent.NotificationReceived("System", label, "", "android")
        }
        onCriticalEvent(event)
    }

    // ── Private event handlers ────────────────────────────────────────────────

    private fun handleScoredEvent(scored: ScoredEvent) {
        viewModelScope.launch {
            handleDirectEvent(scored.event)
        }
    }

    private fun handleDirectEvent(event: IslandEvent) {
        when (event) {
            is IslandEvent.ShowCall -> {
                callDuration = 0L
                callTimerJob?.cancel()
                _islandState.value = IslandState.PhoneCall(event.name, event.number, event.isIncoming)
                stackManager.push(
                    StackItemFactory.call(IslandState.PhoneCall(event.name, event.number, event.isIncoming))
                )
                expand(IslandExpansion.EXPANDED)
                triggerParticles("call")
            }
            is IslandEvent.CallEnded -> {
                callTimerJob?.cancel()
                stackManager.remove("call")
                scheduleAutoDismiss(300)
            }
            is IslandEvent.MediaUpdate -> {
                val state = IslandState.NowPlaying(
                    event.title, event.artist, event.albumArt,
                    event.isPlaying, event.progress
                )
                _islandState.value = state
                stackManager.push(StackItemFactory.media(state))
                if (_expansion.value == IslandExpansion.COLLAPSED) {
                    expand(IslandExpansion.COMPACT)
                }
                _isVisible.value = true
            }
            is IslandEvent.MediaStopped -> {
                stackManager.removeByType(com.dynamicisland.stack.StackItemType.MEDIA)
                scheduleAutoDismiss(2000)
            }
            is IslandEvent.NotificationReceived -> {
                if (_islandState.value !is IslandState.PhoneCall) {
                    val state = IslandState.Notification(
                        event.appName, event.title, event.text,
                        packageName = event.packageName
                    )
                    _islandState.value = state
                    stackManager.push(
                        StackItemFactory.notification(state, event.packageName + System.currentTimeMillis())
                    )
                    expand(IslandExpansion.COMPACT)
                    _isVisible.value = true
                    scheduleAutoDismiss(4000)
                }
            }
            is IslandEvent.ChargingUpdate -> {
                val cur = _islandState.value
                if (cur !is IslandState.PhoneCall && cur !is IslandState.NowPlaying) {
                    val state = IslandState.Charging(event.percentage, event.isCharging)
                    _islandState.value = state
                    if (event.isCharging) {
                        stackManager.push(StackItemFactory.charging(state))
                        expand(IslandExpansion.COMPACT)
                        _isVisible.value = true
                        triggerParticles("charging")
                        scheduleAutoDismiss(3000)
                    }
                }
            }
            is IslandEvent.Dismiss -> dismiss()
        }
    }

    private fun handleGesture(gesture: com.dynamicisland.gesture.IslandGesture) {
        when (gesture) {
            is com.dynamicisland.gesture.IslandGesture.Tap -> {
                _expansion.value = when (_expansion.value) {
                    IslandExpansion.COLLAPSED -> IslandExpansion.COMPACT
                    IslandExpansion.COMPACT   -> IslandExpansion.EXPANDED
                    IslandExpansion.EXPANDED  -> IslandExpansion.COMPACT
                }
                autoDismissJob?.cancel()
            }
            is com.dynamicisland.gesture.IslandGesture.LongPress -> {
                _expansion.value = IslandExpansion.EXPANDED
                // Show quick reply if notification
                val cur = _islandState.value
                if (cur is IslandState.Notification) {
                    _quickReplySender.value = cur.title
                    _showQuickReply.value   = true
                }
            }
            is com.dynamicisland.gesture.IslandGesture.SwipeUp   -> dismiss()
            is com.dynamicisland.gesture.IslandGesture.SwipeDown  -> onSwipeDown()
            is com.dynamicisland.gesture.IslandGesture.DoubleTap  -> {
                // Double tap to fullscreen
                _expansion.value = IslandExpansion.EXPANDED
            }
            else -> {}
        }
    }

    private fun expand(to: IslandExpansion) {
        _expansion.value = to
    }

    private fun dismiss() {
        autoDismissJob?.cancel()
        _expansion.value = IslandExpansion.COLLAPSED
        _showControlCenter.value = false
        _showQuickReply.value    = false
        viewModelScope.launch {
            delay(280)
            _islandState.value = IslandState.Idle
            _isVisible.value   = false
        }
    }

    private fun scheduleAutoDismiss(delayMs: Long) {
        autoDismissJob?.cancel()
        autoDismissJob = viewModelScope.launch {
            delay(delayMs)
            dismiss()
        }
    }

    private fun triggerParticles(type: String) {
        if (_animSettings.value.particlesEnabled) {
            viewModelScope.launch { _particleTrigger.emit(type) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoDismissJob?.cancel()
        callTimerJob?.cancel()
        stackManager.destroy()
        aiBrain.destroy()
        themeEngine.destroy()
    }
}
