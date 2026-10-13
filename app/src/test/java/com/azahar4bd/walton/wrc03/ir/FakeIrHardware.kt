package com.azahar4bd.walton.wrc03.ir

/** In-memory [IrHardware] so the controller can be driven from a JVM unit test. */
class FakeIrHardware(
    override val hasEmitter: Boolean = true,
    override val carrierRanges: List<CarrierRange>? = null,
    private val failure: RuntimeException? = null,
) : IrHardware {
    /** Everything that was handed to [transmit], in order. */
    val transmissions = mutableListOf<Transmission>()

    data class Transmission(
        val carrierFrequencyHz: Int,
        val pattern: IntArray,
    )

    override fun transmit(
        carrierFrequencyHz: Int,
        pattern: IntArray,
    ) {
        failure?.let { throw it }
        transmissions += Transmission(carrierFrequencyHz, pattern.copyOf())
    }

    fun lastTransmission(): Transmission = transmissions.last()
}
