package app.luma.helper

enum class UnlockGatePhase {
    Idle,
    SecureMask,
    AwaitingCredential,
    UnlockGateVisible,
    Dismissing,
}

data class UnlockGateUiSnapshot(
    val phase: UnlockGatePhase = UnlockGatePhase.Idle,
    val visible: Boolean = false,
    val showingHomeStatusBar: Boolean = false,
)

internal data class UnlockGateState(
    val phase: UnlockGatePhase = UnlockGatePhase.Idle,
    val wakeArmed: Boolean = false,
    val repeatedHomeGateEligible: Boolean = false,
    val ignoreNextHomeUp: Boolean = false,
    val homeContentTopPx: Int = 0,
    val prefersHomeStatusBar: Boolean = false,
    val shownAtUptimeMs: Long = 0L,
    val dismissDeadlineUptimeMs: Long? = null,
) {
    fun toUiSnapshot(): UnlockGateUiSnapshot =
        UnlockGateUiSnapshot(
            phase = phase,
            visible = phase != UnlockGatePhase.Idle,
            showingHomeStatusBar =
                phase != UnlockGatePhase.Idle &&
                    prefersHomeStatusBar &&
                    homeContentTopPx > 0,
        )
}

internal sealed interface UnlockGateEffect {
    data object BringLumaToFront : UnlockGateEffect

    data object CancelDismiss : UnlockGateEffect

    data class ScheduleDismiss(
        val delayMs: Long,
    ) : UnlockGateEffect

    data object StartSecureDismissGesture : UnlockGateEffect
}

internal sealed interface UnlockGateEvent {
    data class DismissRequested(
        val nowUptimeMs: Long,
        val minDelayMs: Long,
    ) : UnlockGateEvent

    data class DismissTimeout(
        val nowUptimeMs: Long,
    ) : UnlockGateEvent

    data class HomeKeyDown(
        val nowUptimeMs: Long,
        val gateEnabled: Boolean,
    ) : UnlockGateEvent

    data class HomeKeyUp(
        val nowUptimeMs: Long,
        val minDelayMs: Long,
    ) : UnlockGateEvent

    data class LauncherIntentConsumed(
        val nowUptimeMs: Long,
        val minDelayMs: Long,
    ) : UnlockGateEvent

    data class ScreenOff(
        val nowUptimeMs: Long,
    ) : UnlockGateEvent

    data class ScreenOn(
        val nowUptimeMs: Long,
        val gateEnabled: Boolean,
        val deviceLocked: Boolean,
    ) : UnlockGateEvent

    data class SetHomeContentTop(
        val contentTopPx: Int,
    ) : UnlockGateEvent

    data class SetRepeatedHomeGateEligible(
        val eligible: Boolean,
    ) : UnlockGateEvent

    data object SecureGestureCompleted : UnlockGateEvent

    data object SecureGestureFailed : UnlockGateEvent

    data object SecureMaskTapped : UnlockGateEvent

    data class UserPresent(
        val nowUptimeMs: Long,
        val gateEnabled: Boolean,
        val deviceLocked: Boolean,
    ) : UnlockGateEvent

    data class RestoreRequested(
        val nowUptimeMs: Long,
    ) : UnlockGateEvent
}

