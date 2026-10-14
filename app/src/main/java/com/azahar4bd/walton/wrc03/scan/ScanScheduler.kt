package com.azahar4bd.walton.wrc03.scan

import com.azahar4bd.walton.wrc03.ir.IrSendResult

/**
 * Delayed-callback seam for [CodeScanEngine].
 *
 * The app drives it with a `Handler` ([HandlerScanScheduler]); unit tests drive it by
 * hand so a whole sweep can be checked without sleeping a single millisecond.
 */
interface ScanScheduler {
    /** Replace any pending callback with [action], to run after [delayMs]. */
    fun schedule(
        delayMs: Long,
        action: () -> Unit,
    )

    /** Drop the pending callback, if any. */
    fun cancel()
}

/** Beams one candidate; the app wires this to `IrRemoteController.send(command, frame)`. */
fun interface ScanTransmitter {
    fun transmit(candidate: ScanCandidate): IrSendResult
}
