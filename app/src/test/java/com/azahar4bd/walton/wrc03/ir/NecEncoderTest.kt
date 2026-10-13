package com.azahar4bd.walton.wrc03.ir

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NEC (6122 family) frame encoding.
 *
 * The four expected patterns below are not hand written: they are produced by
 * `tools/verify_nec_reference.py`, which re-derives them from the published protocol
 * timings and decodes them back. Regenerate with `--emit` after changing any constant.
 */
class NecEncoderTest {
    // power_standard_addr00: address=0x0000 command=0x45 mode=STANDARD
private val POWER_STANDARD_ADDR00 = intArrayOf(
            9000, 4500, 560, 560, 560, 560, 560, 560,
            560, 560, 560, 560, 560, 560, 560, 560,
            560, 560, 560, 1690, 560, 1690, 560, 1690,
            560, 1690, 560, 1690, 560, 1690, 560, 1690,
            560, 1690, 560, 1690, 560, 560, 560, 1690,
            560, 560, 560, 560, 560, 560, 560, 1690,
            560, 560, 560, 560, 560, 1690, 560, 560,
            560, 1690, 560, 1690, 560, 1690, 560, 560,
            560, 1690, 560,
)

// digit_1_standard_addr00: address=0x0000 command=0x0C mode=STANDARD
private val DIGIT_1_STANDARD_ADDR00 = intArrayOf(
            9000, 4500, 560, 560, 560, 560, 560, 560,
            560, 560, 560, 560, 560, 560, 560, 560,
            560, 560, 560, 1690, 560, 1690, 560, 1690,
            560, 1690, 560, 1690, 560, 1690, 560, 1690,
            560, 1690, 560, 560, 560, 560, 560, 1690,
            560, 1690, 560, 560, 560, 560, 560, 560,
            560, 560, 560, 1690, 560, 1690, 560, 560,
            560, 560, 560, 1690, 560, 1690, 560, 1690,
            560, 1690, 560,
)

// mute_extended_addr_00ff: address=0x00FF command=0x47 mode=EXTENDED
private val MUTE_EXTENDED_ADDR_00FF = intArrayOf(
            9000, 4500, 560, 1690, 560, 1690, 560, 1690,
            560, 1690, 560, 1690, 560, 1690, 560, 1690,
            560, 1690, 560, 560, 560, 560, 560, 560,
            560, 560, 560, 560, 560, 560, 560, 560,
            560, 560, 560, 1690, 560, 1690, 560, 1690,
            560, 560, 560, 560, 560, 560, 560, 1690,
            560, 560, 560, 560, 560, 560, 560, 560,
            560, 1690, 560, 1690, 560, 1690, 560, 560,
            560, 1690, 560,
)

// volume_up_extended_addr_807f: address=0x807F command=0x15 mode=EXTENDED
private val VOLUME_UP_EXTENDED_ADDR_807F = intArrayOf(
            9000, 4500, 560, 1690, 560, 1690, 560, 1690,
            560, 1690, 560, 1690, 560, 1690, 560, 1690,
            560, 560, 560, 560, 560, 560, 560, 560,
            560, 560, 560, 560, 560, 560, 560, 560,
            560, 1690, 560, 1690, 560, 560, 560, 1690,
            560, 560, 560, 1690, 560, 560, 560, 560,
            560, 560, 560, 560, 560, 1690, 560, 560,
            560, 1690, 560, 560, 560, 1690, 560, 1690,
            560, 1690, 560,
)

private val REPEAT_FRAME = intArrayOf(
            9000, 2250, 560,
)

    @Test
    fun aFrameIs67EntriesLong() {
        assertEquals(67, NecEncoder.encodeFrame(0x00, 0x45).size)
        assertEquals(NecProtocol.FRAME_PATTERN_LENGTH, NecEncoder.encodeFrame(0x00, 0x45).size)
    }

    @Test
    fun theCarrierIs38KHz() {
        assertEquals(38_000, NecProtocol.CARRIER_FREQUENCY_HZ)
    }

    @Test
    fun theFrameOpensWithALeaderBurstAndClosesWithATrailingBurst() {
        val pattern = NecEncoder.encodeFrame(0x00, 0x45)
        assertEquals(NecProtocol.LEADER_MARK_US, pattern[0])
        assertEquals(NecProtocol.LEADER_SPACE_US, pattern[1])
        assertEquals(NecProtocol.TRAILING_MARK_US, pattern[pattern.lastIndex])
    }