internal class UnlockGateStateMachine(
    private val minVisibilityMs: Long,
) {
    var state: UnlockGateState = UnlockGateState()
        private set

    val snapshot: UnlockGateUiSnapshot
        get() = state.toUiSnapshot()

    fun dispatch(event: UnlockGateEvent): List<UnlockGateEffect> {
        val previousState = state
        val reduction = reduce(previousState, event)
        val effects = reduction.effects.toMutableList()

        if (previousState.dismissDeadlineUptimeMs != null && reduction.state.dismissDeadlineUptimeMs == null) {
            effects.add(0, UnlockGateEffect.CancelDismiss)
        }

        state = reduction.state
        return effects
    }

    fun forceIdle(clearRepeatedHomeGateEligibility: Boolean) {
        state =
            UnlockGateState(
                repeatedHomeGateEligible = if (clearRepeatedHomeGateEligibility) false else state.repeatedHomeGateEligible,
                homeContentTopPx = state.homeContentTopPx,
            )
    }

    private fun reduce(
        currentState: UnlockGateState,
        event: UnlockGateEvent,
    ): Reduction =
        when (event) {
            is UnlockGateEvent.DismissRequested ->
                scheduleDismiss(
                    currentState = currentState,
                    nowUptimeMs = event.nowUptimeMs,
                    minDelayMs = event.minDelayMs,
                )

            is UnlockGateEvent.DismissTimeout -> {
                val dismissDeadline = currentState.dismissDeadlineUptimeMs
                if (currentState.phase != UnlockGatePhase.Dismissing || dismissDeadline == null || event.nowUptimeMs < dismissDeadline) {
                    Reduction(currentState)
                } else {
                    Reduction(currentState.enterIdle())
                }
            }

            is UnlockGateEvent.HomeKeyDown -> {
                if (currentState.phase != UnlockGatePhase.Idle || !event.gateEnabled || !currentState.repeatedHomeGateEligible) {
                    Reduction(currentState)
                } else {
                    Reduction(
                        currentState.enterVisible(
                            phase = UnlockGatePhase.UnlockGateVisible,
                            nowUptimeMs = event.nowUptimeMs,
                            ignoreNextHomeUp = true,
                        ),
                    )
                }
            }

            is UnlockGateEvent.HomeKeyUp -> {
                if (currentState.phase != UnlockGatePhase.UnlockGateVisible && currentState.phase != UnlockGatePhase.Dismissing) {
                    Reduction(currentState)
                } else if (currentState.ignoreNextHomeUp) {
                    Reduction(
                        currentState.copy(
                            ignoreNextHomeUp = false,
                            dismissDeadlineUptimeMs = null,
                        ),
                    )
                } else {
                    scheduleDismiss(
                        currentState = currentState,
                        nowUptimeMs = event.nowUptimeMs,
                        minDelayMs = event.minDelayMs,
                    )
                }
            }

            is UnlockGateEvent.LauncherIntentConsumed ->
                scheduleDismiss(
                    currentState = currentState,
                    nowUptimeMs = event.nowUptimeMs,
                    minDelayMs = event.minDelayMs,
                )

            is UnlockGateEvent.ScreenOff ->
                Reduction(
                    currentState.enterIdle().copy(
                        wakeArmed = true,
                    ),
                )

            is UnlockGateEvent.ScreenOn -> {
                if (!currentState.wakeArmed) {
                    Reduction(currentState)
                } else if (!event.gateEnabled) {
                    Reduction(currentState.enterIdle())
                } else if (event.deviceLocked) {
                    Reduction(
                        currentState.enterVisible(
                            phase = UnlockGatePhase.SecureMask,
                            nowUptimeMs = event.nowUptimeMs,
                        ).copy(
                            wakeArmed = false,
                        ),
                    )
                } else {
                    Reduction(
                        currentState.enterVisible(
                            phase = UnlockGatePhase.UnlockGateVisible,
                            nowUptimeMs = event.nowUptimeMs,
                        ).copy(
                            wakeArmed = false,
                        ),
                        effects = listOf(UnlockGateEffect.BringLumaToFront),
                    )
                }
            }

            is UnlockGateEvent.SetHomeContentTop ->
                Reduction(
                    currentState.copy(
                        homeContentTopPx = event.contentTopPx.coerceAtLeast(0),
                    ),
                )

            is UnlockGateEvent.SetRepeatedHomeGateEligible ->
                Reduction(
                    currentState.copy(
                        repeatedHomeGateEligible = event.eligible,
                    ),
                )

            UnlockGateEvent.SecureGestureCompleted -> Reduction(currentState)

            UnlockGateEvent.SecureGestureFailed -> {
                if (currentState.phase != UnlockGatePhase.AwaitingCredential) {
                    Reduction(currentState)
                } else {
                    Reduction(
                        currentState.copy(
                            phase = UnlockGatePhase.SecureMask,
                            dismissDeadlineUptimeMs = null,
                        ),
                    )
                }
            }

            UnlockGateEvent.SecureMaskTapped -> {
                if (currentState.phase != UnlockGatePhase.SecureMask) {
                    Reduction(currentState)
                } else {
                    Reduction(
                        currentState.copy(
                            phase = UnlockGatePhase.AwaitingCredential,
                            dismissDeadlineUptimeMs = null,
                        ),
                        effects = listOf(UnlockGateEffect.StartSecureDismissGesture),
                    )
                }
            }

            is UnlockGateEvent.UserPresent -> {
                val shouldShowUnlockGate =
                    event.gateEnabled &&
                        (
                            currentState.wakeArmed ||
                                currentState.phase == UnlockGatePhase.SecureMask ||
                                currentState.phase == UnlockGatePhase.AwaitingCredential
                        )
                if (!shouldShowUnlockGate) {
                    Reduction(currentState.copy(wakeArmed = false))
                } else {
                    Reduction(
                        currentState.enterVisible(
                            phase = UnlockGatePhase.UnlockGateVisible,
                            nowUptimeMs = event.nowUptimeMs,
                        ).copy(
                            wakeArmed = false,
                        ),
                        effects = listOf(UnlockGateEffect.BringLumaToFront),
                    )
                }
            }

            is UnlockGateEvent.RestoreRequested ->
                if (currentState.phase != UnlockGatePhase.Idle) {
                    Reduction(currentState)
                } else {
                    Reduction(
                        currentState.enterVisible(
                            phase = UnlockGatePhase.UnlockGateVisible,
                            nowUptimeMs = event.nowUptimeMs,
                        ),
                    )
                }
        }

    private fun scheduleDismiss(
        currentState: UnlockGateState,
        nowUptimeMs: Long,
        minDelayMs: Long,
    ): Reduction {
        if (currentState.phase != UnlockGatePhase.UnlockGateVisible && currentState.phase != UnlockGatePhase.Dismissing) {
            return Reduction(currentState)
        }

        val remainingDelay =
            maxOf(
                minDelayMs,
                (minVisibilityMs - (nowUptimeMs - currentState.shownAtUptimeMs)).coerceAtLeast(0L),
            )
        if (remainingDelay == 0L) {
            return Reduction(currentState.enterIdle())
        }

        return Reduction(
            currentState.copy(
                phase = UnlockGatePhase.Dismissing,
                dismissDeadlineUptimeMs = nowUptimeMs + remainingDelay,
            ),
            effects = listOf(UnlockGateEffect.ScheduleDismiss(remainingDelay)),
        )
    }

    private fun UnlockGateState.enterIdle(): UnlockGateState =
        copy(
            phase = UnlockGatePhase.Idle,
            wakeArmed = false,
            ignoreNextHomeUp = false,
            prefersHomeStatusBar = false,
            shownAtUptimeMs = 0L,
            dismissDeadlineUptimeMs = null,
        )

    private fun UnlockGateState.enterVisible(
        phase: UnlockGatePhase,
        nowUptimeMs: Long,
        ignoreNextHomeUp: Boolean = false,
    ): UnlockGateState =
        copy(
            phase = phase,
            ignoreNextHomeUp = ignoreNextHomeUp,
            prefersHomeStatusBar = false,
            shownAtUptimeMs = nowUptimeMs,
            dismissDeadlineUptimeMs = null,
        )

    private data class Reduction(
        val state: UnlockGateState,
        val effects: List<UnlockGateEffect> = emptyList(),
    )
}
