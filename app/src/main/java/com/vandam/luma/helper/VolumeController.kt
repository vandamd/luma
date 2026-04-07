package com.vandam.luma.helper

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.KeyEvent
import android.widget.Toast
import com.vandam.luma.R

private const val VOLUME_INDICATOR_HIDE_DELAY_MS = 1500L

private data class VolumeIndicatorState(
    val labelRes: Int,
    val progress: Float,
)

private enum class VolumeStateType {
    Media,
    Ringer,
    Vibrate,
    Silent,
}

private data class VolumeState(
    val type: VolumeStateType,
    val volume: Int = 0,
    val maxVolume: Int = 0,
) {
    fun toIndicatorState(): VolumeIndicatorState =
        when (type) {
            VolumeStateType.Media -> {
                VolumeIndicatorState(
                    labelRes = R.string.volume_indicator_media,
                    progress = if (maxVolume <= 0) 0f else volume.toFloat() / maxVolume.toFloat(),
                )
            }

            VolumeStateType.Ringer -> {
                VolumeIndicatorState(
                    labelRes = R.string.volume_indicator_ringer,
                    progress = if (maxVolume <= 0) 0f else volume.toFloat() / maxVolume.toFloat(),
                )
            }

            VolumeStateType.Vibrate -> {
                VolumeIndicatorState(
                    labelRes = R.string.volume_indicator_vibrate,
                    progress = 0f,
                )
            }

            VolumeStateType.Silent -> {
                VolumeIndicatorState(
                    labelRes = R.string.volume_indicator_silent,
                    progress = 0f,
                )
            }
        }
}

private data class VolumePrediction(
    val state: VolumeState,
    val promptForSilentAccess: Boolean = false,
)

