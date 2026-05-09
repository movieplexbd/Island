package com.dynamicisland

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import com.dynamicisland.offline.OfflineCacheManager
import com.dynamicisland.service.DynamicIslandServiceV3
import com.dynamicisland.theme.ThemeCatalog
import com.dynamicisland.utils.PermissionHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val overlayPermLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { }
    private val phonePerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DynamicIslandAppV3() }
    }

    override fun onResume() {
        super.onResume()
        if (PermissionHelper.allPermissionsGranted(this)) startIslandService()
    }

    @Composable
    fun DynamicIslandAppV3() {
        val scope   = rememberCoroutineScope()
        val cache   = remember { OfflineCacheManager(applicationContext) }
        var savedTheme by remember { mutableStateOf("obsidian") }

        LaunchedEffect(Unit) { savedTheme = cache.loadThemeId() ?: "obsidian" }

        MaterialTheme(colorScheme = darkColorScheme()) {
            MainScreenV3(
                onRequestOverlay           = {
                    overlayPermLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                },
                onRequestNotificationAccess = {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                onRequestPhonePermission   = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        phonePerm.launch(android.Manifest.permission.READ_PHONE_STATE)
                },
                onStartService             = ::startIslandService,
                onStopService              = ::stopIslandService,
                onThemeSelected            = { themeId ->
                    scope.launch { cache.saveThemeId(themeId) }
                    DynamicIslandServiceV3.instance?.viewModel
                        ?.setTheme(ThemeCatalog.byId(themeId))
                },
                savedThemeId               = savedTheme,
                hasOverlayPerm             = PermissionHelper.hasOverlayPermission(this),
                hasNotifAccess             = PermissionHelper.hasNotificationAccess(this),
            )
        }
    }

    private fun startIslandService() {
        if (!PermissionHelper.hasOverlayPermission(this)) return
        ContextCompat.startForegroundService(this,
            Intent(this, DynamicIslandServiceV3::class.java))
    }

    private fun stopIslandService() {
        startService(Intent(this, DynamicIslandServiceV3::class.java).apply {
            action = DynamicIslandServiceV3.ACTION_STOP })
    }
}

// ═══════════════════════════ V3 MAIN SCREEN ═══════════════════════════

@Composable
fun MainScreenV3(
    onRequestOverlay: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onRequestPhonePermission: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onThemeSelected: (String) -> Unit,
    savedThemeId: String,
    hasOverlayPerm: Boolean,
    hasNotifAccess: Boolean,
) {
    var isRunning  by remember { mutableStateOf(DynamicIslandServiceV3.instance != null) }
    var activeTab  by remember { mutableIntStateOf(0) }

    val bgGradient = Brush.verticalGradient(listOf(Color(0xFF050510), Color(0xFF0A0A1A), Color(0xFF000000)))

    Box(Modifier.fillMaxSize().background(bgGradient)) {
        Column(Modifier.fillMaxSize()) {
            // Header
            V3Header(isRunning)
            // Tabs
            TabRow(
                selectedTabIndex = activeTab,
                containerColor   = Color.Transparent,
                contentColor     = Color.White,
                indicator        = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        color = Color(0xFF007AFF)
                    )
                }
            ) {
                listOf("Setup", "Themes", "Features").forEachIndexed { i, label ->
                    Tab(selected = activeTab == i, onClick = { activeTab = i },
                        text = { Text(label, fontSize = 13.sp) })
                }
            }

            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                when (activeTab) {
                    0 -> SetupTab(hasOverlayPerm, hasNotifAccess, isRunning,
                        onRequestOverlay, onRequestNotificationAccess, onRequestPhonePermission,
                        onStartService = { onStartService(); isRunning = true },
                        onStopService  = { onStopService();  isRunning = false })
                    1 -> ThemeTab(savedThemeId, onThemeSelected)
                    2 -> FeaturesTab()
                }
            }
        }
    }
}

