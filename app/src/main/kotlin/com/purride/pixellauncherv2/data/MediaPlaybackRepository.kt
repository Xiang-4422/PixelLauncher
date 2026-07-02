package com.purride.pixellauncherv2.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.Rating
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.purride.pixellauncherv2.launcher.MediaPlaybackSnapshot
import java.util.Locale

@Suppress("DEPRECATION")
class MediaPlaybackRepository(
    private val context: Context,
    private val notificationListener: ComponentName,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) {
    private val sessionManager: MediaSessionManager? =
        context.getSystemService(MediaSessionManager::class.java)

    private var listener: ((MediaPlaybackSnapshot) -> Unit)? = null
    private var currentController: MediaController? = null

    private val activeSessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener {
            selectActiveController(it.orEmpty())
            publishCurrent()
        }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publishCurrent()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            publishCurrent()
        }

        override fun onSessionDestroyed() {
            selectActiveController(activeControllers())
            publishCurrent()
        }

        override fun onSessionEvent(event: String, extras: Bundle?) {
            publishCurrent()
        }
    }

    fun start(onPlaybackChanged: (MediaPlaybackSnapshot) -> Unit) {
        stop()
        listener = onPlaybackChanged
        runCatching {
            sessionManager?.addOnActiveSessionsChangedListener(
                activeSessionsChangedListener,
                notificationListener,
                mainHandler,
            )
        }
        selectActiveController(activeControllers())
        onPlaybackChanged(current())
    }

    fun stop() {
        runCatching {
            sessionManager?.removeOnActiveSessionsChangedListener(activeSessionsChangedListener)
        }
        currentController?.unregisterCallback(controllerCallback)
        currentController = null
        listener = null
    }

    fun current(): MediaPlaybackSnapshot {
        if (currentController == null) {
            selectActiveController(activeControllers())
        }
        return snapshotFor(currentController)
    }

    fun openPlayer(): Boolean {
        val controller = currentController ?: return false
        controller.sessionActivity?.let { pendingIntent ->
            val sent = runCatching {
                pendingIntent.send()
                true
            }.getOrDefault(false)
            if (sent) return true
        }
        val intent = context.packageManager.getLaunchIntentForPackage(controller.packageName) ?: return false
        return runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    fun togglePlayPause() {
        val controller = currentController ?: return
        val controls = controller.transportControls
        val playbackState = controller.playbackState
        when {
            playbackState?.state.isPlayingState() -> controls.pause()
            supports(playbackState, PlaybackState.ACTION_PLAY_PAUSE) -> controls.play()
            supports(playbackState, PlaybackState.ACTION_PLAY) -> controls.play()
            else -> controls.play()
        }
        publishCurrent()
    }

    fun skipPrevious() {
        currentController?.transportControls?.skipToPrevious()
        publishCurrent()
    }

    fun skipNext() {
        currentController?.transportControls?.skipToNext()
        publishCurrent()
    }

    fun seekToProgress(progress: Float) {
        val controller = currentController ?: return
        val durationMillis = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        if (durationMillis <= 0L || !supports(controller.playbackState, PlaybackState.ACTION_SEEK_TO)) {
            return
        }
        controller.transportControls.seekTo((durationMillis * progress.coerceIn(0f, 1f)).toLong())
        publishCurrent()
    }

    fun toggleFavorite() {
        val controller = currentController ?: return
        val playbackState = controller.playbackState
        val ratingType = controller.ratingType
        val currentRating = controller.metadata?.getRating(MediaMetadata.METADATA_KEY_USER_RATING)
        val controls = controller.transportControls
        if (supports(playbackState, PlaybackState.ACTION_SET_RATING)) {
            when (ratingType) {
                Rating.RATING_HEART -> {
                    controls.setRating(Rating.newHeartRating(currentRating?.hasHeart() != true))
                    publishCurrent()
                    return
                }

                Rating.RATING_THUMB_UP_DOWN -> {
                    controls.setRating(Rating.newThumbRating(currentRating?.isThumbUp() != true))
                    publishCurrent()
                    return
                }
            }
        }
        favoriteCustomAction(playbackState)?.let { action ->
            controls.sendCustomAction(action.action, Bundle.EMPTY)
            publishCurrent()
        }
    }

    private fun activeControllers(): List<MediaController> =
        runCatching { sessionManager?.getActiveSessions(notificationListener).orEmpty() }
            .getOrDefault(emptyList())

    private fun selectActiveController(controllers: List<MediaController>) {
        val selected = controllers
            .filterNot { controller -> controller.packageName == context.packageName }
            .firstOrNull { controller -> controller.playbackState?.state.isPlayingState() }
            ?: controllers
                .filterNot { controller -> controller.packageName == context.packageName }
                .firstOrNull { controller -> controller.metadata != null || controller.playbackState != null }
        if (selected?.sessionToken == currentController?.sessionToken) {
            return
        }
        currentController?.unregisterCallback(controllerCallback)
        currentController = selected
        selected?.registerCallback(controllerCallback, mainHandler)
    }

    private fun publishCurrent() {
        val snapshot = current()
        val target = listener ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            target(snapshot)
        } else {
            mainHandler.post { target(snapshot) }
        }
    }

    private fun snapshotFor(controller: MediaController?): MediaPlaybackSnapshot {
        if (controller == null) return MediaPlaybackSnapshot()
        val metadata = controller.metadata
        val playbackState = controller.playbackState
        val title = metadata.title()
        val artist = metadata.artist()
        val duration = (metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).coerceAtLeast(0L)
        val rating = metadata?.getRating(MediaMetadata.METADATA_KEY_USER_RATING)
        val ratingType = controller.ratingType
        val isFavorite = when (ratingType) {
            Rating.RATING_HEART -> rating?.hasHeart() == true
            Rating.RATING_THUMB_UP_DOWN -> rating?.isThumbUp() == true
            else -> false
        }
        val canSetRating = supports(playbackState, PlaybackState.ACTION_SET_RATING) &&
            (ratingType == Rating.RATING_HEART || ratingType == Rating.RATING_THUMB_UP_DOWN)
        return MediaPlaybackSnapshot(
            isActive = metadata != null || playbackState != null,
            packageName = controller.packageName.orEmpty(),
            title = title,
            artist = artist,
            isPlaying = playbackState?.state.isPlayingState(),
            canPlayPause = supportsAny(
                playbackState,
                PlaybackState.ACTION_PLAY,
                PlaybackState.ACTION_PAUSE,
                PlaybackState.ACTION_PLAY_PAUSE,
            ),
            canSkipPrevious = supports(playbackState, PlaybackState.ACTION_SKIP_TO_PREVIOUS),
            canSkipNext = supports(playbackState, PlaybackState.ACTION_SKIP_TO_NEXT),
            canSeek = duration > 0L && supports(playbackState, PlaybackState.ACTION_SEEK_TO),
            positionMillis = (playbackState?.position ?: 0L).coerceAtLeast(0L),
            durationMillis = duration,
            positionUpdatedRealtimeMillis = playbackState?.lastPositionUpdateTime ?: 0L,
            playbackSpeed = playbackState?.playbackSpeed ?: 1f,
            canToggleFavorite = canSetRating || favoriteCustomAction(playbackState) != null,
            isFavorite = isFavorite,
        )
    }

    private fun MediaMetadata?.title(): String {
        if (this == null) return ""
        return getString(MediaMetadata.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty()
    }

    private fun MediaMetadata?.artist(): String {
        if (this == null) return ""
        return getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?.takeIf { it.isNotBlank() }
            ?: getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty()
    }

    private fun supports(state: PlaybackState?, action: Long): Boolean =
        state != null && state.actions and action != 0L

    private fun supportsAny(state: PlaybackState?, vararg actions: Long): Boolean =
        actions.any { action -> supports(state, action) }

    private fun Int?.isPlayingState(): Boolean =
        this == PlaybackState.STATE_PLAYING ||
            this == PlaybackState.STATE_FAST_FORWARDING ||
            this == PlaybackState.STATE_REWINDING

    private fun favoriteCustomAction(state: PlaybackState?): PlaybackState.CustomAction? =
        state?.customActions.orEmpty().firstOrNull { action ->
            val normalized = "${action.action} ${action.name}"
                .lowercase(Locale.US)
            normalized.contains("like") ||
                normalized.contains("favorite") ||
                normalized.contains("favourite") ||
                normalized.contains("heart") ||
                normalized.contains("收藏") ||
                normalized.contains("喜欢")
        }
}
