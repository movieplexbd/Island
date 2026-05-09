package com.dynamicisland.theme

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║           THEME ENGINE — v2.0                       ║
 * ║  Wallpaper extraction · Material You · Per-app      ║
 * ╚══════════════════════════════════════════════════════╝
 */

// ── Island color palette ──────────────────────────────────────────────────────

data class IslandTheme(
    val id: String,
    val name: String,
    val baseColor: Color,
    val accentColor: Color,
    val glowColor: Color,
    val glassTint: Color,
    val textColor: Color = Color.White,
    val isLightBackground: Boolean = false
)

// Built-in theme catalog
object ThemeCatalog {
    val OBSIDIAN = IslandTheme(
        id = "obsidian", name = "Obsidian",
        baseColor   = Color(0xFF0A0A0A),
        accentColor = Color(0xFF007AFF),
        glowColor   = Color(0xFF0055CC),
        glassTint   = Color(0x201A1A2E)
    )
    val AURORA = IslandTheme(
        id = "aurora", name = "Aurora",
        baseColor   = Color(0xFF0D1B2A),
        accentColor = Color(0xFF00F5C4),
        glowColor   = Color(0xFF00C49A),
        glassTint   = Color(0x2000F5C4)
    )
    val SAKURA = IslandTheme(
        id = "sakura", name = "Sakura",
        baseColor   = Color(0xFF1A0A12),
        accentColor = Color(0xFFFF6B8A),
        glowColor   = Color(0xFFFF3D6B),
        glassTint   = Color(0x30FF6B8A)
    )
    val SOLAR = IslandTheme(
        id = "solar", name = "Solar",
        baseColor   = Color(0xFF1A1000),
        accentColor = Color(0xFFFFB700),
        glowColor   = Color(0xFFFF8C00),
        glassTint   = Color(0x30FFB700)
    )
    val GHOST = IslandTheme(
        id = "ghost", name = "Ghost",
        baseColor         = Color(0xFFF8F8FA),
        accentColor       = Color(0xFF000000),
        glowColor         = Color(0x40000000),
        glassTint         = Color(0xA0FFFFFF),
        textColor         = Color.Black,
        isLightBackground = true
    )
    val NEON = IslandTheme(
        id = "neon", name = "Neon",
        baseColor   = Color(0xFF050510),
        accentColor = Color(0xFF00FF88),
        glowColor   = Color(0xFF00CC66),
        glassTint   = Color(0x2000FF88)
    )

    val all = listOf(OBSIDIAN, AURORA, SAKURA, SOLAR, GHOST, NEON)
}

// ── Theme engine ──────────────────────────────────────────────────────────────

