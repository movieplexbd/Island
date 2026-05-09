# 🏝️ Dynamic Island for Android — V3

A premium iOS-style Dynamic Island overlay for Android, rebuilt from the ground up for V3 with **offline-first architecture**, **12 themes**, and **10 new island types**.

---

## ✨ What's New in V3

| Feature | Description |
|---|---|
| 🌤️ Weather Island | Live weather with hourly forecast, offline cache (30 min) |
| 🏃 Step Counter | Real-time activity ring, calorie/distance/heart rate |
| ⏰ Alarm Island | Shake animation, snooze/dismiss buttons |
| 🎯 Focus Mode | Session countdown, DND integration, app-block counter |
| ⚽ Sport Score | Live match ticker with animated LIVE dot |
| 🎙️ Voice Assist | Animated waveform bars while listening |
| 📥 Download | App install progress tracker with speed |
| 🔴 Screen Recording | Duration timer + cast target display |
| 😴 Sleep Score | Morning health summary with quality rating |
| 📋 Clipboard | Smart snippet with auto category detection |
| 🔀 Split-Pill | Two activities side-by-side (music + steps, call + battery) |
| 📡 Offline Mode | All states cached to DataStore, auto-restored on boot |
| 💡 Ambient Sensor | Glow intensity adapts to room brightness |
| 12 🎨 Themes | Cyberpunk, Galaxy, Lava, Ocean, Forest, Titanium + 6 more |
| 🔁 Auto-Restart | `START_STICKY` service never stays dead |
| ⚡ Per-app Themes | Automatic theme based on active app package |

---

## 🎨 Theme Gallery

| Theme | Colors | V3? |
|---|---|---|
| Obsidian | Black + Blue | ✓ (V2) |
| Aurora | Dark Teal + Cyan | ✓ (V2) |
| Sakura | Dark Rose + Pink | ✓ (V2) |
| Solar | Dark Gold + Amber | ✓ (V2) |
| Ghost | White + Black | ✓ (V2) |
| Neon | Near-Black + Green | ✓ (V2) |
| **Cyberpunk** | **Deep Purple + Magenta** | 🆕 V3 |
| **Ocean Deep** | **Navy + Cyan** | 🆕 V3 |
| **Lava** | **Dark Red + OrangeRed** | 🆕 V3 |
| **Galaxy** | **Midnight + Violet** | 🆕 V3 |
| **Forest** | **Deep Green + Lime** | 🆕 V3 |
| **Titanium** | **Dark Grey + Silver** | 🆕 V3 |

---

## 📋 Requirements

- Android **8.0+ (API 26)**
- Android Studio **Iguana (2023.2.1)** or newer
- Kotlin **2.0.x**
- JDK **17**

---

## 🚀 Build & Run

```bash
unzip DynamicIsland_V3.zip
cd DynamicIsland_V3

# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease
```

APK output → `app/build/outputs/apk/`

---

## 📱 First-Time Setup

1. **Display over other apps** — Settings → Special App Access → Display over other apps → Dynamic Island ✓
2. **Notification access** — Settings → Notification Listener → Dynamic Island ✓
3. **Phone state** (optional) — grant at the in-app prompt
4. Tap **Start Island** → pill appears at the top

---

## 🔀 Split-Pill Usage

```kotlin
// Show music + step counter side by side
SplitPillHelper.musicWithSteps(steps = 8432, calories = 320)

// Show call + battery
SplitPillHelper.callWithBattery(percentage = 73)

// Custom split
SplitPillHelper.show(IslandState.Weather(condition = WeatherCondition.SUNNY, tempC = 24f, ...))

// Return to single activity
SplitPillHelper.clear()
```

---

## 📡 Sending Events (V3)

```kotlin
// Weather (auto-cached offline)
DynamicIslandServiceV3.sendEvent(IslandEvent.WeatherUpdate(
    IslandState.Weather(
        condition      = WeatherCondition.PARTLY_CLOUDY,
        tempC          = 21f,
        feelsLikeC     = 19f,
        humidity       = 68,
        cityName       = "Dhaka",
        windKph        = 12f,
        uvIndex        = 5,
        hourlyForecast = listOf(HourlyWeather(14, WeatherCondition.SUNNY, 23f, 10))
    )
))

// Steps (also auto-saved to DataStore)
DynamicIslandServiceV3.sendEvent(IslandEvent.StepUpdate(
    steps = 7350, calories = 294, distanceKm = 5.9f, heartRate = 72))

// Alarm
DynamicIslandServiceV3.sendCriticalEvent(IslandEvent.AlarmFired("Morning", "07:00 AM"))

// Focus session
DynamicIslandServiceV3.sendCriticalEvent(IslandEvent.FocusStarted("Deep Work", 25))

// Sport score
DynamicIslandServiceV3.sendEvent(IslandEvent.SportScoreUpdate(
    IslandState.SportScore("Football", "BAR", "RMA", 2, 1, "LIVE 67'", "La Liga")
))

// Voice assistant
DynamicIslandServiceV3.sendCriticalEvent(IslandEvent.VoiceAssistActivated("Listening…"))

// Download
DynamicIslandServiceV3.sendEvent(IslandEvent.DownloadUpdate("Spotify", 0.72f, 4.8f))

// Screen recording
DynamicIslandServiceV3.sendCriticalEvent(IslandEvent.ScreenRecordingStarted())

// Split pill
DynamicIslandServiceV3.sendCriticalEvent(
    IslandEvent.SplitSecondaryState(IslandState.StepCounter(steps = 6200)))
```

