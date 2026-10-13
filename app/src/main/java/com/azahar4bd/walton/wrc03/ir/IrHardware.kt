package com.azahar4bd.walton.wrc03.ir

/** One continuous band of carrier frequencies an IR blaster can modulate. */
data class CarrierRange(
    val minFrequencyHz: Int,
    val maxFrequencyHz: Int,
) {
    fun contains(frequencyHz: Int): Boolean = frequencyHz in minFrequencyHz..maxFrequencyHz

    override fun toString(): String = "$minFrequencyHz–$maxFrequencyHz Hz"
}

/**
 * Thin abstraction over `android.hardware.ConsumerIrManager` so the send/availability
 * logic can be unit tested on a plain JVM (see `IrRemoteControllerTest`).
 */
interface IrHardware {
    /** `ConsumerIrManager.hasIrEmitter()`. */
    val hasEmitter: Boolean

    /**
     * `ConsumerIrManager.getCarrierFrequencies()`. Empty or null means the device does
     * not advertise any range, in which case we assume 38 kHz is supported.
     */
    val carrierRanges: List<CarrierRange>?

    /** `ConsumerIrManager.transmit(carrierFrequency, pattern)`. May throw. */
    fun transmit(carrierFrequencyHz: Int, pattern: IntArray)
}
