package com.dynamicisland.stack

import com.dynamicisland.model.IslandState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║           ISLAND STACK SYSTEM — v2.0                 ║
 * ║  Manages concurrent live activities like iOS 16+     ║
 * ╚══════════════════════════════════════════════════════╝
 *
 * Allows multiple island activities to coexist simultaneously.
 *
 * Visual model (collapsed view):
 *   ╭────────────────────────────╮
 *   │  🎵  [primary]  [●][●]    │  ← stacked dots for secondary items
 *   ╰────────────────────────────╯
 *
 * Expanded model:
 *   ╭────────────────────────────╮
 *   │  PRIMARY CARD (tall)       │
 *   │━━━━━━━━━━━━━━━━━━━━━━━━━━━│
 *   │  secondary card (compact)  │
 *   ╰────────────────────────────╯
 *
 * Rules:
 *  - Max [MAX_STACK_SIZE] concurrent activities
 *  - Priority queue: highest priority is always primary
 *  - CALL always bumps everything else to secondary
 *  - Auto-expire stale items after [ITEM_TTL_MS]
 */
class IslandStackManager {

    companion object {
        const val MAX_STACK_SIZE = 4
        const val ITEM_TTL_MS   = 30_000L  // 30 seconds
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Ordered by priority descending
    private val _stack = MutableStateFlow<List<StackItem>>(emptyList())
    val stack: StateFlow<List<StackItem>> = _stack.asStateFlow()

    /** Primary (top) item — always the highest priority active state */
    val primary: StateFlow<StackItem?> = stack.map { it.firstOrNull() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Secondary items (shown as dots / mini cards below primary) */
    val secondary: StateFlow<List<StackItem>> = stack.map { it.drop(1) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Total number of active items */
    val stackSize: StateFlow<Int> = stack.map { it.size }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    init {
        startExpiryWatcher()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Push a new activity onto the stack.
     * If the same [id] already exists, it replaces the existing item.
     */
    fun push(item: StackItem) {
        val current = _stack.value.toMutableList()

        // Replace if same id exists
        val existingIdx = current.indexOfFirst { it.id == item.id }
        if (existingIdx >= 0) {
            current[existingIdx] = item.copy(addedAt = System.currentTimeMillis())
        } else {
            current.add(item)
            // Trim to max size (remove lowest priority)
            if (current.size > MAX_STACK_SIZE) {
                current.sortByDescending { it.priority }
                current.removeAt(current.lastIndex)
            }
        }

        current.sortByDescending { it.priority }
        _stack.value = current
    }

    /**
     * Remove an activity by id.
     */
    fun remove(id: String) {
        _stack.value = _stack.value.filter { it.id != id }
    }

    /**
     * Remove all items of a given type.
     */
    fun removeByType(type: StackItemType) {
        _stack.value = _stack.value.filter { it.type != type }
    }

    /**
     * Clear the entire stack.
     */
    fun clear() {
        _stack.value = emptyList()
    }

    /**
     * Temporarily bring a secondary item to primary (user tapped a dot).
     * Swaps priorities temporarily for [durationMs].
     */
    fun focusItem(id: String, durationMs: Long = 5000L) {
        val target = _stack.value.firstOrNull { it.id == id } ?: return
        push(target.copy(priority = 999)) // Temporarily highest

        scope.launch {
            delay(durationMs)
            // Restore original priority
            val current = _stack.value.toMutableList()
            val idx = current.indexOfFirst { it.id == id }
            if (idx >= 0) {
                current[idx] = current[idx].copy(priority = target.priority)
                current.sortByDescending { it.priority }
                _stack.value = current
            }
        }
    }

    /**
     * Update live data on an existing item (e.g. progress, timer tick).
     */
    fun update(id: String, newState: IslandState) {
        val current = _stack.value.toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx >= 0) {
            current[idx] = current[idx].copy(
                state    = newState,
                addedAt  = current[idx].addedAt  // Don't reset TTL
            )
            _stack.value = current
        }
    }

    fun destroy() { scope.cancel() }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun startExpiryWatcher() {
        scope.launch {
            while (isActive) {
                delay(5_000L)
                val now = System.currentTimeMillis()
                val filtered = _stack.value.filter { item ->
                    item.neverExpires || (now - item.addedAt) < ITEM_TTL_MS
                }
                if (filtered.size != _stack.value.size) {
                    _stack.value = filtered
                }
            }
        }
    }
}

// ── Data models ───────────────────────────────────────────────────────────────

data class StackItem(
    /** Stable unique identifier — use "call", "music", "notif:{pkg}", etc. */
    val id: String,
    val type: StackItemType,
    val state: IslandState,
    val priority: Int,          // 0–999 (999 = temporary focus)
    val addedAt: Long = System.currentTimeMillis(),
    val neverExpires: Boolean = false,   // true for CALL, active MEDIA
    val accentColor: Long = 0xFFFFFFFF,  // ARGB tint for dot indicator
    val label: String = ""               // Short label for dot tooltip
)

enum class StackItemType {
    CALL, MEDIA, NOTIFICATION, CHARGING, TIMER,
    NAVIGATION, WEATHER, PERFORMANCE, CUSTOM
}

/**
 * Pre-built factory helpers for common stack items.
 */
object StackItemFactory {

    fun call(state: IslandState.PhoneCall) = StackItem(
        id           = "call",
        type         = StackItemType.CALL,
        state        = state,
        priority     = 100,
        neverExpires = true,
        accentColor  = 0xFF34C759,
        label        = "Call"
    )

    fun media(state: IslandState.NowPlaying) = StackItem(
        id           = "media",
        type         = StackItemType.MEDIA,
        state        = state,
        priority     = 60,
        neverExpires = true,
        accentColor  = 0xFF007AFF,
        label        = state.title.take(10)
    )

    fun notification(state: IslandState.Notification, id: String) = StackItem(
        id           = "notif:$id",
        type         = StackItemType.NOTIFICATION,
        state        = state,
        priority     = 30,
        neverExpires = false,
        accentColor  = 0xFFFF9500,
        label        = state.appName
    )

    fun charging(state: IslandState.Charging) = StackItem(
        id           = "charging",
        type         = StackItemType.CHARGING,
        state        = state,
        priority     = 40,
        neverExpires = false,
        accentColor  = 0xFFFF9500,
        label        = "${state.percentage}%"
    )

    fun from(state: IslandState): StackItem = when (state) {
        is IslandState.PhoneCall    -> call(state)
        is IslandState.NowPlaying   -> media(state)
        is IslandState.Notification -> notification(state, state.packageName + System.currentTimeMillis())
        is IslandState.Charging     -> charging(state)
        else -> StackItem(
            id           = "generic:${state::class.simpleName}",
            type         = StackItemType.CUSTOM,
            state        = state,
            priority     = 20,
            neverExpires = false,
            accentColor  = 0xFF8E8E93,
            label        = state::class.simpleName ?: "Island"
        )
    }
}