    @Test
    fun everyDataBitIsABurstFollowedByAShortOrLongSpace() {
        val pattern = NecEncoder.encodeFrame(0x00, 0x45)
        for (index in 2 until pattern.lastIndex step 2) {
            assertEquals("index $index should be a burst", NecProtocol.BIT_MARK_US, pattern[index])
            val space = pattern[index + 1]
            assertTrue(
                "index ${index + 1} should be a 0 or 1 space but was $space",
                space == NecProtocol.ZERO_SPACE_US || space == NecProtocol.ONE_SPACE_US,
            )
        }
    }

    @Test
    fun bytesGoOutLeastSignificantBitFirst() {
        // command 0x01: its lowest bit is a 1, so the first command space is the long one.
        val pattern = NecEncoder.encodeFrame(0x00, 0x01)
        assertEquals(NecProtocol.ONE_SPACE_US, pattern[35])
        assertEquals(NecProtocol.ZERO_SPACE_US, pattern[37])
    }

    @Test
    fun aStandardAddressIsFollowedByItsInverse() {
        val pattern = NecEncoder.encodeFrame(0x00, 0x45)
        for (bit in 0 until 8) {
            assertEquals(NecProtocol.ZERO_SPACE_US, pattern[3 + bit * 2]) // 0x00
            assertEquals(NecProtocol.ONE_SPACE_US, pattern[19 + bit * 2]) // ~0x00 = 0xFF
        }
    }

    @Test
    fun anExtendedAddressSendsItsLowByteFirst() {
        val frame = NecFrame(0x00FF, 0x47, NecAddressMode.EXTENDED)
        assertArrayEquals(intArrayOf(0xFF, 0x00, 0x47, 0xB8), frame.wireBytes())

        val pattern = NecEncoder.encodeFrame(frame)
        assertEquals(NecProtocol.ONE_SPACE_US, pattern[3]) // lowest bit of 0xFF
        assertEquals(NecProtocol.ZERO_SPACE_US, pattern[19]) // lowest bit of 0x00
    }

    @Test
    fun theRepeatFrameIsNineMillisecondsTwoPointTwoFiveAndABurst() {
        val repeat = NecEncoder.encodeRepeatFrame()
        assertArrayEquals(intArrayOf(9_000, 2_250, 560), repeat)
        assertEquals(NecProtocol.REPEAT_PATTERN_LENGTH, repeat.size)
    }

    @Test
    fun evenTheLongestFrameFitsInsideTheRepeatPeriod() {
        val worstCase = NecEncoder.encodeFrame(0xFFFF, 0xFF, NecAddressMode.EXTENDED)
        val bestCase = NecEncoder.encodeFrame(0x0000, 0x00, NecAddressMode.EXTENDED)
        assertTrue(
            "${NecEncoder.durationUs(worstCase)}us must stay under ${NecProtocol.REPEAT_PERIOD_US}us",
            NecEncoder.durationUs(worstCase) < NecProtocol.REPEAT_PERIOD_US,
        )
        assertTrue(NecEncoder.durationUs(bestCase) > 0)
    }

    @Test
    fun thePowerFrameMatchesTheReferenceVector() {
        assertArrayEquals(POWER_STANDARD_ADDR00, NecEncoder.encodeFrame(0x00, 0x45))
    }

    @Test
    fun theDigitOneFrameMatchesTheReferenceVector() {
        assertArrayEquals(DIGIT_1_STANDARD_ADDR00, NecEncoder.encodeFrame(0x00, 0x0C))
    }

    @Test
    fun theMuteFrameMatchesTheReferenceVector() {
        assertArrayEquals(
            MUTE_EXTENDED_ADDR_00FF,
            NecEncoder.encodeFrame(0x00FF, 0x47, NecAddressMode.EXTENDED),
        )
    }

    @Test
    fun theVolumeUpFrameMatchesTheReferenceVector() {
        assertArrayEquals(
            VOLUME_UP_EXTENDED_ADDR_807F,
            NecEncoder.encodeFrame(0x807F, 0x15, NecAddressMode.EXTENDED),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun theCommandMustFitInOneByte() {
        NecEncoder.encodeFrame(0x00, 0x100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aStandardAddressMustFitInOneByte() {
        NecEncoder.encodeFrame(0x100, 0x45, NecAddressMode.STANDARD)
    }

    @Test
    fun anExtendedAddressMayUseSixteenBits() {
        assertEquals(67, NecEncoder.encodeFrame(0xFFFF, 0xFF, NecAddressMode.EXTENDED).size)
    }
}
