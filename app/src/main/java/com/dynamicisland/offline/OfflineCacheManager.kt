package com.dynamicisland.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.dynamicisland.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.json.JSONArray

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║        OFFLINE CACHE MANAGER — v3.0                 ║
 * ║  Persists island states so the app works offline    ║
 * ╚══════════════════════════════════════════════════════╝
 *
 * Strategy:
 *  • All serializable states are written to DataStore on change
 *  • On boot / no-network, last known states are restored
 *  • Weather data caches for 30 min before staleness warning
 *  • Step counts persist across reboots via DataStore
 *  • User preferences (theme, shape, haptics) always survive
 */

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "island_v3_cache")

class OfflineCacheManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Keys ─────────────────────────────────────────────────────────────────
    companion object {
        // Weather
        private val KEY_WEATHER_JSON      = stringPreferencesKey("weather_json")
        private val KEY_WEATHER_TIMESTAMP = longPreferencesKey("weather_ts")
        private val WEATHER_STALE_MS      = 30 * 60 * 1_000L   // 30 min

        // Steps (resets daily)
        private val KEY_STEPS             = intPreferencesKey("steps_today")
        private val KEY_STEPS_CALORIES    = intPreferencesKey("steps_calories")
        private val KEY_STEPS_DISTANCE    = floatPreferencesKey("steps_distance")
        private val KEY_STEPS_DATE        = stringPreferencesKey("steps_date")

        // User preferences
        private val KEY_THEME_ID          = stringPreferencesKey("theme_id")
        private val KEY_ISLAND_SHAPE      = stringPreferencesKey("island_shape")
        private val KEY_HAPTICS_ENABLED   = booleanPreferencesKey("haptics_enabled")
        private val KEY_PARTICLES_ENABLED = booleanPreferencesKey("particles_enabled")
        private val KEY_GLOW_ENABLED      = booleanPreferencesKey("glow_enabled")
        private val KEY_STEP_GOAL         = intPreferencesKey("step_goal")
        private val KEY_CITY_NAME         = stringPreferencesKey("city_name")
        private val KEY_CELSIUS           = booleanPreferencesKey("use_celsius")

        // Sport scores
        private val KEY_SPORT_JSON        = stringPreferencesKey("sport_scores_json")

        // Focus sessions
        private val KEY_FOCUS_NAME        = stringPreferencesKey("focus_name")
        private val KEY_FOCUS_END_EPOCH   = longPreferencesKey("focus_end_epoch")
        private val KEY_FOCUS_TOTAL_MIN   = intPreferencesKey("focus_total_min")

        // Screen recording
        private val KEY_REC_ACTIVE        = booleanPreferencesKey("recording_active")
        private val KEY_REC_START_EPOCH   = longPreferencesKey("recording_start")
    }

    // ════════════════════════ NETWORK ════════════════════════

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    val networkState: Flow<Boolean> = flow {
        while (true) {
            emit(isOnline())
            delay(10_000)
        }
    }.distinctUntilChanged()

    // ════════════════════════ WEATHER ════════════════════════

    suspend fun cacheWeather(weather: IslandState.Weather) {
        val json = JSONObject().apply {
            put("condition", weather.condition.name)
            put("tempC", weather.tempC)
            put("feelsLikeC", weather.feelsLikeC)
            put("humidity", weather.humidity)
            put("cityName", weather.cityName)
            put("windKph", weather.windKph)
            put("uvIndex", weather.uvIndex)
            val hourly = JSONArray()
            weather.hourlyForecast.forEach { h ->
                hourly.put(JSONObject().apply {
                    put("hour", h.hour)
                    put("condition", h.condition.name)
                    put("tempC", h.tempC)
                    put("precipChance", h.precipChance)
                })
            }
            put("hourly", hourly)
        }
        context.dataStore.edit { prefs ->
            prefs[KEY_WEATHER_JSON]      = json.toString()
            prefs[KEY_WEATHER_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun loadCachedWeather(): IslandState.Weather? {
        val prefs = context.dataStore.data.first()
        val json  = prefs[KEY_WEATHER_JSON] ?: return null
        val ts    = prefs[KEY_WEATHER_TIMESTAMP] ?: 0L
        return try {
            val obj = JSONObject(json)
            val hourlyArr = obj.optJSONArray("hourly") ?: JSONArray()
            val hourly = (0 until hourlyArr.length()).map { i ->
                val h = hourlyArr.getJSONObject(i)
                HourlyWeather(
                    hour         = h.getInt("hour"),
                    condition    = WeatherCondition.valueOf(h.getString("condition")),
                    tempC        = h.getDouble("tempC").toFloat(),
                    precipChance = h.getInt("precipChance")
                )
            }
            IslandState.Weather(
                condition       = WeatherCondition.valueOf(obj.getString("condition")),
                tempC           = obj.getDouble("tempC").toFloat(),
                feelsLikeC      = obj.getDouble("feelsLikeC").toFloat(),
                humidity        = obj.getInt("humidity"),
                cityName        = obj.getString("cityName"),
                windKph         = obj.getDouble("windKph").toFloat(),
                uvIndex         = obj.getInt("uvIndex"),
                hourlyForecast  = hourly,
                isCached        = (System.currentTimeMillis() - ts) > WEATHER_STALE_MS
            )
        } catch (e: Exception) { null }
    }

    fun isWeatherStale(): Flow<Boolean> = context.dataStore.data
        .map { prefs ->
            val ts = prefs[KEY_WEATHER_TIMESTAMP] ?: 0L
            (System.currentTimeMillis() - ts) > WEATHER_STALE_MS
        }

    // ════════════════════════ STEPS ════════════════════════

    suspend fun saveSteps(steps: Int, calories: Int, distanceKm: Float) {
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            .format(java.util.Date())
        context.dataStore.edit { prefs ->
            prefs[KEY_STEPS]          = steps
            prefs[KEY_STEPS_CALORIES] = calories
            prefs[KEY_STEPS_DISTANCE] = distanceKm
            prefs[KEY_STEPS_DATE]     = today
        }
    }

    suspend fun loadSteps(): Triple<Int, Int, Float> {
        val prefs = context.dataStore.data.first()
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            .format(java.util.Date())
        val savedDate = prefs[KEY_STEPS_DATE] ?: ""
        // Reset if it's a new day
        if (savedDate != today) return Triple(0, 0, 0f)
        return Triple(
            prefs[KEY_STEPS]          ?: 0,
            prefs[KEY_STEPS_CALORIES] ?: 0,
            prefs[KEY_STEPS_DISTANCE] ?: 0f
        )
    }

    // ════════════════════════ FOCUS ════════════════════════

    suspend fun saveFocusSession(name: String, durationMinutes: Int) {
        val endEpoch = System.currentTimeMillis() + durationMinutes * 60_000L
        context.dataStore.edit { prefs ->
            prefs[KEY_FOCUS_NAME]      = name
            prefs[KEY_FOCUS_END_EPOCH] = endEpoch
            prefs[KEY_FOCUS_TOTAL_MIN] = durationMinutes
        }
    }

    suspend fun loadActiveFocus(): IslandState.FocusMode? {
        val prefs    = context.dataStore.data.first()
        val name     = prefs[KEY_FOCUS_NAME]      ?: return null
        val endEpoch = prefs[KEY_FOCUS_END_EPOCH] ?: return null
        val total    = prefs[KEY_FOCUS_TOTAL_MIN] ?: return null
        val remaining = ((endEpoch - System.currentTimeMillis()) / 60_000L).toInt()
        if (remaining <= 0) return null
        return IslandState.FocusMode(name, remaining, total)
    }

    suspend fun clearFocusSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_FOCUS_NAME)
            prefs.remove(KEY_FOCUS_END_EPOCH)
        }
    }

    // ════════════════════════ RECORDING ════════════════════════

    suspend fun saveRecordingState(isActive: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REC_ACTIVE]      = isActive
            if (isActive) prefs[KEY_REC_START_EPOCH] = System.currentTimeMillis()
            else          prefs.remove(KEY_REC_START_EPOCH)
        }
    }

    suspend fun loadRecordingState(): IslandState.ScreenRecording? {
        val prefs  = context.dataStore.data.first()
        val active = prefs[KEY_REC_ACTIVE] ?: false
        if (!active) return null
        val start  = prefs[KEY_REC_START_EPOCH] ?: System.currentTimeMillis()
        val dur    = (System.currentTimeMillis() - start) / 1_000L
        return IslandState.ScreenRecording(isRecording = true, durationSeconds = dur)
    }

    // ════════════════════════ PREFERENCES ════════════════════════

    suspend fun saveThemeId(id: String) {
        context.dataStore.edit { it[KEY_THEME_ID] = id }
    }
    suspend fun loadThemeId(): String? =
        context.dataStore.data.first()[KEY_THEME_ID]

    suspend fun saveIslandShape(shape: String) {
        context.dataStore.edit { it[KEY_ISLAND_SHAPE] = shape }
    }
    suspend fun loadIslandShape(): String =
        context.dataStore.data.first()[KEY_ISLAND_SHAPE] ?: "PILL"

    suspend fun saveHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HAPTICS_ENABLED] = enabled }
    }
    suspend fun loadHapticsEnabled(): Boolean =
        context.dataStore.data.first()[KEY_HAPTICS_ENABLED] ?: true

    suspend fun saveParticlesEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PARTICLES_ENABLED] = enabled }
    }
    suspend fun loadParticlesEnabled(): Boolean =
        context.dataStore.data.first()[KEY_PARTICLES_ENABLED] ?: true

    suspend fun saveGlowEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_GLOW_ENABLED] = enabled }
    }
    suspend fun loadGlowEnabled(): Boolean =
        context.dataStore.data.first()[KEY_GLOW_ENABLED] ?: true

    suspend fun saveStepGoal(goal: Int) {
        context.dataStore.edit { it[KEY_STEP_GOAL] = goal }
    }
    suspend fun loadStepGoal(): Int =
        context.dataStore.data.first()[KEY_STEP_GOAL] ?: 10_000

    suspend fun saveCityName(city: String) {
        context.dataStore.edit { it[KEY_CITY_NAME] = city }
    }
    suspend fun loadCityName(): String =
        context.dataStore.data.first()[KEY_CITY_NAME] ?: ""

    suspend fun saveUseCelsius(celsius: Boolean) {
        context.dataStore.edit { it[KEY_CELSIUS] = celsius }
    }
    suspend fun loadUseCelsius(): Boolean =
        context.dataStore.data.first()[KEY_CELSIUS] ?: true

    // ════════════════════════ RESTORE ALL ════════════════════════

    /**
     * Restores the most relevant cached state on app start.
     * Priority: Focus > Recording > Weather > Steps
     */
    suspend fun restoreLastKnownState(): IslandState? {
        return loadActiveFocus()
            ?: loadRecordingState()
            ?: loadCachedWeather()
    }

    fun cleanup() { scope.cancel() }
}
