package com.dynamicisland.model

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║       ISLAND STATE — v3.0                           ║
 * ║  All possible states the Dynamic Island can render  ║
 * ╚══════════════════════════════════════════════════════╝
 *
 * V3 additions:
 *  • Weather      — real-time weather with offline cache
 *  • StepCounter  — fitness activity ring
 *  • Alarm        — alarm snooze/dismiss overlay
 *  • FocusMode    — DND / focus session indicator
 *  • Clipboard    — smart clipboard snippet
 *  • SleepScore   — morning sleep summary
 *  • SportScore   — live match ticker
 *  • VoiceAssist  — voice command listening state
 *  • DownloadProgress — app install tracker
 *  • ScreenRecording  — record/cast indicator
 */
sealed class IslandState {

    object Idle : IslandState()

    data class PhoneCall(
        val callerName: String,
        val callerNumber: String,
        val isIncoming: Boolean = true,
        val duration: Long = 0L,
        val callerAvatarUrl: String? = null
    ) : IslandState()

    data class NowPlaying(
        val title: String,
        val artist: String,
        val albumArt: android.graphics.Bitmap? = null,
        val isPlaying: Boolean = true,
        val progress: Float = 0f,
        val duration: Long = 0L,
        val lyrics: String? = null,
        val nextTitle: String? = null
    ) : IslandState()

    data class Notification(
        val appName: String,
        val title: String,
        val text: String,
        val icon: android.graphics.drawable.Icon? = null,
        val packageName: String = "",
        val replyable: Boolean = false
    ) : IslandState()

    data class Charging(
        val percentage: Int,
        val isCharging: Boolean,
        val isWireless: Boolean = false,
        val estimatedMinutes: Int = 0,
        val temperature: Float = 0f
    ) : IslandState()

    data class Navigation(
        val instruction: String,
        val distance: String,
        val eta: String,
        val maneuverType: ManeuverType = ManeuverType.STRAIGHT,
        val laneGuide: String? = null
    ) : IslandState()

    data class Timer(
        val label: String,
        val remainingSeconds: Long,
        val totalSeconds: Long,
        val isRunning: Boolean = true
    ) : IslandState()

    data class LiveActivity(
        val title: String,
        val subtitle: String,
        val emoji: String = "●",
        val progress: Float? = null
    ) : IslandState()

    // ══════════════════════ V3 STATES ════════════════════════

    data class Weather(
        val condition: WeatherCondition,
        val tempC: Float,
        val feelsLikeC: Float,
        val humidity: Int,
        val cityName: String,
        val windKph: Float = 0f,
        val uvIndex: Int = 0,
        val hourlyForecast: List<HourlyWeather> = emptyList(),
        val isCached: Boolean = false
    ) : IslandState()

    data class StepCounter(
        val steps: Int,
        val goal: Int = 10_000,
        val calories: Int = 0,
        val distanceKm: Float = 0f,
        val activeMinutes: Int = 0,
        val heartRate: Int? = null
    ) : IslandState()

    data class Alarm(
        val label: String,
        val timeString: String,
        val snoozable: Boolean = true,
        val snoozeMinutes: Int = 5
    ) : IslandState()

    data class FocusMode(
        val sessionName: String,
        val remainingMinutes: Int,
        val totalMinutes: Int,
        val appsBlocked: Int = 0
    ) : IslandState()

    data class ClipboardSnippet(
        val text: String,
        val sourceApp: String,
        val category: ClipCategory = ClipCategory.TEXT
    ) : IslandState()

    data class SleepScore(
        val score: Int,
        val hoursSlept: Float,
        val deepSleepPercent: Int,
        val recommendation: String
    ) : IslandState()

    data class SportScore(
        val sport: String,
        val homeTeam: String,
        val awayTeam: String,
        val homeScore: Int,
        val awayScore: Int,
        val status: String,
        val league: String = ""
    ) : IslandState()

    data class VoiceAssist(
        val promptText: String = "Listening…",
        val isProcessing: Boolean = false,
        val resultText: String? = null
    ) : IslandState()

