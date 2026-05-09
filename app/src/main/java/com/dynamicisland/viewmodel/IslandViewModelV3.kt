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
import com.dynamicisland.model.*
import com.dynamicisland.offline.OfflineCacheManager
import com.dynamicisland.sensor.SensorEngine
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
 * ║      ISLAND VIEWMODEL V3 — Full-Power Core          ║
 * ║  + Offline cache  + Sensor engine  + Split-pill     ║
 * ║  + Weather  + Steps  + Alarm  + Focus  + 12 themes  ║
 * ╚══════════════════════════════════════════════════════╝
 */
class IslandViewModelV3(private val context: Context) : ViewModel() {

    // ── Core state ─────────────────────────────────────────────────────────────
    private val _islandState = MutableStateFlow<IslandState>(IslandState.Idle)
    val islandState: StateFlow<IslandState> = _islandState.asStateFlow()

    private val _expansion = MutableStateFlow(IslandExpansion.COLLAPSED)
    val expansion: StateFlow<IslandExpansion> = _expansion.asStateFlow()

    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    // V3: Split-pill secondary state
    private val _secondaryState = MutableStateFlow<IslandState?>(null)
    val secondaryState: StateFlow<IslandState?> = _secondaryState.asStateFlow()

    // ── Stack ──────────────────────────────────────────────────────────────────
    val stackManager = IslandStackManager()
    val stack: StateFlow<List<StackItem>> = stackManager.stack
    val stackSize: StateFlow<Int> = stackManager.stackSize

    // ── Theme ──────────────────────────────────────────────────────────────────
    private val themeEngine = ThemeEngine(context)
    val activeTheme: StateFlow<IslandTheme> = themeEngine.activeTheme
    val adaptiveAlpha: StateFlow<Float> = themeEngine.adaptiveAlpha

    // ── AI ─────────────────────────────────────────────────────────────────────
    private val aiBrain = AIIslandBrain(context)

    // ── Gesture ────────────────────────────────────────────────────────────────
    val gestureEngine = IslandGestureEngine()

    // ── Animation ─────────────────────────────────────────────────────────────
    private val _animSettings = MutableStateFlow(DefaultAnimationSettings)
    val animSettings: StateFlow<AnimationSettings> = _animSettings.asStateFlow()

    private val _islandShape = MutableStateFlow(IslandShape.PILL)
    val islandShape: StateFlow<IslandShape> = _islandShape.asStateFlow()

    // ── V3: Offline cache ──────────────────────────────────────────────────────
    val offlineCache = OfflineCacheManager(context)
    val isOffline: StateFlow<Boolean> = offlineCache.networkState
        .map { !it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ── V3: Sensor engine ──────────────────────────────────────────────────────
    private val sensorEngine = SensorEngine(context)
    val autoBrightnessHint: StateFlow<Float> = sensorEngine.autoBrightnessHint
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.5f)

    // ── V3: Step count (persistent) ────────────────────────────────────────────
    private val _stepState = MutableStateFlow<IslandState.StepCounter?>(null)
    val stepState: StateFlow<IslandState.StepCounter?> = _stepState.asStateFlow()

    // ── V3: Weather (cached) ───────────────────────────────────────────────────
    private val _weatherState = MutableStateFlow<IslandState.Weather?>(null)
    val weatherState: StateFlow<IslandState.Weather?> = _weatherState.asStateFlow()

    // ── Control center ─────────────────────────────────────────────────────────
    private val _showControlCenter = MutableStateFlow(false)
    val showControlCenter: StateFlow<Boolean> = _showControlCenter.asStateFlow()

    // ── Quick reply ────────────────────────────────────────────────────────────
    private val _showQuickReply = MutableStateFlow(false)
    val showQuickReply: StateFlow<Boolean> = _showQuickReply.asStateFlow()
    private val _quickReplySender = MutableStateFlow("")
    val quickReplySender: StateFlow<String> = _quickReplySender.asStateFlow()

    // ── Performance ────────────────────────────────────────────────────────────
    private val _perfData = MutableStateFlow(PerformanceData())
    val perfData: StateFlow<PerformanceData> = _perfData.asStateFlow()

    // ── Particle trigger ───────────────────────────────────────────────────────
    private val _particleTrigger = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val particleTrigger: SharedFlow<String> = _particleTrigger.asSharedFlow()

    // ── Dev mode ───────────────────────────────────────────────────────────────
    private val _devModeEnabled = MutableStateFlow(false)
    val devModeEnabled: StateFlow<Boolean> = _devModeEnabled.asStateFlow()

