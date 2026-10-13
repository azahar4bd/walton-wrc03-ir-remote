package com.azahar4bd.walton.wrc03.ir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IrAvailabilityDetectorTest {
    private val broadRange = listOf(CarrierRange(30_000, 60_000))
    private val narrowRange = listOf(CarrierRange(56_000, 60_000))

    @Test
    fun aMissingSystemServiceIsReportedAsNotSupported() {
        assertEquals(IrAvailability.NotSupported, IrAvailabilityDetector.detect(null))
    }

    @Test
    fun aPhoneWithoutAnEmitterIsReportedClearly() {
        val hardware = FakeIrHardware(hasEmitter = false)
        assertEquals(IrAvailability.NoEmitter, IrAvailabilityDetector.detect(hardware))
    }

    @Test
    fun anEmitterThatDoesNotAdvertiseBandsIsAssumedToCover38KHz() {
        val hardware = FakeIrHardware(hasEmitter = true, carrierRanges = null)
        val state = IrAvailabilityDetector.detect(hardware)
        assertTrue(state is IrAvailability.Ready)
        assertTrue((state as IrAvailability.Ready).ranges.isEmpty())
        assertEquals(NecProtocol.CARRIER_FREQUENCY_HZ, state.targetFrequencyHz)
    }

    @Test
    fun anEmptyRangeListIsAlsoTreatedAsUnknown() {
        val hardware = FakeIrHardware(hasEmitter = true, carrierRanges = emptyList())
        assertTrue(IrAvailabilityDetector.detect(hardware) is IrAvailability.Ready)
    }

    @Test
    fun aBlasterCovering38KHzIsReady() {
        val hardware = FakeIrHardware(hasEmitter = true, carrierRanges = broadRange)
        assertEquals(IrAvailability.Ready(broadRange, 38_000), IrAvailabilityDetector.detect(hardware))
    }

    @Test
    fun aBlasterThatOnlyDoes56KHzCannotSendNec() {
        val hardware = FakeIrHardware(hasEmitter = true, carrierRanges = narrowRange)
        val state = IrAvailabilityDetector.detect(hardware)
        assertTrue(state is IrAvailability.CarrierUnsupported)
        assertEquals(narrowRange, (state as IrAvailability.CarrierUnsupported).ranges)
    }

    @Test
    fun theRangeCheckUsesTheProfileCarrier() {
        val hardware = FakeIrHardware(hasEmitter = true, carrierRanges = narrowRange)
        assertTrue(IrAvailabilityDetector.detect(hardware, 57_000) is IrAvailability.Ready)
    }
}