    data class DownloadProgress(
        val appName: String,
        val progress: Float,
        val speedMbps: Float = 0f,
        val isInstalling: Boolean = false
    ) : IslandState()

    data class ScreenRecording(
        val isRecording: Boolean,
        val durationSeconds: Long = 0L,
        val isCasting: Boolean = false,
        val castTarget: String = ""
    ) : IslandState()
}

// ════════════════════════ ENUMS & DATA ════════════════════════

enum class IslandExpansion {
    COLLAPSED, COMPACT, EXPANDED,
    SPLIT   // V3: Two activities side-by-side
}

enum class WeatherCondition(val emoji: String, val label: String) {
    SUNNY("☀️", "Sunny"),
    PARTLY_CLOUDY("⛅", "Partly Cloudy"),
    CLOUDY("☁️", "Cloudy"),
    RAINY("🌧️", "Rainy"),
    STORMY("⛈️", "Stormy"),
    SNOWY("❄️", "Snowy"),
    FOGGY("🌫️", "Foggy"),
    WINDY("💨", "Windy"),
    HAIL("🌨️", "Hail"),
    CLEAR_NIGHT("🌙", "Clear Night"),
    PARTLY_CLOUDY_NIGHT("🌤️", "Partly Cloudy")
}

enum class ManeuverType {
    STRAIGHT, TURN_LEFT, TURN_RIGHT, U_TURN,
    ROUNDABOUT, MERGE, EXIT, DESTINATION
}

enum class ClipCategory {
    TEXT, URL, PHONE, EMAIL, CODE, ADDRESS
}

data class HourlyWeather(
    val hour: Int,
    val condition: WeatherCondition,
    val tempC: Float,
    val precipChance: Int
)

// ════════════════════════ EVENTS ════════════════════════

sealed class IslandEvent {
    // V1/V2
    data class ShowCall(val name: String, val number: String, val isIncoming: Boolean) : IslandEvent()
    data class CallEnded(val duration: Long) : IslandEvent()
    data class MediaUpdate(val title: String, val artist: String, val isPlaying: Boolean,
                           val progress: Float, val albumArt: android.graphics.Bitmap?) : IslandEvent()
    object MediaStopped : IslandEvent()
    data class NotificationReceived(val appName: String, val title: String, val text: String,
                                    val packageName: String, val replyable: Boolean = false) : IslandEvent()
    data class ChargingUpdate(val percentage: Int, val isCharging: Boolean,
                              val isWireless: Boolean = false, val estimatedMinutes: Int = 0) : IslandEvent()
    object Dismiss : IslandEvent()

    // V3
    data class WeatherUpdate(val state: IslandState.Weather) : IslandEvent()
    data class StepUpdate(val steps: Int, val calories: Int, val distanceKm: Float,
                          val heartRate: Int? = null) : IslandEvent()
    data class AlarmFired(val label: String, val timeString: String) : IslandEvent()
    data class AlarmSnoozed(val snoozeMinutes: Int) : IslandEvent()
    object AlarmDismissed : IslandEvent()
    data class FocusStarted(val name: String, val minutes: Int) : IslandEvent()
    object FocusEnded : IslandEvent()
    data class ClipboardChanged(val text: String, val sourceApp: String) : IslandEvent()
    data class SportScoreUpdate(val state: IslandState.SportScore) : IslandEvent()
    data class VoiceAssistActivated(val prompt: String = "Listening…") : IslandEvent()
    object VoiceAssistDismissed : IslandEvent()
    data class DownloadUpdate(val appName: String, val progress: Float, val speedMbps: Float = 0f) : IslandEvent()
    object DownloadComplete : IslandEvent()
    data class ScreenRecordingStarted(val isCasting: Boolean = false, val target: String = "") : IslandEvent()
    object ScreenRecordingStopped : IslandEvent()
    data class SleepSummaryReceived(val state: IslandState.SleepScore) : IslandEvent()
    data class LyricsUpdate(val lyricLine: String) : IslandEvent()
    data class SplitSecondaryState(val state: IslandState) : IslandEvent()
    object ClearSplit : IslandEvent()
}
