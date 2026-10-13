package com.azahar4bd.walton.wrc03.ir

/** Result of asking a phone "can you actually beam IR at 38 kHz?". */
sealed interface IrAvailability {
    /** No `ConsumerIrManager` service on this device at all (very rare, API >= 19). */
    data object NotSupported : IrAvailability

    /** The service exists but reports no IR emitter — most modern phones. */
    data object NoEmitter : IrAvailability

    /** Emitter exists but the advertised carrier bands do not cover 38 kHz. */
    data class CarrierUnsupported(
        val ranges: List<CarrierRange>,
        val targetFrequencyHz: Int,
    ) : IrAvailability

    /** Good to go. [ranges] is empty when the device does not advertise any band. */
    data class Ready(
        val ranges: List<CarrierRange>,
        val targetFrequencyHz: Int,
    ) : IrAvailability
}

object IrAvailabilityDetector {
    /**
     * @param hardware null when the system service is missing.
     * @param targetFrequencyHz the carrier the profile needs, 38 kHz for NEC/6122.
     */
    fun detect(
        hardware: IrHardware?,
        targetFrequencyHz: Int = NecProtocol.CARRIER_FREQUENCY_HZ,
    ): IrAvailability {
        val emitter = hardware ?: return IrAvailability.NotSupported
        if (!emitter.hasEmitter) return IrAvailability.NoEmitter

        val ranges = emitter.carrierRanges
        if (ranges.isNullOrEmpty()) {
            // Device does not advertise bands: assume it covers the standard range.
            return IrAvailability.Ready(emptyList(), targetFrequencyHz)
        }
        return if (ranges.any { it.contains(targetFrequencyHz) }) {
            IrAvailability.Ready(ranges, targetFrequencyHz)
        } else {
            IrAvailability.CarrierUnsupported(ranges, targetFrequencyHz)
        }
    }
}
