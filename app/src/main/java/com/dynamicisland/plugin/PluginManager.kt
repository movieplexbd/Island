package com.dynamicisland.plugin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dynamicisland.model.IslandExpansion
import com.dynamicisland.model.IslandState
import com.dynamicisland.stack.StackItem
import kotlinx.coroutines.flow.Flow

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║       PLUGIN ARCHITECTURE — v2.0                    ║
 * ║  Modular island plugin system for third-party devs  ║
 * ╚══════════════════════════════════════════════════════╝
 *
 * DEVELOPER GUIDE:
 *
 * 1. Implement [IslandPlugin] in your module
 * 2. Register via [PluginManager.register]
 * 3. Your plugin provides its own:
 *    - StackItems (pushed to the stack)
 *    - Compose UI (rendered inside the island)
 *    - Settings screen
 *    - Event sources (flows)
 *
 * Example minimal plugin:
 *
 * ```kotlin
 * class WeatherPlugin : IslandPlugin {
 *     override val id = "weather_v1"
 *     override val displayName = "Weather Live"
 *     override val version = "1.0"
 *
 *     override fun stackItems(): Flow<List<StackItem>> = weatherFlow()
 *
 *     @Composable
 *     override fun IslandContent(state: IslandState, expansion: IslandExpansion) {
 *         WeatherIslandContent(state, expansion)
 *     }
 * }
 * ```
 */

// ── Plugin interface ──────────────────────────────────────────────────────────

interface IslandPlugin {
    /** Unique reverse-domain ID, e.g. "com.example.weather" */
    val id: String

    /** Human-readable display name */
    val displayName: String

    /** Plugin version string */
    val version: String

    /** Plugin category for UI grouping */
    val category: PluginCategory get() = PluginCategory.OTHER

    /** Optional icon resource or drawable descriptor */
    val iconEmoji: String get() = "🔌"

    /**
     * Flow of StackItems this plugin wants to push onto the island.
     * PluginManager subscribes and merges all plugin flows.
     */
    fun stackItems(): Flow<List<StackItem>>

    /**
     * Renders the plugin's content inside the island when this plugin's
     * StackItem is the primary item.
     */
    @Composable
    fun IslandContent(state: IslandState, expansion: IslandExpansion)

    /**
     * Optional compact content for when the plugin's item is secondary.
     */
    @Composable
    fun SecondaryContent(item: StackItem, modifier: Modifier) {}

    /**
     * Settings composable — shown in the settings screen plugin panel.
     */
    @Composable
    fun SettingsContent() {}

    /** Called when plugin is activated (service started). */
    fun onActivate() {}

    /** Called when plugin is deactivated (service stopped). */
    fun onDeactivate() {}

    /** Whether this plugin's items can be dismissed by swipe. */
    val isDismissible: Boolean get() = true

    /** Whether this plugin supports dark/light theme variants. */
    val supportsTheming: Boolean get() = true
}

enum class PluginCategory {
    PRODUCTIVITY, MEDIA, COMMUNICATION, HEALTH,
    GAMING, UTILITIES, SYSTEM, OTHER
}

// ── Plugin Manager ────────────────────────────────────────────────────────────

object PluginManager {

    private val _plugins = mutableMapOf<String, PluginEntry>()

    val all: List<PluginEntry> get() = _plugins.values.sortedBy { it.plugin.displayName }
    val active: List<PluginEntry> get() = all.filter { it.isEnabled }

    /**
     * Register a new plugin. Idempotent — re-registering same id updates.
     */
    fun register(plugin: IslandPlugin) {
        val entry = _plugins[plugin.id] ?: PluginEntry(plugin = plugin, isEnabled = true)
        _plugins[plugin.id] = entry.copy(plugin = plugin)
    }

    fun unregister(id: String) {
        _plugins[id]?.plugin?.onDeactivate()
        _plugins.remove(id)
    }

    fun enable(id: String) {
        val entry = _plugins[id] ?: return
        _plugins[id] = entry.copy(isEnabled = true)
        entry.plugin.onActivate()
    }

    fun disable(id: String) {
        val entry = _plugins[id] ?: return
        _plugins[id] = entry.copy(isEnabled = false)
        entry.plugin.onDeactivate()
    }

    fun isEnabled(id: String): Boolean = _plugins[id]?.isEnabled == true

    fun getPlugin(id: String): IslandPlugin? = _plugins[id]?.plugin

    fun findByCategory(category: PluginCategory): List<IslandPlugin> =
        active.filter { it.plugin.category == category }.map { it.plugin }
}

data class PluginEntry(
    val plugin: IslandPlugin,
    val isEnabled: Boolean,
    val installedAt: Long = System.currentTimeMillis()
)

// ── Built-in plugins ──────────────────────────────────────────────────────────

/** Weather live activity plugin */
class WeatherPlugin : IslandPlugin {
    override val id = "builtin.weather"
    override val displayName = "Weather Live"
    override val version = "1.0"
    override val category = PluginCategory.UTILITIES
    override val iconEmoji = "🌤️"

    private val weatherFlow = kotlinx.coroutines.flow.MutableStateFlow<List<StackItem>>(emptyList())
    override fun stackItems() = weatherFlow

    @Composable
    override fun IslandContent(state: IslandState, expansion: IslandExpansion) {
        WeatherIslandContent(state, expansion)
    }
}

/** FPS + performance gaming overlay */
class GamingPlugin : IslandPlugin {
    override val id = "builtin.gaming"
    override val displayName = "Gaming Mode"
    override val version = "1.0"
    override val category = PluginCategory.GAMING
    override val iconEmoji = "🎮"

    private val stackFlow = kotlinx.coroutines.flow.MutableStateFlow<List<StackItem>>(emptyList())
    override fun stackItems() = stackFlow

    @Composable
    override fun IslandContent(state: IslandState, expansion: IslandExpansion) {
        GamingIslandContent(state, expansion)
    }
}

// ── Placeholder composables (implemented in their respective files) ────────────

@Composable
private fun WeatherIslandContent(state: IslandState, expansion: IslandExpansion) {
    // Full implementation in WeatherIsland.kt
}

@Composable
private fun GamingIslandContent(state: IslandState, expansion: IslandExpansion) {
    // Full implementation in GamingIsland.kt
}
