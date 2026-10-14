package com.azahar4bd.walton.wrc03.ir

import android.content.Context
import android.hardware.ConsumerIrManager

/**
 * `ConsumerIrManager` (API 19+) behind the [IrHardware] seam.
 *
 * Requires `<uses-permission android:name="android.permission.TRANSMIT_IR" />`;
 * it is a normal permission, so no runtime prompt is needed.
 */
class ConsumerIrHardware(
    private val manager: ConsumerIrManager,
) : IrHardware {
    override val hasEmitter: Boolean
        get() = manager.hasIrEmitter()

    override val carrierRanges: List<CarrierRange>?
        get() =
            manager.carrierFrequencies?.map { range ->
                CarrierRange(range.minFrequency, range.maxFrequency)
            }

    override fun transmit(
        carrierFrequencyHz: Int,
        pattern: IntArray,
    ) {
        manager.transmit(carrierFrequencyHz, pattern)
    }

    companion object {
        /** Returns null when the device has no IR service at all. */
        fun create(context: Context): IrHardware? {
            val manager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
            return manager?.let(::ConsumerIrHardware)
        }
    }
}
