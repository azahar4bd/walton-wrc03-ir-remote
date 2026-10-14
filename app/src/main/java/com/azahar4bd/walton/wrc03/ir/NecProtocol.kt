package com.azahar4bd.walton.wrc03.ir

/**
 * Timing constants of the NEC infra-red protocol, the family used by the uPD6122 /
 * PT2222 class of remote transmitter ICs ("6122" in the profile names below).
 *
 * A frame is pulse-distance encoded on a 38 kHz carrier:
 *
 *   * 9 ms leading burst + 4.5 ms space (leader)
 *   * 32 bits, each byte sent least-significant-bit first:
 *       [address / custom code, 16 bit] [data code, 16 bit]
 *   * every bit is a 562.5 us burst followed by either a 562.5 us space (logical 0)
 *     or a ~1.69 ms space (logical 1)
 *   * one final 562.5 us burst closes the frame
 *
 * While a key is held down the transmitter sends a repeat frame (9 ms burst,
 * 2.25 ms space, 562.5 us burst) every 108 ms instead of the full frame.
 *
 * The numbers are mirrored by `tools/verify_nec_reference.py`, which re-derives the
 * expected test vectors from the spec and checks them in CI.
 */
object NecProtocol {
    /** Carrier the TV receiver demodulates. Most 6122-family remotes use 38 kHz. */
    const val CARRIER_FREQUENCY_HZ = 38_000

    const val LEADER_MARK_US = 9_000
    const val LEADER_SPACE_US = 4_500

    const val BIT_MARK_US = 560
    const val ZERO_SPACE_US = 560
    const val ONE_SPACE_US = 1_690

    const val TRAILING_MARK_US = 560

    const val REPEAT_SPACE_US = 2_250
    const val REPEAT_PERIOD_US = 108_000

    /** 2 leader entries + 32 bits * 2 entries + 1 trailing burst. */
    const val FRAME_PATTERN_LENGTH = 67

    /** 9 ms burst + 2.25 ms space + 562.5 us burst. */
    const val REPEAT_PATTERN_LENGTH = 3
}

/**
 * NEC carries a 16 bit address block before the command, but what goes on the wire
 * differs between the two conventions:
 *
 * * [STANDARD]  - 8 bit address followed by its bitwise inverse (0x00 -> 0x00 0xFF)
 * * [EXTENDED]  - a full 16 bit address, low byte first (0x00FF -> 0xFF 0x00)
 *
 * A receiver cannot tell the two apart (0x00FF extended looks exactly like 0xFF
 * standard), which is why each profile has to state which one it uses.
 */
enum class NecAddressMode {
    STANDARD,
    EXTENDED,
}

/**
 * One NEC frame: the 16 bit address block plus the 8 bit command.
 *
 * @property address 0x00..0xFF for [NecAddressMode.STANDARD], 0x0000..0xFFFF for EXTENDED.
 * @property command 0x00..0xFF.
 */
data class NecFrame(
    val address: Int,
    val command: Int,
    val mode: NecAddressMode = NecAddressMode.STANDARD,
) {
    init {
        when (mode) {
            NecAddressMode.STANDARD ->
                require(address in 0x00..0xFF) {
                    "STANDARD address must fit in one byte, got 0x${address.toString(16)}"
                }

            NecAddressMode.EXTENDED ->
                require(address in 0x0000..0xFFFF) {
                    "EXTENDED address must fit in 16 bits, got 0x${address.toString(16)}"
                }
        }
        require(command in 0x00..0xFF) {
            "command must fit in one byte, got 0x${command.toString(16)}"
        }
    }

    /** The four bytes as they appear on the wire, in transmission order. */
    fun wireBytes(): IntArray =
        when (mode) {
            NecAddressMode.STANDARD -> intArrayOf(address, address.inv() and 0xFF, command, command.inv() and 0xFF)
            NecAddressMode.EXTENDED -> intArrayOf(address and 0xFF, (address shr 8) and 0xFF, command, command.inv() and 0xFF)
        }
}

/**
 * Turns [NecFrame]s into the alternating "on / off, in microseconds" patterns that
 * `android.hardware.ConsumerIrManager.transmit()` expects (index 0 = burst on).
 */
object NecEncoder {
    fun encodeFrame(frame: NecFrame): IntArray = encodeFrame(frame.address, frame.command, frame.mode)

    fun encodeFrame(
        address: Int,
        command: Int,
        mode: NecAddressMode = NecAddressMode.STANDARD,
    ): IntArray {
        val frame = NecFrame(address, command, mode)
        val pattern = IntArray(NecProtocol.FRAME_PATTERN_LENGTH)
        var index = 0

        pattern[index++] = NecProtocol.LEADER_MARK_US
        pattern[index++] = NecProtocol.LEADER_SPACE_US

        for (byte in frame.wireBytes()) {
            for (bit in 0 until 8) {
                val isSet = (byte shr bit) and 1 == 1 // least significant bit first
                pattern[index++] = NecProtocol.BIT_MARK_US
                pattern[index++] = if (isSet) NecProtocol.ONE_SPACE_US else NecProtocol.ZERO_SPACE_US
            }
        }

        pattern[index] = NecProtocol.TRAILING_MARK_US
        return pattern
    }

    /** Repeat frame, sent every [NecProtocol.REPEAT_PERIOD_US] while a key is held. */
    fun encodeRepeatFrame(): IntArray =
        intArrayOf(
            NecProtocol.LEADER_MARK_US,
            NecProtocol.REPEAT_SPACE_US,
            NecProtocol.TRAILING_MARK_US,
        )

    /** Total air time of a pattern in microseconds, useful for repeat scheduling. */
    fun durationUs(pattern: IntArray): Int = pattern.sum()
}
