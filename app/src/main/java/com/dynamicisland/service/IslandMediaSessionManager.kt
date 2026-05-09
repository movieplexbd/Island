package com.dynamicisland.service

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.dynamicisland.model.IslandEvent
import kotlinx.coroutines.*

/**
 * Monitors active [MediaController] sessions and pushes
 * NowPlaying updates into the Dynamic Island.
 *
 * Requires notification listener permission to retrieve sessions.
 */
class IslandMediaSessionManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pollingJob: Job? = null

    // Track currently observed controller to avoid duplicates
    private var activePackage: String? = null

    private val sessionManager by lazy {
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    private val activeControllerListener = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            state ?: return
            updateFromController(currentController)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateFromController(currentController)
        }
    }

    private var currentController: MediaController? = null

    fun start() {
        pollingJob = scope.launch {
            while (isActive) {
                refreshSessions()
                delay(2000L) // Poll every 2 seconds
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        currentController?.unregisterCallback(activeControllerListener)
        scope.cancel()
    }

    private fun refreshSessions() {
        try {
            val componentName = android.content.ComponentName(
                context, IslandNotificationListener::class.java
            )
            val controllers = sessionManager.getActiveSessions(componentName)

            if (controllers.isEmpty()) {
                if (currentController != null) {
                    currentController = null
                    DynamicIslandServiceV3.sendEvent(IslandEvent.MediaStopped)
                }
                return
            }

            // Take the most active controller
            val topController = controllers.firstOrNull { controller ->
                controller.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: controllers.first()

            if (topController.packageName != activePackage) {
                currentController?.unregisterCallback(activeControllerListener)
                topController.registerCallback(activeControllerListener)
                currentController = topController
                activePackage = topController.packageName
            }

            updateFromController(topController)

        } catch (e: SecurityException) {
            // Notification listener permission not granted yet
        }
    }

    private fun updateFromController(controller: MediaController?) {
        controller ?: return

        val state    = controller.playbackState ?: return
        val metadata = controller.metadata ?: return

        val isPlaying = state.state == PlaybackState.STATE_PLAYING

        val title  = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: "Unknown Artist"

        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val position = state.position
        val progress = if (duration > 0) (position.toFloat() / duration.toFloat()) else 0f

        val albumArt: Bitmap? = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)

        DynamicIslandServiceV3.sendEvent(
            IslandEvent.MediaUpdate(
                title     = title,
                artist    = artist,
                isPlaying = isPlaying,
                progress  = progress.coerceIn(0f, 1f),
                albumArt  = albumArt
            )
        )

        if (!isPlaying) {
            // Give user a moment to see "paused" before dismissing
            scope.launch {
                delay(3000)
                DynamicIslandServiceV3.sendEvent(IslandEvent.MediaStopped)
            }
        }
    }

    /** Send play/pause command to the active media session */
    fun playPause() {
        val transport = currentController?.transportControls
        val state = currentController?.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING) transport?.pause()
        else transport?.play()
    }

    fun skipNext()     { currentController?.transportControls?.skipToNext() }
    fun skipPrevious() { currentController?.transportControls?.skipToPrevious() }
}