@Composable
private fun V3Header(isRunning: Boolean) {
    val inf = rememberInfiniteTransition(label = "glow")
    val glowAlpha by inf.animateFloat(0.4f, 1f,
        infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "ga")

    Column(
        Modifier.fillMaxWidth().padding(24.dp, 48.dp, 24.dp, 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(80.dp).background(
                Color(0xFF007AFF).copy(alpha = glowAlpha * 0.3f), CircleShape))
            Box(Modifier.width(120.dp).height(36.dp).background(Color.Black, RoundedCornerShape(18.dp)))
        }
        Spacer(Modifier.height(12.dp))
        Text("Dynamic Island", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("v3.0", color = Color(0xFF007AFF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (isRunning) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).background(Color(0xFF4CAF50).copy(alpha = glowAlpha), CircleShape))
                Text("Running", color = Color(0xFF4CAF50), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SetupTab(
    hasOverlay: Boolean, hasNotif: Boolean, isRunning: Boolean,
    onOverlay: () -> Unit, onNotif: () -> Unit, onPhone: () -> Unit,
    onStartService: () -> Unit, onStopService: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Permissions", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        PermCard("Display over other apps", "Required to show the island overlay",
            Icons.Rounded.Layers, hasOverlay, onOverlay)
        PermCard("Notification access", "Required for notification + music detection",
            Icons.Rounded.Notifications, hasNotif, onNotif)
        PermCard("Phone state (optional)", "For caller ID detection",
            Icons.Rounded.Phone, false, onPhone, optional = true)

        Spacer(Modifier.height(8.dp))
        Text("Service", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (!isRunning) {
            Button(onClick = onStartService,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(16.dp),
                enabled  = hasOverlay && hasNotif,
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
            ) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start Island", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            OutlinedButton(onClick = onStopService,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(16.dp),
                border   = BorderStroke(1.dp, Color(0xFFFF5252))
            ) {
                Icon(Icons.Rounded.Stop, null, Modifier.size(20.dp), tint = Color(0xFFFF5252))
                Spacer(Modifier.width(8.dp))
                Text("Stop Island", fontSize = 16.sp, color = Color(0xFFFF5252))
            }
        }

        Spacer(Modifier.height(8.dp))
        V3InfoCard()
    }
}

@Composable
private fun PermCard(title: String, desc: String, icon: ImageVector,
                     granted: Boolean, onClick: () -> Unit, optional: Boolean = false) {
    Card(
        onClick = if (granted) ({}) else onClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (granted) Color(0xFF0A2A0A) else Color(0xFF1A1A2E))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).background(
                (if (granted) Color(0xFF4CAF50) else Color(0xFF007AFF)).copy(alpha = 0.15f),
                RoundedCornerShape(12.dp)), Alignment.Center) {
                Icon(icon, null, tint = if (granted) Color(0xFF4CAF50) else Color(0xFF007AFF),
                    modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(desc, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
            }
            Icon(if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.ChevronRight, null,
                tint = if (granted) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ThemeTab(activeThemeId: String, onSelect: (String) -> Unit) {
    val themes = ThemeCatalog.all
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Choose Theme", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("12 themes including 6 new V3 exclusives",
            color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        themes.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { theme ->
                    val selected = theme.id == activeThemeId
                    Card(
                        onClick = { onSelect(theme.id) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        border = if (selected) BorderStroke(2.dp, theme.accentColor) else null,
                        colors = CardDefaults.cardColors(containerColor = theme.baseColor)
                    ) {
                        Column(Modifier.padding(14.dp).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(16.dp).background(theme.accentColor, CircleShape))
                                Box(Modifier.size(16.dp).background(theme.glowColor, CircleShape))
                            }
                            Text(theme.name, color = theme.textColor, fontSize = 13.sp,
                                fontWeight = FontWeight.Bold)
                            if (selected) {
                                Text("Active ✓", color = theme.accentColor, fontSize = 10.sp)
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FeaturesTab() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("V3 Features", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        val v3Features = listOf(
            "🌤️" to "Weather Island — offline cached, hourly forecast",
            "🏃" to "Step Counter — live activity ring with heart rate",
            "⏰" to "Alarm Island — snooze / dismiss with shake animation",
            "🎯" to "Focus Mode — session timer with app blocking indicator",
            "⚽" to "Sport Score — live match ticker with pulsing dot",
            "🎙️" to "Voice Assist — waveform visualizer while listening",
            "📥" to "Download Progress — real-time install tracker",
            "🔴" to "Screen Recording — timer + cast target display",
            "😴" to "Sleep Score — morning health summary",
            "📋" to "Clipboard Island — smart category detection",
            "🔀" to "Split-Pill Mode — two activities side-by-side",
            "📡" to "Offline Support — all states cached to DataStore",
            "💡" to "Ambient Light Sensor — auto-adjusts glow intensity",
            "12 🎨" to "Themes — Cyberpunk, Galaxy, Lava, Ocean + more",
            "🔁" to "Auto-Restart — START_STICKY service never dies",
            "⚡" to "Per-app themes — automatic theme based on active app",
        )
        v3Features.forEach { (icon, desc) ->
            Row(Modifier.fillMaxWidth().background(Color(0xFF1A1A2E), RoundedCornerShape(12.dp))
                .padding(14.dp, 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 20.sp)
                Text(desc, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun V3InfoCard() {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Gestures", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            listOf("Single tap" to "Toggle compact ↔ expanded",
                "Long press" to "Full expand / open control center",
                "Swipe up" to "Dismiss island",
                "Swipe down" to "Collapse to compact"
            ).forEach { (g, a) ->
                Row(horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(g, color = Color(0xFF007AFF), fontSize = 12.sp)
                    Text(a, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                }
            }
        }
    }
}
