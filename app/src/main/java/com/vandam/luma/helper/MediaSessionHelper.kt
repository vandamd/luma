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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class MediaInfo(
    val title: String,
    val artist: String,
    val showsPauseButton: Boolean,
    val showsStopButton: Boolean,
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
    private var notificationObserverJob: Job? = null
    @Volatile
    private var trackingEnabled = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        listenerComponent = ComponentName(context, LumaNotificationListener::class.java)
        mediaSessionManager =
            context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    }

    fun setTrackingEnabled(enabled: Boolean) {
        if (trackingEnabled == enabled) return
        trackingEnabled = enabled
        if (enabled) {
            startNotificationObserver()
            refresh()
        } else {
            notificationObserverJob?.cancel()
            notificationObserverJob = null
            clearCallbacks()
            _mediaInfo.value = null
        }
    }

    fun refresh() {
        mainHandler.post { refreshSessions(registerCallbacks = trackingEnabled) }
    }

    fun togglePlayPause() {
        val controller = activeController() ?: return
        val playbackState = controller.playbackState?.state ?: PlaybackState.STATE_NONE
        if (showsPauseButton(playbackState)) {
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

    private fun activeController(
        controllers: List<MediaController> = activeCallbacks.keys.toList(),
    ): MediaController? {
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

    fun getActiveMediaPackageName(forceRefresh: Boolean = false): String? =
        if (forceRefresh || !trackingEnabled || activeCallbacks.isEmpty()) {
            activeController(currentControllers())?.packageName
        } else {
            activeController()?.packageName
        }

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

    private fun showsPauseButton(playbackState: Int): Boolean =
        playbackState == PlaybackState.STATE_PLAYING ||
            playbackState == PlaybackState.STATE_FAST_FORWARDING ||
            playbackState == PlaybackState.STATE_REWINDING ||
            playbackState == PlaybackState.STATE_BUFFERING ||
            playbackState == PlaybackState.STATE_CONNECTING ||
            playbackState == PlaybackState.STATE_SKIPPING_TO_PREVIOUS ||
            playbackState == PlaybackState.STATE_SKIPPING_TO_NEXT ||
            playbackState == PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM

    private fun currentControllers(): List<MediaController> {
        val msm = mediaSessionManager ?: return emptyList()
        val component = listenerComponent ?: return emptyList()
        return try {
            msm.getActiveSessions(component)
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    private fun refreshSessions(registerCallbacks: Boolean) {
        val controllers = currentControllers()
        if (!registerCallbacks) {
            clearCallbacks()
            updateMediaInfo(controllers)
            return
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
        updateMediaInfo(controllers)
    }

    private fun clearCallbacks() {
        activeCallbacks.toMap().forEach { (controller, callback) ->
            runCatching {
                controller.unregisterCallback(callback)
            }
        }
        activeCallbacks.clear()
    }

    private fun startNotificationObserver() {
        if (notificationObserverJob?.isActive == true) return
        if (!trackingEnabled) return
        notificationObserverJob =
            scope.launch {
                LumaNotificationListener.changeVersion.collectLatest {
                    mainHandler.post { refreshSessions(registerCallbacks = trackingEnabled) }
                }
            }
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

    private fun updateMediaInfo(
        controllers: List<MediaController> = activeCallbacks.keys.toList(),
    ) {
        val active = activeController(controllers)
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
        val playbackState = active.playbackState?.state ?: PlaybackState.STATE_NONE
        val showsPauseButton = showsPauseButton(playbackState)
        val showsStopButton = playbackState == PlaybackState.STATE_PAUSED

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
                showsPauseButton = showsPauseButton,
                showsStopButton = showsStopButton,
                isPodcast = isPodcast,
            )
    }
}
