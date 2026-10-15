package com.azahar4bd.walton.wrc03.scan

import com.azahar4bd.walton.wrc03.ir.IrSendResult
import com.azahar4bd.walton.wrc03.model.ScannedCode

/**
 * Walks a [ScanPlan] candidate by candidate until the user shouts "that one worked".
 *
 * The engine owns no clock and no Android type: it asks a [ScanScheduler] to call it back
 * and a [ScanTransmitter] to put the frame in the air. That keeps the whole sweep —
 * including its failure paths — testable on a plain JVM.
 *
 * Each candidate goes out [pulsesPerCandidate] times [pulseGapMs] apart, the way a real
 * handset repeats a key, because a single frame is easy for a TV to miss.
 */
class CodeScanEngine(
    val plan: ScanPlan,
    private val transmitter: ScanTransmitter,
    private val scheduler: ScanScheduler,
    initialIntervalMs: Long = DEFAULT_INTERVAL_MS,
    private val pulsesPerCandidate: Int = PULSES_PER_CANDIDATE,
    private val pulseGapMs: Long = PULSE_GAP_MS,
) {
    init {
        require(initialIntervalMs in MIN_INTERVAL_MS..MAX_INTERVAL_MS) {
            "interval must stay in $MIN_INTERVAL_MS..$MAX_INTERVAL_MS ms, got $initialIntervalMs"
        }
        require(pulsesPerCandidate >= 1) { "a candidate has to be sent at least once" }
        require(pulseGapMs >= 0) { "pulse gap cannot be negative" }
    }

    /** Called after every state change, on the thread that triggered it. */
    var listener: ((ScanSnapshot) -> Unit)? = null

    var state: ScanState = ScanState.IDLE
        private set

    /** Pause between two candidates. May be retuned live, straight from the seek bar. */
    var intervalMs: Long = initialIntervalMs
        set(value) {
            require(value in MIN_INTERVAL_MS..MAX_INTERVAL_MS) {
                "interval must stay in $MIN_INTERVAL_MS..$MAX_INTERVAL_MS ms, got $value"
            }
            field = value
        }

    /** Index of the candidate in flight, `-1` before the first one. */
    var cursor: Int = -1
        private set

    var found: List<ScannedCode> = emptyList()
        private set

    var failureMessageBn: String? = null
        private set

    private var pulsesLeft: Int = 0

    val currentCandidate: ScanCandidate? get() = plan.candidateAt(cursor)

    val sentCount: Int get() = if (cursor < 0) 0 else cursor + 1

    /** Reset and beam the first candidate. Also how "আবার শুরু" works after a sweep. */
    fun start(): ScanSnapshot {
        scheduler.cancel()
        cursor = -1
        pulsesLeft = 0
        failureMessageBn = null
        state = ScanState.RUNNING
        advance()
        return notifyChanged()
    }

    fun pause(): ScanSnapshot {
        if (state == ScanState.RUNNING) {
            state = ScanState.PAUSED
            scheduler.cancel()
        }
        return notifyChanged()
    }

    fun resume(): ScanSnapshot {
        if (state == ScanState.PAUSED) {
            state = ScanState.RUNNING
            scheduleNext(intervalMs)
        }
        return notifyChanged()
    }

    /** Back to [ScanState.IDLE]; codes already marked as found are kept. */
    fun stop(): ScanSnapshot {
        scheduler.cancel()
        state = ScanState.IDLE
        cursor = -1
        pulsesLeft = 0
        failureMessageBn = null
        return notifyChanged()
    }

    /** Jump to the next candidate without waiting for the interval ("এক ধাপ এগিয়ে দিন"). */
    fun skip(): ScanSnapshot {
        if (state == ScanState.RUNNING) advance()
        return notifyChanged()
    }

    /**
     * The TV reacted: remember the candidate in flight and stop sweeping, so the set is
     * not hammered while the user reads the result.
     */
    fun markFound(foundAtEpochMs: Long): ScanSnapshot {
        val candidate = currentCandidate
        if (candidate != null && snapshot().canMarkFound) {
            val record =
                ScannedCode(
                    command = plan.target,
                    address = candidate.address,
                    addressMode = candidate.addressMode,
                    code = candidate.commandByte,
                    carrierHz = candidate.carrierHz,
                    foundAtEpochMs = foundAtEpochMs,
                )
            val alreadyKnown =
                found.any {
                    it.address == record.address &&
                        it.addressMode == record.addressMode &&
                        it.code == record.code
                }
            if (!alreadyKnown) found = found + record
        }
        pause()
        return notifyChanged()
    }

    fun snapshot(): ScanSnapshot =
        ScanSnapshot(
            state = state,
            target = plan.target,
            candidate = currentCandidate,
            sentCount = sentCount,
            total = plan.size,
            intervalMs = intervalMs,
            remainingMs = remainingMs(),
            found = found,
            failureMessageBn = failureMessageBn,
        )

    // --- internals -----------------------------------------------------------

    private fun remainingMs(): Long {
        val left = (plan.size - sentCount).coerceAtLeast(0)
        val perCandidate = intervalMs + ((pulsesPerCandidate - 1) * pulseGapMs)
        return left * perCandidate
    }

    private fun advance() {
        scheduler.cancel()
        val next = cursor + 1
        if (next >= plan.size) {
            state = ScanState.FINISHED
            pulsesLeft = 0
            return
        }
        cursor = next
        pulsesLeft = pulsesPerCandidate
        firePulse()
    }

    private fun firePulse() {
        val candidate = currentCandidate ?: return
        when (val result = transmitter.transmit(candidate)) {
            is IrSendResult.Sent -> {
                pulsesLeft -= 1
                if (pulsesLeft > 0) {
                    scheduler.schedule(pulseGapMs) {
                        if (state == ScanState.RUNNING) {
                            firePulse()
                            notifyChanged()
                        }
                    }
                } else {
                    scheduleNext(intervalMs)
                }
            }

            is IrSendResult.Failed -> {
                // No point sweeping 700 codes the phone cannot send: stop and say why.
                state = ScanState.FAILED
                failureMessageBn = result.reason.messageBn
                pulsesLeft = 0
                scheduler.cancel()
            }
        }
    }

    private fun scheduleNext(delayMs: Long) {
        scheduler.schedule(delayMs) {
            if (state == ScanState.RUNNING) {
                advance()
                notifyChanged()
            }
        }
    }

    private fun notifyChanged(): ScanSnapshot {
        val snapshot = snapshot()
        listener?.invoke(snapshot)
        return snapshot
    }

    companion object {
        /** Pause between two candidates; short enough to sweep, long enough to watch. */
        const val DEFAULT_INTERVAL_MS = 1_200L
        const val MIN_INTERVAL_MS = 600L
        const val MAX_INTERVAL_MS = 3_000L

        /** Full frames sent per candidate, and the gap between them. */
        const val PULSES_PER_CANDIDATE = 2
        const val PULSE_GAP_MS = 110L
    }
}