---

## 🏗️ V3 Project Structure

```
app/src/main/java/com/dynamicisland/
├── DynamicIslandApp.kt              # Application class (auto-start)
├── MainActivity.kt                  # V3 setup UI (tabs: Setup/Themes/Features)
├── model/
│   └── IslandState.kt               # 15+ states including all V3 types
├── viewmodel/
│   └── IslandViewModelV3.kt         # Full V3 state machine
├── ui/
│   ├── DynamicIslandOverlayV3.kt    # Master V3 compositor
│   └── islands/
│       ├── V3Islands.kt             # Weather/Steps/Alarm/Focus/Sport/Voice/...
│       ├── CallIsland.kt
│       ├── MusicIsland.kt
│       └── OtherIslands.kt
├── service/
│   ├── DynamicIslandServiceV3.kt    # START_STICKY foreground service
│   ├── IslandNotificationListener.kt (V3: alarm + reply detection)
│   ├── IslandMediaSessionManager.kt
│   └── PhoneStateReceiver.kt
├── offline/
│   └── OfflineCacheManager.kt       # DataStore cache for all states
├── sensor/
│   └── SensorEngine.kt              # Step counter, light, heart rate
├── receiver/
│   ├── Receivers.kt                 # Boot + Charging (updated for V3)
│   └── AlarmReceiver.kt             # NEW: alarm broadcast handler
├── theme/
│   └── ThemeEngine.kt               # 12 themes + wallpaper extraction
├── v3/
│   ├── WeatherWorker.kt             # WorkManager periodic weather refresh
│   ├── FocusSessionManager.kt       # DND integration
│   ├── ClipboardMonitor.kt          # Clipboard change listener
│   └── SplitPillHelper.kt           # Split-pill convenience API
├── ai/
│   └── AIIslandBrain.kt             # Smart priority + suppression (V2)
├── animation/
│   └── LiquidMorphAnimator.kt       # Physics-based blob morphing (V2)
├── rendering/
│   ├── BlurRenderEngine.kt
│   ├── GlowEffects.kt
│   └── ParticleSystem.kt
├── stack/
│   ├── IslandStackManager.kt
│   └── StackedIslandView.kt
├── gesture/
│   └── GestureEngine.kt
├── haptics/
│   └── HapticEngine.kt
├── customization/
│   └── CustomizationEngine.kt
├── layout/
│   └── FoldableSupport.kt
├── notification/
│   └── SmartNotificationFilter.kt
├── plugin/
│   └── PluginManager.kt
└── utils/
    └── PermissionHelper.kt          # V3: activity recognition + sensors
```

---

## 🎮 Gestures

| Gesture | Action |
|---|---|
| Single tap | Toggle compact ↔ expanded |
| Long press | Fully expand / open control center |
| Swipe up | Dismiss island |
| Swipe down | Collapse to compact |

---

## 📡 Offline Behaviour

| Data | Cache duration | Restored on boot? |
|---|---|---|
| Weather | 30 min (stale badge after) | ✓ |
| Step count | Resets daily | ✓ |
| Focus session | Until session ends | ✓ |
| Screen recording | Until explicitly stopped | ✓ |
| Theme choice | Forever | ✓ |
| Island shape | Forever | ✓ |

---

## ⚡ Performance

- Overlay uses `FLAG_NOT_FOCUSABLE` — never blocks touch to other apps
- Step counter via Android `TYPE_STEP_COUNTER` — no battery polling
- Weather cached in DataStore — no redundant network calls
- `START_STICKY` service auto-restarts after system kills
- All sensor subscriptions use `callbackFlow` — clean lifecycle handling

---

## 🔒 Privacy

- Zero data transmitted to any server
- All caches stored locally in DataStore
- Contact lookup is local only
- Step data never leaves the device
- No internet permission required (weather API is plug-in, opt-in)

---

## 📄 License

MIT — free to use, modify, and distribute.

---

## 🐙 GitHub Repository Details

This project is hosted on GitHub. You can find the repository at: [https://github.com/movieplexbd/Island](https://github.com/movieplexbd/Island)

### Cloning the Repository

To get a local copy up and running, follow these simple steps:

```bash
git clone https://github.com/movieplexbd/Island.git
cd Island
```

### Contributing

Contributions are what make the open-source community such an amazing place to learn, inspire, and create. Any contributions you make are **greatly appreciated**.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request