class VolumeController(
    private val context: Context,
) {
    private var audioManager: AudioManager? = null
    private var notificationManager: NotificationManager? = null
    private var volumeWorkerThread: HandlerThread? = null
    private var volumeWorkerHandler: Handler? = null
    private var lastKnownVolumeState: VolumeState? = null
    private var volumeApplyGeneration = 0L

    private val isVolumeIndicatorVisible: Boolean
        get() = ActionService.instance()?.isVolumeOnlyOverlayVisible() == true

    fun init() {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        volumeWorkerThread =
            HandlerThread("VolumeWorker").also {
                it.start()
                volumeWorkerHandler = Handler(it.looper)
            }
    }

    fun destroy() {
        volumeWorkerHandler?.removeCallbacksAndMessages(null)
        volumeWorkerThread?.quitSafely()
        volumeWorkerHandler = null
        volumeWorkerThread = null
        audioManager = null
        notificationManager = null
        lastKnownVolumeState = null
        volumeApplyGeneration = 0L
    }

    fun handleVolumeKey(keyCode: Int): Boolean {
        val am = audioManager ?: return false
        val nm = notificationManager ?: return false

        val isVolumeUp =
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> true
                KeyEvent.KEYCODE_VOLUME_DOWN -> false
                else -> return false
            }

        val currentState =
            if (isVolumeIndicatorVisible) {
                lastKnownVolumeState ?: readCurrentVolumeState(am)
            } else {
                readCurrentVolumeState(am)
            }
        val prediction = predictNextVolumeState(currentState, isVolumeUp, nm.isNotificationPolicyAccessGranted)
        if (prediction.promptForSilentAccess) {
            showToast(context, context.getString(R.string.toast_volume_silent_requires_permission), Toast.LENGTH_LONG)
            openNotificationPolicyAccessSettings(context)
        }

        lastKnownVolumeState = prediction.state
        showVolumeIndicator(prediction.state.toIndicatorState())
        val generation = ++volumeApplyGeneration
        if (!prediction.promptForSilentAccess) {
            enqueueVolumeApply(prediction.state, generation, am, nm)
        }
        return true
    }

    private fun readCurrentVolumeState(audioManager: AudioManager): VolumeState {
        if (audioManager.isMusicActive) {
            return VolumeState(
                type = VolumeStateType.Media,
                volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
                maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1),
            )
        }

        val maxRingVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1)
        when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_VIBRATE -> {
                return VolumeState(
                    type = VolumeStateType.Vibrate,
                    maxVolume = maxRingVolume,
                )
            }

            AudioManager.RINGER_MODE_SILENT -> {
                return VolumeState(
                    type = VolumeStateType.Silent,
                    maxVolume = maxRingVolume,
                )
            }

            else -> {
                return VolumeState(
                    type = VolumeStateType.Ringer,
                    volume = audioManager.getStreamVolume(AudioManager.STREAM_RING),
                    maxVolume = maxRingVolume,
                )
            }
        }
    }

    private fun predictNextVolumeState(
        currentState: VolumeState,
        isVolumeUp: Boolean,
        canEnterSilent: Boolean,
    ): VolumePrediction =
        when (currentState.type) {
            VolumeStateType.Media -> {
                val targetVolume =
                    if (isVolumeUp) {
                        (currentState.volume + 1).coerceAtMost(currentState.maxVolume)
                    } else {
                        (currentState.volume - 1).coerceAtLeast(0)
                    }
                VolumePrediction(currentState.copy(volume = targetVolume))
            }

            VolumeStateType.Ringer -> {
                if (isVolumeUp) {
                    val targetVolume =
                        if (currentState.volume == 0) {
                            1
                        } else {
                            (currentState.volume + 1).coerceAtMost(currentState.maxVolume)
                        }
                    VolumePrediction(currentState.copy(volume = targetVolume))
                } else {
                    if (currentState.volume > 1) {
                        VolumePrediction(currentState.copy(volume = currentState.volume - 1))
                    } else {
                        VolumePrediction(
                            VolumeState(
                                type = VolumeStateType.Vibrate,
                                maxVolume = currentState.maxVolume,
                            ),
                        )
                    }
                }
            }

            VolumeStateType.Vibrate -> {
                if (isVolumeUp) {
                    VolumePrediction(
                        VolumeState(
                            type = VolumeStateType.Ringer,
                            volume = 1,
                            maxVolume = currentState.maxVolume,
                        ),
                    )
                } else {
                    if (canEnterSilent) {
                        VolumePrediction(
                            VolumeState(
                                type = VolumeStateType.Silent,
                                maxVolume = currentState.maxVolume,
                            ),
                        )
                    } else {
                        VolumePrediction(currentState, promptForSilentAccess = true)
                    }
                }
            }

            VolumeStateType.Silent -> {
                if (isVolumeUp) {
                    VolumePrediction(
                        VolumeState(
                            type = VolumeStateType.Vibrate,
                            maxVolume = currentState.maxVolume,
                        ),
                    )
                } else {
                    VolumePrediction(currentState)
                }
            }
        }

    private fun enqueueVolumeApply(
        state: VolumeState,
        generation: Long,
        audioManager: AudioManager,
        notificationManager: NotificationManager,
    ) {
        val handler = volumeWorkerHandler ?: return
        val mainHandler = Handler(Looper.getMainLooper())

        handler.removeCallbacksAndMessages(null)
        handler.post {
            applyVolumeState(audioManager, notificationManager, state)
            val actualState = readCurrentVolumeState(audioManager)
            mainHandler.post {
                if (generation == volumeApplyGeneration) {
                    lastKnownVolumeState = actualState
                    if (isVolumeIndicatorVisible) {
                        showVolumeIndicator(actualState.toIndicatorState())
                    }
                }
            }
        }
    }

    private fun applyVolumeState(
        audioManager: AudioManager,
        notificationManager: NotificationManager,
        state: VolumeState,
    ) {
        when (state.type) {
            VolumeStateType.Media -> {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, state.volume, 0)
            }

            VolumeStateType.Ringer -> {
                trySetRingerMode(audioManager, notificationManager, AudioManager.RINGER_MODE_NORMAL)
                audioManager.setStreamVolume(AudioManager.STREAM_RING, state.volume, 0)
            }

            VolumeStateType.Vibrate -> {
                audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
                trySetRingerMode(audioManager, notificationManager, AudioManager.RINGER_MODE_VIBRATE)
            }

            VolumeStateType.Silent -> {
                audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
                trySetRingerMode(audioManager, notificationManager, AudioManager.RINGER_MODE_SILENT)
            }
        }
    }

    private fun trySetRingerMode(
        audioManager: AudioManager,
        notificationManager: NotificationManager,
        mode: Int,
        promptForPolicyAccess: Boolean = false,
    ): Boolean {
        if (mode == AudioManager.RINGER_MODE_SILENT && !notificationManager.isNotificationPolicyAccessGranted) {
            if (promptForPolicyAccess) {
                showToast(context, context.getString(R.string.toast_volume_silent_requires_permission), Toast.LENGTH_LONG)
                openNotificationPolicyAccessSettings(context)
            }
            return false
        }

        return try {
            audioManager.ringerMode = mode
            true
        } catch (_: SecurityException) {
            if (promptForPolicyAccess) {
                showToast(context, context.getString(R.string.toast_volume_silent_requires_permission), Toast.LENGTH_LONG)
                openNotificationPolicyAccessSettings(context)
            }
            false
        }
    }

    private fun showVolumeIndicator(state: VolumeIndicatorState) {
        ActionService.instance()?.showVolumeOnlyOverlay(state.labelRes, state.progress, compactPadding = true)
    }
}