    private var autoDismissJob: Job? = null
    private var callTimerJob:   Job? = null
    private var callDuration          = 0L
    private var focusCountdownJob:  Job? = null

    init {
        viewModelScope.launch { aiBrain.processedEvent.collect { handleScoredEvent(it) } }
        observeGestures()
        initSensors()
        restoreOfflineState()
        loadSavedPreferences()
    }

    // ── Initialization ────────────────────────────────────────────────────────

    private fun initSensors() {
        if (sensorEngine.hasStepCounter) {
            viewModelScope.launch {
                sensorEngine.stepCounterFlow.collect { totalSteps ->
                    val cached = offlineCache.loadSteps()
                    val delta  = (totalSteps - cached.first).coerceAtLeast(0)
                    val steps  = cached.first + delta
                    val cal    = (steps * 0.04f).toInt()
                    val dist   = steps * 0.0008f
                    offlineCache.saveSteps(steps, cal, dist)
                    val stepState = IslandState.StepCounter(
                        steps = steps, calories = cal, distanceKm = dist, goal = offlineCache.loadStepGoal()
                    )
                    _stepState.value = stepState
                    if (_islandState.value is IslandState.StepCounter) {
                        _islandState.value = stepState
                    }
                }
            }
        }
        // Ambient light → adaptive glow
        if (sensorEngine.hasAmbientLight) {
            viewModelScope.launch {
                sensorEngine.ambientLightFlow.collect { lux ->
                    themeEngine.setAdaptiveAlpha(lux)
                }
            }
        }
        // Heart rate supplement for step state
        if (sensorEngine.hasHeartRate) {
            viewModelScope.launch {
                sensorEngine.heartRateFlow.collect { bpm ->
                    val current = _stepState.value
                    if (current != null) {
                        _stepState.value = current.copy(heartRate = bpm)
                        if (_islandState.value is IslandState.StepCounter)
                            _islandState.value = _stepState.value!!
                    }
                }
            }
        }
    }

    private fun restoreOfflineState() {
        viewModelScope.launch {
            val restored = offlineCache.restoreLastKnownState()
            if (restored != null && _islandState.value is IslandState.Idle) {
                showState(restored)
            }
            // Always restore cached weather
            offlineCache.loadCachedWeather()?.let { _weatherState.value = it }
        }
    }

    private fun loadSavedPreferences() {
        viewModelScope.launch {
            val themeId = offlineCache.loadThemeId()
            if (themeId != null) themeEngine.setThemeById(themeId)
            val shape   = offlineCache.loadIslandShape()
            _islandShape.value = runCatching { IslandShape.valueOf(shape) }.getOrDefault(IslandShape.PILL)
        }
    }

    // ── Event handling ────────────────────────────────────────────────────────

    fun onEvent(event: IslandEvent, sourcePackage: String = "") {
        aiBrain.submitEvent(event, sourcePackage)
        // Per-app theme hint
        if (sourcePackage.isNotBlank()) {
            val hint = themeEngine.suggestThemeForPackage(sourcePackage)
            if (hint != null) themeEngine.setTheme(hint)
        }
    }

    fun onCriticalEvent(event: IslandEvent) {
        viewModelScope.launch { handleEvent(event) }
    }

    private fun handleScoredEvent(scored: ScoredEvent) {
        viewModelScope.launch { handleEvent(scored.event) }
    }

