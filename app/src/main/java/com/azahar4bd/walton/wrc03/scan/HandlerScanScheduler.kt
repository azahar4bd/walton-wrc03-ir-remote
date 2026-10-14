package com.azahar4bd.walton.wrc03.scan

import android.os.Handler
import android.os.Looper

/**
 * [ScanScheduler] backed by the main looper, so every tick lands on the UI thread and the
 * activity can render straight from the snapshot the engine hands back.
 */
class HandlerScanScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : ScanScheduler {
    private var pending: (() -> Unit)? = null

    private val tick =
        Runnable {
            val action = pending
            pending = null
            action?.invoke()
        }

    override fun schedule(
        delayMs: Long,
        action: () -> Unit,
    ) {
        cancel()
        pending = action
        handler.postDelayed(tick, delayMs)
    }

    override fun cancel() {
        handler.removeCallbacks(tick)
        pending = null
    }
}
