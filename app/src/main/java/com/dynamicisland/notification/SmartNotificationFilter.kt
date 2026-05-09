package com.dynamicisland.notification

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicisland.model.IslandEvent
import com.dynamicisland.ui.IslandDarkGray
import com.dynamicisland.ui.IslandMedGray
import com.dynamicisland.ui.IslandWhite

/**
 * ╔══════════════════════════════════════════════════════╗
 * ║    SMART NOTIFICATION FILTER — v2.0                 ║
 * ╚══════════════════════════════════════════════════════╝
 *
 * ML-style scoring without requiring ML Kit:
 * Uses keyword analysis, sender learning, and time-of-day
 * heuristics to classify notification importance.
 */
class SmartNotificationFilter(private val context: Context) {

    enum class Result { SHOW_HIGH, SHOW_NORMAL, SHOW_LOW, SUPPRESS }

    // Learned important senders (grows over time with user interaction)
    private val importantSenders = mutableSetOf<String>()
    // Packages user has dismissed 3+ times in a row → suppress
    private val repetitivelySuppressed = mutableMapOf<String, Int>()

    private val highValueKeywords = setOf(
        "urgent", "alert", "verify", "code", "otp", "confirm",
        "payment", "order", "ship", "deliver", "security"
    )
    private val lowValueKeywords = setOf(
        "sale", "offer", "discount", "promo", "% off", "deal",
        "newsletter", "unsubscribe", "update available", "new in"
    )
    private val suppressedCategories = setOf(
        "com.google.android.gm",      // Gmail promo tabs (heuristic)
    )

    fun evaluate(event: IslandEvent.NotificationReceived): Result {
        val pkg   = event.packageName.lowercase()
        val title = event.title.lowercase()
        val text  = event.text.lowercase()
        val all   = "$title $text"

        // Hard suppress repeated dismissals
        if ((repetitivelySuppressed[pkg] ?: 0) >= 3) return Result.SUPPRESS

        // Important senders learned from user engagement
        if (pkg in importantSenders) return Result.SHOW_HIGH

        // Keyword scoring
        val highScore = highValueKeywords.count { it in all }
        val lowScore  = lowValueKeywords.count  { it in all }

        return when {
            highScore >= 2                 -> Result.SHOW_HIGH
            highScore >= 1 && lowScore == 0 -> Result.SHOW_NORMAL
            lowScore >= 2                  -> Result.SHOW_LOW
            lowScore >= 1                  -> Result.SUPPRESS
            else                           -> Result.SHOW_NORMAL
        }
    }

    fun markImportant(packageName: String) {
        importantSenders.add(packageName.lowercase())
        repetitivelySuppressed.remove(packageName.lowercase())
    }

    fun markDismissed(packageName: String) {
        val pkg   = packageName.lowercase()
        val count = (repetitivelySuppressed[pkg] ?: 0) + 1
        repetitivelySuppressed[pkg] = count
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// QUICK REPLY POPUP COMPOSABLE
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Floating quick-reply card that expands below the island.
 * Triggered when user long-presses on a messaging notification.
 */
@Composable
fun QuickReplyPopup(
    visible: Boolean,
    appName: String,
    senderName: String,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn() + slideInVertically { -it / 2 } + expandVertically(),
        exit    = fadeOut() + slideOutVertically { -it / 2 } + shrinkVertically()
    ) {
        var replyText by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(IslandDarkGray)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Reply to $senderName",
                    color = IslandWhite.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Rounded.Close, null, tint = IslandWhite.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                }
            }

            // Quick reply chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("👍 OK", "On my way", "Can't talk").forEach { chip ->
                    QuickReplyChip(text = chip, onClick = { onSend(chip) })
                }
            }

            // Text input row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value         = replyText,
                    onValueChange = { replyText = it },
                    placeholder   = { Text("Reply...", color = IslandWhite.copy(alpha = 0.3f), fontSize = 13.sp) },
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedTextColor   = IslandWhite,
                        unfocusedTextColor = IslandWhite,
                        focusedBorderColor = Color(0xFF007AFF).copy(alpha = 0.5f),
                        unfocusedBorderColor = IslandMedGray,
                        cursorColor        = Color(0xFF007AFF)
                    ),
                    modifier      = Modifier.weight(1f),
                    maxLines      = 2,
                    shape         = RoundedCornerShape(12.dp)
                )
                IconButton(
                    onClick  = { if (replyText.isNotBlank()) { onSend(replyText); replyText = "" } },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (replyText.isNotBlank()) Color(0xFF007AFF) else IslandMedGray,
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(Icons.Rounded.Send, null, tint = IslandWhite, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun QuickReplyChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(IslandMedGray)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, color = IslandWhite, fontSize = 11.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CLIPBOARD HISTORY ISLAND
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Maintains a rolling clipboard history and renders a
 * floating picker island when triggered.
 */
class ClipboardHistoryManager {
    private val _history = mutableStateListOf<ClipEntry>()
    val history: List<ClipEntry> get() = _history

    fun addEntry(text: String) {
        if (text.isBlank()) return
        // Deduplicate
        _history.removeAll { it.text == text }
        _history.add(0, ClipEntry(text = text, timestamp = System.currentTimeMillis()))
        // Keep last 10
        while (_history.size > 10) _history.removeLast()
    }

    fun remove(id: Long) { _history.removeAll { it.timestamp == id } }
    fun clear() { _history.clear() }
}

data class ClipEntry(val text: String, val timestamp: Long)

@Composable
fun ClipboardHistoryIsland(
    history: List<ClipEntry>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(IslandDarkGray)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.ContentCopy, null, tint = Color(0xFF007AFF), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Clipboard", color = IslandWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Rounded.Close, null, tint = IslandWhite.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
            }
        }

        history.take(5).forEach { entry ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(IslandMedGray)
                    .clickable { onSelect(entry.text) }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Text(
                    text     = entry.text,
                    color    = IslandWhite.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
