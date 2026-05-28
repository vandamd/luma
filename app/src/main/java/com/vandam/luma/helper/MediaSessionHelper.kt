package com.vandam.luma.helper

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
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
    val showsPreviousButton: Boolean,
    val showsNextButton: Boolean,
)

object MediaSessionHelper {
    private const val LIGHT_OS_PACKAGE = "com.lightos"
    private const val PODCAST_SEEK_MS = 15_000L
    private const val TRACK_SWITCH_GRACE_MS = 1_500L

    private val _mediaInfo = MutableStateFlow<MediaInfo?>(null)
    val mediaInfo: StateFlow<MediaInfo?> = _mediaInfo.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaSessionManager: MediaSessionManager? = null
    private var audioManager: AudioManager? = null
    private var listenerComponent: ComponentName? = null
    private val activeCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private val dismissedPackageNames = mutableSetOf<String>()
    private var trackSwitchPackageName: String? = null
    private var trackSwitchGraceUntilUptimeMs = 0L
    private var trackSwitchGraceRunnable: Runnable? = null
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
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
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
            clearTrackSwitchGrace()
            clearCallbacks()
            _mediaInfo.value = null
        }
    }

    fun refresh() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            refreshSessions(registerCallbacks = trackingEnabled)
        } else {
            mainHandler.post { refreshSessions(registerCallbacks = trackingEnabled) }
        }
    }

    fun togglePlayPause() {
        val controller = activeController() ?: return
        clearTrackSwitchGrace()
        val playbackState = controller.playbackState?.state ?: PlaybackState.STATE_NONE
        if (showsPauseButton(playbackState)) {
            if (controller.packageName == LIGHT_OS_PACKAGE) {
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
            } else {
                controller.transportControls.pause()
            }
        } else {
            if (controller.packageName == LIGHT_OS_PACKAGE) {
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            } else {
                controller.transportControls.play()
            }
        }
    }

    fun skipToNext() {
        val controller = activeController() ?: return
        startTrackSwitchGrace(controller)
        if (controller.packageName == LIGHT_OS_PACKAGE) {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        } else {
            controller.transportControls.skipToNext()
        }
    }

    fun skipToPrevious() {
        val controller = activeController() ?: return
        startTrackSwitchGrace(controller)
        if (controller.packageName == LIGHT_OS_PACKAGE) {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        } else {
            controller.transportControls.skipToPrevious()
        }
    }

    fun rewind() {
        val controller = activeController() ?: return
        val playbackState = controller.playbackState ?: return
        val customAction = playbackState.podcastSeekCustomAction(isBack = true)
        if (customAction != null) {
            controller.transportControls.sendCustomAction(customAction.action, null)
        } else if (playbackState.hasAction(PlaybackState.ACTION_SEEK_TO)) {
            val position = (playbackState.currentPosition() - PODCAST_SEEK_MS).coerceAtLeast(0)
            controller.transportControls.seekTo(position)
        } else {
            controller.transportControls.rewind()
        }
    }

    fun fastForward() {
        val controller = activeController() ?: return
        val playbackState = controller.playbackState ?: return
        val customAction = playbackState.podcastSeekCustomAction(isBack = false)
        if (customAction != null) {
            controller.transportControls.sendCustomAction(customAction.action, null)
        } else if (playbackState.hasAction(PlaybackState.ACTION_SEEK_TO)) {
            controller.transportControls.seekTo(playbackState.currentPosition() + PODCAST_SEEK_MS)
        } else {
            controller.transportControls.fastForward()
        }
    }

    fun stopAndDismiss() {
        val controllers = activeCallbacks.keys.toList()
        val controller = activeController(controllers) ?: return
        clearTrackSwitchGrace()
        dismissedPackageNames.addAll(
            controllers
                .filterNot { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                .map { it.packageName },
        )
        controller.transportControls.stop()
        LumaNotificationListener.dismissMediaNotifications(controller.packageName)
        updateMediaInfo(controllers)
    }

    private fun activeController(
        controllers: List<MediaController> = activeCallbacks.keys.toList(),
    ): MediaController? {
        pruneDismissedPackages(controllers)

        val playingController =
            controllers.firstOrNull { controller ->
                controller.playbackState?.state == PlaybackState.STATE_PLAYING
            }
        if (playingController != null) {
            dismissedPackageNames.remove(playingController.packageName)
            return playingController
        }

        return controllers.firstOrNull { controller ->
            controller.isPausedSession() && controller.packageName !in dismissedPackageNames
        }
    }

    fun getActiveMediaPackageName(forceRefresh: Boolean = false): String? =
        if (forceRefresh || !trackingEnabled || activeCallbacks.isEmpty()) {
            activeController(currentControllers())?.packageName
        } else {
            activeController()?.packageName
        }

    private fun pruneDismissedPackages(controllers: List<MediaController>) {
        val activePackages = controllers.mapTo(mutableSetOf()) { it.packageName }
        dismissedPackageNames.retainAll(activePackages)
    }

    private fun MediaController.isPausedSession(): Boolean {
        val state = playbackState?.state
        val isPaused = state == PlaybackState.STATE_PAUSED || state == PlaybackState.STATE_BUFFERING
        return isPaused && (
            packageName == LIGHT_OS_PACKAGE ||
                LumaNotificationListener.hasActiveMediaNotification(packageName)
        )
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

    private fun hasPodcastMetadata(description: MediaDescription?): Boolean {
        val mediaUri = description?.mediaUri?.toString().orEmpty()
        val extras = description?.extras
        return extras?.let { bundle ->
            val mediaType = bundle.getString("android.mediaType") ?: bundle.getString("media_type")
            mediaType?.contains("podcast", ignoreCase = true) == true
        } == true || mediaUri.contains("podcast", ignoreCase = true)
    }

    private fun hasPodcastSeekCustomActions(playbackState: PlaybackState?): Boolean {
        return playbackState.podcastSeekCustomAction(isBack = true) != null &&
            playbackState.podcastSeekCustomAction(isBack = false) != null
    }

    private fun PlaybackState.CustomAction.matchesPodcastSeekAction(isBack: Boolean): Boolean {
        if (matchesPodcastSeekText(action, isBack)) return true
        return matchesPodcastSeekText(name?.toString().orEmpty(), isBack)
    }

    private fun PlaybackState?.podcastSeekCustomAction(isBack: Boolean): PlaybackState.CustomAction? =
        this?.customActions.orEmpty().firstOrNull { it.matchesPodcastSeekAction(isBack) }

    private fun matchesPodcastSeekText(
        value: String,
        isBack: Boolean,
    ): Boolean {
        if (!value.contains("15", ignoreCase = true)) return false

        val hasSeekHint =
            value.contains("seek", ignoreCase = true) ||
                value.contains("skip", ignoreCase = true) ||
                value.contains("replay", ignoreCase = true) ||
                value.contains("rewind", ignoreCase = true) ||
                value.contains("fastforward", ignoreCase = true) ||
                value.contains("fast_forward", ignoreCase = true)
        if (!hasSeekHint) return false

        return if (isBack) {
            value.contains("back", ignoreCase = true) ||
                value.contains("backward", ignoreCase = true) ||
                value.contains("replay", ignoreCase = true) ||
                value.contains("rewind", ignoreCase = true)
        } else {
            value.contains("forward", ignoreCase = true) ||
                value.contains("fastforward", ignoreCase = true) ||
                value.contains("fast_forward", ignoreCase = true)
        }
    }

    private fun PlaybackState.hasAction(action: Long): Boolean = actions and action != 0L

    private fun startTrackSwitchGrace(controller: MediaController) {
        val playbackState = controller.playbackState?.state ?: PlaybackState.STATE_NONE
        if (!showsPauseButton(playbackState)) return

        trackSwitchPackageName = controller.packageName
        trackSwitchGraceUntilUptimeMs = SystemClock.uptimeMillis() + TRACK_SWITCH_GRACE_MS
        trackSwitchGraceRunnable?.let(mainHandler::removeCallbacks)
        trackSwitchGraceRunnable =
            Runnable {
                clearTrackSwitchGrace()
                updateMediaInfo()
            }.also { runnable ->
                mainHandler.postDelayed(runnable, TRACK_SWITCH_GRACE_MS)
            }
    }

    private fun clearTrackSwitchGrace() {
        trackSwitchGraceRunnable?.let(mainHandler::removeCallbacks)
        trackSwitchGraceRunnable = null
        trackSwitchPackageName = null
        trackSwitchGraceUntilUptimeMs = 0L
    }

    private fun shouldShowPlayingControlsDuringTrackSwitch(controller: MediaController): Boolean {
        if (trackSwitchPackageName != controller.packageName) return false
        if (SystemClock.uptimeMillis() <= trackSwitchGraceUntilUptimeMs) return true
        clearTrackSwitchGrace()
        return false
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val manager = audioManager ?: return
        val downTime = SystemClock.uptimeMillis()
        manager.dispatchMediaKeyEvent(KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        manager.dispatchMediaKeyEvent(KeyEvent(downTime, downTime, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun PlaybackState.currentPosition(): Long {
        val basePosition = position.coerceAtLeast(0)
        if (state != PlaybackState.STATE_PLAYING) return basePosition

        val elapsedMs = SystemClock.elapsedRealtime() - lastPositionUpdateTime
        if (elapsedMs <= 0L) return basePosition

        return (basePosition + elapsedMs * playbackSpeed).toLong().coerceAtLeast(0)
    }

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
                LumaNotificationListener.mediaChangeVersion.collectLatest {
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

        val metadata = active.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist =
            metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: ""
        val playbackState = active.playbackState
        val playbackStatus = playbackState?.state ?: PlaybackState.STATE_NONE
        val showPlayingControlsDuringTrackSwitch = shouldShowPlayingControlsDuringTrackSwitch(active)
        val showsPauseButton = showPlayingControlsDuringTrackSwitch || showsPauseButton(playbackStatus)
        val showsStopButton = playbackStatus == PlaybackState.STATE_PAUSED && !showPlayingControlsDuringTrackSwitch
        val hasPodcastCustomSeekActions = hasPodcastSeekCustomActions(playbackState)
        val isPodcast =
            hasPodcastMetadata(metadata?.description) ||
                hasPodcastCustomSeekActions ||
                playbackState.hasPodcastSeekActions()
        val showsPreviousButton =
            if (isPodcast) {
                hasPodcastCustomSeekActions || playbackState.hasPodcastBackAction()
            } else {
                playbackState?.hasAction(PlaybackState.ACTION_SKIP_TO_PREVIOUS) == true
            }
        val showsNextButton =
            if (isPodcast) {
                hasPodcastCustomSeekActions || playbackState.hasPodcastForwardAction()
            } else {
                playbackState?.hasAction(PlaybackState.ACTION_SKIP_TO_NEXT) == true
            }

        _mediaInfo.value =
            MediaInfo(
                title = title,
                artist = artist,
                showsPauseButton = showsPauseButton,
                showsStopButton = showsStopButton,
                isPodcast = isPodcast,
                showsPreviousButton = showsPreviousButton,
                showsNextButton = showsNextButton,
            )
    }

    private fun PlaybackState?.hasPodcastSeekActions(): Boolean =
        this?.let { state ->
            state.hasAction(PlaybackState.ACTION_REWIND) &&
                state.hasAction(PlaybackState.ACTION_FAST_FORWARD)
        } == true

    private fun PlaybackState?.hasPodcastBackAction(): Boolean =
        this?.let { state ->
            state.hasAction(PlaybackState.ACTION_SEEK_TO) ||
                state.hasAction(PlaybackState.ACTION_REWIND)
        } == true

    private fun PlaybackState?.hasPodcastForwardAction(): Boolean =
        this?.let { state ->
            state.hasAction(PlaybackState.ACTION_SEEK_TO) ||
                state.hasAction(PlaybackState.ACTION_FAST_FORWARD)
        } == true
}
