package com.azahar4bd.walton.wrc03.scan

import com.azahar4bd.walton.wrc03.model.RemoteCommand
import com.azahar4bd.walton.wrc03.model.ScannedCode

/** Lifecycle of one sweep. */
enum class ScanState(
    val labelBn: String,
) {
    /** Nothing has been sent yet (also the state after "রিসেট"). */
    IDLE("স্ক্যান শুরু হয়নি"),

    /** Candidates are going out one after another. */
    RUNNING("স্ক্যান চলছে"),

    /** The user paused it, or just marked a code as found. */
    PAUSED("স্ক্যান থামানো"),

    /** The last candidate has been sent. */
    FINISHED("স্ক্যান শেষ"),

    /** The phone refused to transmit — no blaster, wrong carrier, driver error. */
    FAILED("স্ক্যান ব্যর্থ"),
}

/**
 * Everything the scan screen needs to draw, in one immutable value.
 *
 * @property sentCount candidates already beamed out (`0` before the first tick).
 * @property remainingMs rough air time left, for the "আনুমানিক বাকি …" line.
 */
data class ScanSnapshot(
    val state: ScanState,
    val target: RemoteCommand,
    val candidate: ScanCandidate?,
    val sentCount: Int,
    val total: Int,
    val intervalMs: Long,
    val remainingMs: Long,
    val found: List<ScannedCode>,
    val failureMessageBn: String?,
) {
    val isRunning: Boolean get() = state == ScanState.RUNNING

    val progress: Float get() = if (total <= 0) 0f else sentCount.toFloat() / total.toFloat()

    /** The button may only be pressed once something has actually gone out. */
    val canMarkFound: Boolean get() = candidate != null

    val isFinished: Boolean get() = state == ScanState.FINISHED || state == ScanState.FAILED
}