    private suspend fun handleEvent(event: IslandEvent) {
        when (event) {
            // V1/V2
            is IslandEvent.ShowCall -> {
                val s = IslandState.PhoneCall(event.name, event.number, event.isIncoming)
                showState(s); startCallTimer()
                _particleTrigger.emit("call")
            }
            is IslandEvent.CallEnded -> {
                callTimerJob?.cancel(); dismissAfter(3000)
            }
            is IslandEvent.MediaUpdate -> {
                showState(IslandState.NowPlaying(event.title, event.artist, event.albumArt,
                    event.isPlaying, event.progress))
            }
            is IslandEvent.MediaStopped -> { if (_islandState.value is IslandState.NowPlaying) showIdle() }
            is IslandEvent.NotificationReceived -> {
                showState(IslandState.Notification(event.appName, event.title, event.text,
                    packageName = event.packageName, replyable = event.replyable))
                dismissAfter(4000)
            }
            is IslandEvent.ChargingUpdate -> {
                showState(IslandState.Charging(event.percentage, event.isCharging,
                    event.isWireless, event.estimatedMinutes))
                if (event.isCharging) { _particleTrigger.emit("charging"); dismissAfter(4000) }
                else showIdle()
            }
            is IslandEvent.Dismiss -> showIdle()

            // V3
            is IslandEvent.WeatherUpdate -> {
                showState(event.state)
                offlineCache.cacheWeather(event.state)
                _weatherState.value = event.state
                _particleTrigger.emit("weather")
            }
            is IslandEvent.StepUpdate -> {
                val stepState = IslandState.StepCounter(
                    steps = event.steps, calories = event.calories,
                    distanceKm = event.distanceKm, heartRate = event.heartRate,
                    goal = offlineCache.loadStepGoal()
                )
                _stepState.value = stepState
                showState(stepState)
                offlineCache.saveSteps(event.steps, event.calories, event.distanceKm)
                _particleTrigger.emit("steps")
                dismissAfter(5000)
            }
            is IslandEvent.AlarmFired -> {
                showState(IslandState.Alarm(event.label, event.timeString))
                _particleTrigger.emit("alarm")
                _expansion.value = IslandExpansion.EXPANDED
            }
            is IslandEvent.AlarmSnoozed -> {
                showIdle(); dismissAfter(500)
            }
            is IslandEvent.AlarmDismissed -> showIdle()

            is IslandEvent.FocusStarted -> {
                val focusState = IslandState.FocusMode(event.name, event.minutes, event.minutes)
                showState(focusState)
                offlineCache.saveFocusSession(event.name, event.minutes)
                startFocusCountdown(event.name, event.minutes)
            }
            is IslandEvent.FocusEnded -> {
                focusCountdownJob?.cancel()
                offlineCache.clearFocusSession()
                showIdle()
            }
            is IslandEvent.ClipboardChanged -> {
                val cat = detectClipCategory(event.text)
                showState(IslandState.ClipboardSnippet(event.text, event.sourceApp, cat))
                dismissAfter(3000)
            }
            is IslandEvent.SportScoreUpdate -> {
                showState(event.state)
                dismissAfter(6000)
            }
            is IslandEvent.VoiceAssistActivated -> {
                showState(IslandState.VoiceAssist(event.prompt))
                _expansion.value = IslandExpansion.COMPACT
            }
            is IslandEvent.VoiceAssistDismissed -> showIdle()
            is IslandEvent.DownloadUpdate -> {
                showState(IslandState.DownloadProgress(event.appName, event.progress, event.speedMbps))
            }
            is IslandEvent.DownloadComplete -> { dismissAfter(1500) }
            is IslandEvent.ScreenRecordingStarted -> {
                val recState = IslandState.ScreenRecording(true, 0L, event.isCasting, event.target)
                showState(recState)
                offlineCache.saveRecordingState(true)
            }
            is IslandEvent.ScreenRecordingStopped -> {
                offlineCache.saveRecordingState(false)
                showIdle()
            }
            is IslandEvent.SleepSummaryReceived -> {
                showState(event.state)
                _expansion.value = IslandExpansion.EXPANDED
                dismissAfter(8000)
            }
            is IslandEvent.LyricsUpdate -> {
                val current = _islandState.value
                if (current is IslandState.NowPlaying)
                    _islandState.value = current.copy(lyrics = event.lyricLine)
            }
            // V3: Split-pill
            is IslandEvent.SplitSecondaryState -> {
                _secondaryState.value = event.state
                _expansion.value = IslandExpansion.SPLIT
            }
            is IslandEvent.ClearSplit -> {
                _secondaryState.value = null
                _expansion.value = IslandExpansion.COMPACT
            }
            else -> { /* no-op */ }
        }
    }

    // ── Public controls ───────────────────────────────────────────────────────

    fun setTheme(theme: IslandTheme) {
        themeEngine.setTheme(theme)
        viewModelScope.launch { offlineCache.saveThemeId(theme.id) }
    }

    fun setIslandShape(shape: IslandShape) {
        _islandShape.value = shape
        viewModelScope.launch { offlineCache.saveIslandShape(shape.name) }
    }

    fun setStepGoal(goal: Int) {
        viewModelScope.launch { offlineCache.saveStepGoal(goal) }
    }

    fun toggleHaptics(enabled: Boolean) {
        _animSettings.value = _animSettings.value.copy(hapticsEnabled = enabled)
        viewModelScope.launch { offlineCache.saveHapticsEnabled(enabled) }
    }

    fun toggleParticles(enabled: Boolean) {
        _animSettings.value = _animSettings.value.copy(particlesEnabled = enabled)
        viewModelScope.launch { offlineCache.saveParticlesEnabled(enabled) }
    }

    fun toggleGlow(enabled: Boolean) {
        _animSettings.value = _animSettings.value.copy(glowEnabled = enabled)
        viewModelScope.launch { offlineCache.saveGlowEnabled(enabled) }
    }

