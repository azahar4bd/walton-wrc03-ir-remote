package com.azahar4bd.walton.wrc03.scan

/**
 * Hand-cranked [ScanScheduler]: nothing happens until the test says so, which lets a whole
 * sweep be verified without sleeping a single millisecond.
 */
class ManualScanScheduler : ScanScheduler {
    private var pending: (() -> Unit)? = null

    /** Every delay this scheduler was asked for, in order. */
    val scheduledDelays = mutableListOf<Long>()

    /** Delay of the callback currently waiting, null when nothing is pending. */
    var pendingDelayMs: Long? = null
        private set

    var cancelCount: Int = 0
        private set

    val hasPending: Boolean get() = pending != null

    override fun schedule(
        delayMs: Long,
        action: () -> Unit,
    ) {
        pending = action
        pendingDelayMs = delayMs
        scheduledDelays += delayMs
    }

    override fun cancel() {
        cancelCount += 1
        pending = null
        pendingDelayMs = null
    }

    /** Run the single pending callback. Fails loudly when there is none. */
    fun runPending() {
        val action = pending ?: error("no callback was scheduled")
        pending = null
        pendingDelayMs = null
        action()
    }

    /** Keep running pending callbacks until the engine stops scheduling more. */
    fun drain(maxTicks: Int = 100_000) {
        var ticks = 0
        while (hasPending) {
            check(ticks < maxTicks) { "the sweep did not settle after $maxTicks ticks" }
            runPending()
            ticks += 1
        }
    }
}