class ThemeEngine(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _activeTheme = MutableStateFlow(ThemeCatalog.OBSIDIAN)
    val activeTheme: StateFlow<IslandTheme> = _activeTheme.asStateFlow()

    // Per-app overrides (packageName → themeId)
    private val perAppOverrides = mutableMapOf<String, String>()

    // Extracted wallpaper palette
    private val _wallpaperPalette = MutableStateFlow<WallpaperPalette?>(null)
    val wallpaperPalette: StateFlow<WallpaperPalette?> = _wallpaperPalette.asStateFlow()

    // Adaptive transparency (0.0=fully opaque, 1.0=fully transparent)
    private val _adaptiveAlpha = MutableStateFlow(0.0f)
    val adaptiveAlpha: StateFlow<Float> = _adaptiveAlpha.asStateFlow()

    // ── Public API ────────────────────────────────────────────────────────────

    fun setTheme(themeId: String) {
        _activeTheme.value = ThemeCatalog.all.firstOrNull { it.id == themeId }
            ?: ThemeCatalog.OBSIDIAN
    }

    fun setPerAppTheme(packageName: String, themeId: String) {
        perAppOverrides[packageName] = themeId
    }

    fun getThemeForApp(packageName: String): IslandTheme {
        val overrideId = perAppOverrides[packageName]
        return if (overrideId != null) {
            ThemeCatalog.all.firstOrNull { it.id == overrideId } ?: _activeTheme.value
        } else {
            _activeTheme.value
        }
    }

    /**
     * Extracts dominant + vibrant colors from the current wallpaper
     * and optionally auto-applies a matching theme.
     */
    fun extractWallpaperColors(autoApply: Boolean = false) {
        scope.launch {
            val palette = extractColorsAsync()
            _wallpaperPalette.value = palette

            // Compute adaptive transparency based on wallpaper brightness
            palette?.let { p ->
                val brightness = ColorUtils.calculateLuminance(p.dominant.toArgb()).toFloat()
                _adaptiveAlpha.value = when {
                    brightness > 0.7f -> 0.15f  // Light wallpaper → more transparent
                    brightness < 0.3f -> 0.05f  // Dark wallpaper  → more opaque
                    else              -> 0.10f
                }

                if (autoApply) {
                    val generated = generateThemeFromColor(p.vibrant ?: p.dominant)
                    _activeTheme.value = generated
                }
            }
        }
    }

    /**
     * Material You integration — reads system dynamic colors (Android 12+).
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun applyMaterialYou() {
        scope.launch {
            val resources = context.resources
            val primaryColor = try {
                Color(resources.getColor(android.R.color.system_accent1_500, context.theme))
            } catch (_: Exception) {
                Color(0xFF007AFF)
            }

            val generated = generateThemeFromColor(primaryColor, name = "Material You")
            _activeTheme.value = generated
        }
    }

    fun destroy() { scope.cancel() }

    // ── Private ───────────────────────────────────────────────────────────────

    private suspend fun extractColorsAsync(): WallpaperPalette? = withContext(Dispatchers.IO) {
        try {
            val wm      = WallpaperManager.getInstance(context)
            val drawable = wm.drawable ?: return@withContext null
            val bitmap  = Bitmap.createBitmap(
                minOf(drawable.intrinsicWidth, 200),
                minOf(drawable.intrinsicHeight, 200),
                Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(canvas)

            // Simple palette extraction — sample a grid of pixels
            val colors = sampleBitmapColors(bitmap, sampleCount = 100)
            val dominant = colors.groupBy { it }
                .maxByOrNull { it.value.size }?.key ?: Color.Black
            val vibrant  = colors.maxByOrNull { ColorUtils.calculateLuminance(it.toArgb()) }

            WallpaperPalette(
                dominant    = dominant,
                vibrant     = vibrant,
                muted       = colors.minByOrNull { ColorUtils.calculateLuminance(it.toArgb()) },
                brightness  = ColorUtils.calculateLuminance(dominant.toArgb()).toFloat()
            )
        } catch (_: Exception) { null }
    }

    private fun sampleBitmapColors(bitmap: Bitmap, sampleCount: Int): List<Color> {
        val colors = mutableListOf<Color>()
        val w = bitmap.width
        val h = bitmap.height
        repeat(sampleCount) {
            val x = (Math.random() * w).toInt().coerceIn(0, w - 1)
            val y = (Math.random() * h).toInt().coerceIn(0, h - 1)
            colors += Color(bitmap.getPixel(x, y))
        }
        return colors
    }

    private fun generateThemeFromColor(color: Color, name: String = "Custom"): IslandTheme {
        val argb = color.toArgb()
        // Darken for base, use original for accent
        val darkBase = Color(ColorUtils.blendARGB(argb, android.graphics.Color.BLACK, 0.85f))
        val glowC    = Color(ColorUtils.blendARGB(argb, android.graphics.Color.WHITE, 0.2f))
        val glassTint = color.copy(alpha = 0.2f)

        return IslandTheme(
            id          = "generated_${System.currentTimeMillis()}",
            name        = name,
            baseColor   = darkBase,
            accentColor = color,
            glowColor   = glowC,
            glassTint   = glassTint
        )
    }
}

data class WallpaperPalette(
    val dominant: Color,
    val vibrant: Color?,
    val muted: Color?,
    val brightness: Float   // 0.0 (dark) – 1.0 (light)
)