    fun showWeatherFromCache() {
        viewModelScope.launch {
            val cached = offlineCache.loadCachedWeather()
            if (cached != null) showState(cached)
        }
    }

    fun showSteps() {
        val current = _stepState.value
        if (current != null) showState(current)
    }

    fun dismissIsland() { showIdle() }

    fun onTap() {
        _expansion.value = when (_expansion.value) {
            IslandExpansion.COLLAPSED -> IslandExpansion.COMPACT
            IslandExpansion.COMPACT   -> IslandExpansion.EXPANDED
            IslandExpansion.EXPANDED  -> IslandExpansion.COLLAPSED
            IslandExpansion.SPLIT     -> IslandExpansion.SPLIT
        }
    }

    fun onLongPress() {
        if (_expansion.value != IslandExpansion.EXPANDED)
            _expansion.value = IslandExpansion.EXPANDED
        else
            _showControlCenter.value = true
    }

    fun onSwipeUp()      { showIdle() }
    fun onSwipeDown()    { if (_expansion.value == IslandExpansion.EXPANDED) _expansion.value = IslandExpansion.COMPACT }
    fun onPlayPause()    { /* forward to media session */ }
    fun onNextTrack()    { /* forward to media session */ }
    fun onPreviousTrack() { /* forward to media session */ }
    fun onAlarmSnooze()  { onEvent(IslandEvent.AlarmSnoozed(5)) }
    fun onAlarmDismiss() { onEvent(IslandEvent.AlarmDismissed) }
    fun dismissControlCenter() { _showControlCenter.value = false }
    fun sendQuickReply(text: String) { _showQuickReply.value = false }
    fun dismissQuickReply()         { _showQuickReply.value = false }
    fun onDotTap(id: String)        { stackManager.setActive(id) }
    fun toggleDevMode()             { _devModeEnabled.value = !_devModeEnabled.value }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun showState(state: IslandState) {
        _islandState.value = state
        _isVisible.value   = true
        if (_expansion.value == IslandExpansion.COLLAPSED)
            _expansion.value = IslandExpansion.COMPACT
        stackManager.push(StackItemFactory.from(state))
        autoDismissJob?.cancel()
    }

    private fun showIdle() {
        _islandState.value   = IslandState.Idle
        _secondaryState.value = null
        _expansion.value     = IslandExpansion.COLLAPSED
        stackManager.clear()
        autoDismissJob?.cancel()
    }

    private fun dismissAfter(ms: Long) {
        autoDismissJob?.cancel()
        autoDismissJob = viewModelScope.launch {
            delay(ms)
            if (_islandState.value !is IslandState.FocusMode &&
                _islandState.value !is IslandState.NowPlaying &&
                _islandState.value !is IslandState.Alarm) {
                showIdle()
            }
        }
    }

    private fun startCallTimer() {
        callDuration = 0L
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                callDuration++
                val current = _islandState.value
                if (current is IslandState.PhoneCall)
                    _islandState.value = current.copy(duration = callDuration)
                else break
            }
        }
    }

    private fun startFocusCountdown(name: String, totalMinutes: Int) {
        focusCountdownJob?.cancel()
        focusCountdownJob = viewModelScope.launch {
            var remaining = totalMinutes
            while (remaining > 0) {
                delay(60_000)
                remaining--
                _islandState.value = IslandState.FocusMode(name, remaining, totalMinutes)
            }
            showIdle()
            offlineCache.clearFocusSession()
        }
    }

    private fun observeGestures() {
        viewModelScope.launch {
            gestureEngine.gestures.collect { intent ->
                when (intent) {
                    is GestureIntent.Tap       -> onTap()
                    is GestureIntent.LongPress -> onLongPress()
                    is GestureIntent.SwipeUp   -> onSwipeUp()
                    is GestureIntent.SwipeDown -> onSwipeDown()
                    else -> {}
                }
            }
        }
    }

    private fun detectClipCategory(text: String): ClipCategory = when {
        text.startsWith("http://") || text.startsWith("https://") -> ClipCategory.URL
        text.matches(Regex("[+\\d\\s()-]{7,}"))                   -> ClipCategory.PHONE
        text.contains("@") && text.contains(".")                  -> ClipCategory.EMAIL
        text.contains("\n") && (text.contains("{") || text.contains("def ") || text.contains("fun ")) -> ClipCategory.CODE
        else                                                       -> ClipCategory.TEXT
    }

    override fun onCleared() {
        super.onCleared()
        offlineCache.cleanup()
        themeEngine.cleanup()
    }
}
