package com.dynamicisland.v3

import android.content.ClipboardManager
import android.content.Context
import com.dynamicisland.model.IslandEvent
import com.dynamicisland.service.DynamicIslandServiceV3

/**
 * V3: Monitors clipboard changes and shows ClipboardSnippet island.
 * Only activates when the Dynamic Island service is running.
 */
class ClipboardMonitor(private val context: Context) {

    private val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var lastText = ""

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        val clip = cm.primaryClip ?: return@OnPrimaryClipChangedListener
        val text = clip.getItemAt(0)?.text?.toString() ?: return@OnPrimaryClipChangedListener
        if (text.isBlank() || text == lastText) return@OnPrimaryClipChangedListener
        if (text.length > 200) return@OnPrimaryClipChangedListener  // skip huge pastes
        lastText = text
        val sourceApp = runCatching {
            clip.description?.label?.toString() ?: "Unknown"
        }.getOrDefault("Unknown")
        DynamicIslandServiceV3.sendEvent(IslandEvent.ClipboardChanged(text, sourceApp))
    }

    fun start() { cm.addPrimaryClipChangedListener(listener) }
    fun stop()  { cm.removePrimaryClipChangedListener(listener) }
}
