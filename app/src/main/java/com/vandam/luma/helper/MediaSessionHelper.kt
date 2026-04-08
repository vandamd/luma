package com.vandam.luma.helper

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MediaInfo(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val isPodcast: Boolean,
)

object MediaSessionHelper {
    private val _mediaInfo = MutableStateFlow<MediaInfo?>(null)
    val mediaInfo: StateFlow<MediaInfo?> = _mediaInfo.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaSessionManager: MediaSessionManager? = null
    private var listenerComponent: ComponentName? = null
    private val activeCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private var dismissedPackageName: String? = null
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        listenerComponent = ComponentName(context, LumaNotificationListener::class.java)
        mediaSessionManager =
            context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        scope.launch {
            LumaNotificationListener.changeVersion.collect {
                mainHandler.post { refreshSessions() }
            }
        }
    }

    fun refresh() {
        mainHandler.post { refreshSessions() }
    }

    fun togglePlayPause() {
        val controller = activeController() ?: return
        val state = controller.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    fun skipToNext() {
        activeController()?.transportControls?.skipToNext()
    }

    fun skipToPrevious() {
        activeController()?.transportControls?.skipToPrevious()
    }

    fun rewind() {
        val controller = activeController() ?: return
        val pos = controller.playbackState?.position ?: 0
        controller.transportControls.seekTo((pos - 10_000).coerceAtLeast(0))
    }

    fun fastForward() {
        val controller = activeController() ?: return
        val pos = controller.playbackState?.position ?: 0
        controller.transportControls.seekTo(pos + 10_000)
    }

    fun stopAndDismiss() {
        val controller = activeController() ?: return
        controller.transportControls.stop()
        LumaNotificationListener.dismissMediaNotifications(controller.packageName)
        if (controller.playbackState?.state != PlaybackState.STATE_PLAYING) {
            dismissedPackageName = controller.packageName
        }
        updateMediaInfo()
    }

    private fun activeController(): MediaController? {
        val controllers = activeCallbacks.keys.toList()
        clearDismissedPackageIfInactive(controllers)

        val playingController =
            controllers.firstOrNull { controller ->
                controller.playbackState?.state == PlaybackState.STATE_PLAYING
            }
        if (playingController != null) {
            if (playingController.packageName == dismissedPackageName) {
                dismissedPackageName = null
            }
            return playingController
        }

        return controllers.firstOrNull { controller ->
            controller.isPausedSession() && controller.packageName != dismissedPackageName
        }
    }

    fun getActiveMediaPackageName(): String? = activeController()?.packageName

    private fun clearDismissedPackageIfInactive(controllers: List<MediaController>) {
        val dismissedPackage = dismissedPackageName ?: return
        val stillDismissed =
            controllers.any { controller ->
                controller.packageName == dismissedPackage && controller.isPausedSession()
            }
        if (!stillDismissed) {
            dismissedPackageName = null
        }
    }

    private fun MediaController.isPausedSession(): Boolean {
        val state = playbackState?.state
        val isPaused = state == PlaybackState.STATE_PAUSED || state == PlaybackState.STATE_BUFFERING
        return isPaused && LumaNotificationListener.hasActiveMediaNotification(packageName)
    }

    private fun refreshSessions() {
        val msm = mediaSessionManager ?: return
        val component = listenerComponent ?: return
        val controllers =
            try {
                msm.getActiveSessions(component)
            } catch (_: SecurityException) {
                emptyList()
            }

        val stale = activeCallbacks.keys.toSet()
        for (controller in controllers) {
            if (controller !in activeCallbacks) {
                val callback = createCallback(controller)
                controller.registerCallback(callback, mainHandler)
                activeCallbacks[controller] = callback
            }
        }
        for (controller in stale) {
            if (controller !in controllers) {
                activeCallbacks.remove(controller)?.let { controller.unregisterCallback(it) }
            }
        }
        updateMediaInfo()
    }

    private fun createCallback(controller: MediaController): MediaController.Callback =
        object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                updateMediaInfo()
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                updateMediaInfo()
            }

            override fun onSessionDestroyed() {
                activeCallbacks.remove(controller)?.let { controller.unregisterCallback(it) }
                updateMediaInfo()
            }
        }

    private fun updateMediaInfo() {
        val active = activeController()
        if (active == null) {
            _mediaInfo.value = null
            return
        }

        val metadata =
            active.metadata ?: run {
                _mediaInfo.value = null
                return
            }

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return
        val artist =
            metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: ""
        val isPlaying = active.playbackState?.state == PlaybackState.STATE_PLAYING

        val description = active.metadata?.description
        val mediaUri = description?.mediaUri?.toString().orEmpty()
        val extras = description?.extras
        val isPodcast =
            extras?.let { bundle ->
                val mediaType = bundle.getString("android.mediaType") ?: bundle.getString("media_type")
                mediaType?.contains("podcast", ignoreCase = true) == true
            } == true || mediaUri.contains("podcast", ignoreCase = true)

        _mediaInfo.value =
            MediaInfo(
                title = title.toString(),
                artist = artist.toString(),
                isPlaying = isPlaying,
                isPodcast = isPodcast,
            )
    }
}
