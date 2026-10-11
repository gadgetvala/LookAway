package co.tekonika.lookaway.service

enum class Phase { STOPPED, WORKING, BREAK, PAUSED }

enum class Event { WARN, BREAK_STARTED, BREAK_FINISHED, PAUSE_ENDED }

data class BreakConfig(
    val workMs: Long,
    val breakMs: Long,
    /** false: continue where the countdown stopped. true: start fresh if the screen was off for a full break. */
    val resetAfterScreenOff: Boolean = false,
) {
    /** Heads-up before the overlay appears (macOS app: one 20s tick before the break). */
    val warnMs: Long get() = minOf(WARN_MS, workMs / 2)

    companion object {
        const val WARN_MS = 20_000L
        const val DEFAULT_WORK_MIN = 20
        const val DEFAULT_BREAK_SEC = 20
    }
}

/**
 * Pure, immutable 20-20-20 state machine. Every function takes `now` from
 * SystemClock.elapsedRealtime(), so it keeps counting through deep sleep and is trivially testable.
 *
 * Work time is only spent while the screen is on and unlocked. When the screen comes back, the
 * countdown continues where it stopped, or (with [BreakConfig.resetAfterScreenOff]) starts fresh if
 * the screen was off for at least one break length, since the user already looked away.
 */
data class BreakTimer(
    val phase: Phase = Phase.STOPPED,
    /** End of the current phase: WORKING (while screen is on), BREAK and PAUSED. */
    val endsAt: Long = 0L,
    /** WORKING time left, frozen while the screen is off. */
    val remaining: Long = 0L,
    /** When the screen went off, null while it is on. */
    val screenOffAt: Long? = null,
    /** Pre-break warning already delivered for this work cycle. */
    val warned: Boolean = false,
) {
    val isScreenOn: Boolean get() = screenOffAt == null

    /** Time left in the current phase as the user would perceive it. */
    fun timeLeft(now: Long): Long = when {
        phase == Phase.STOPPED -> 0L
        phase == Phase.WORKING && !isScreenOn -> remaining
        else -> (endsAt - now).coerceAtLeast(0L)
    }

    fun work(now: Long, c: BreakConfig) =
        copy(phase = Phase.WORKING, endsAt = now + c.workMs, remaining = c.workMs, warned = false)

    fun startBreak(now: Long, c: BreakConfig) = copy(phase = Phase.BREAK, endsAt = now + c.breakMs, warned = false)

    fun pause(now: Long, durationMs: Long) = copy(phase = Phase.PAUSED, endsAt = now + durationMs, warned = false)

    fun stop() = BreakTimer(screenOffAt = screenOffAt)

    fun screenOff(now: Long): BreakTimer {
        if (!isScreenOn) return this
        val left = if (phase == Phase.WORKING) (endsAt - now).coerceAtLeast(0L) else remaining
        return copy(screenOffAt = now, remaining = left)
    }

    fun screenOn(now: Long, c: BreakConfig): BreakTimer {
        val offAt = screenOffAt ?: return this
        val on = copy(screenOffAt = null)
        if (phase != Phase.WORKING) return on
        return if (c.resetAfterScreenOff && now - offAt >= c.breakMs) on.work(now, c) else on.copy(endsAt = now + remaining)
    }

    /** Advances at most one transition. Call until it returns a null event. */
    fun evaluate(now: Long, c: BreakConfig): Pair<BreakTimer, Event?> = when (phase) {
        Phase.WORKING -> when {
            !isScreenOn -> this to null
            now >= endsAt -> startBreak(now, c) to Event.BREAK_STARTED
            !warned && now >= endsAt - c.warnMs -> copy(warned = true) to Event.WARN
            else -> this to null
        }
        Phase.BREAK -> if (now >= endsAt) work(now, c) to Event.BREAK_FINISHED else this to null
        Phase.PAUSED -> if (now >= endsAt) work(now, c) to Event.PAUSE_ENDED else this to null
        Phase.STOPPED -> this to null
    }

    /** Next moment [evaluate] could change something, or null if only a screen event can. */
    fun nextWakeAt(c: BreakConfig): Long? = when (phase) {
        Phase.WORKING -> if (!isScreenOn) null else if (warned) endsAt else endsAt - c.warnMs
        Phase.BREAK, Phase.PAUSED -> endsAt
        Phase.STOPPED -> null
    }
}
