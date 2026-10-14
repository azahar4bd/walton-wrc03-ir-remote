package com.azahar4bd.walton.wrc03.scan

import com.azahar4bd.walton.wrc03.ir.NecAddressMode
import com.azahar4bd.walton.wrc03.ir.NecFrame
import com.azahar4bd.walton.wrc03.ir.NecProtocol
import com.azahar4bd.walton.wrc03.model.RemoteCommand

/**
 * A single guess the scanner beams at the TV: one complete NEC frame.
 *
 * @property commandByte the 8 bit data code, `0x00..0xFF`.
 */
data class ScanCandidate(
    val address: Int,
    val addressMode: NecAddressMode,
    val commandByte: Int,
    val carrierHz: Int = NecProtocol.CARRIER_FREQUENCY_HZ,
) {
    init {
        require(commandByte in 0x00..0xFF) {
            "command byte must fit in one byte, got 0x${commandByte.toString(16)}"
        }
    }

    fun frame(): NecFrame = NecFrame(address, commandByte, addressMode)

    /** The whole frame as it goes out, used to spot two candidates that are identical. */
    fun wireBytes(): List<Int> = frame().wireBytes().toList()

    fun addressHex(): String =
        if (addressMode == NecAddressMode.STANDARD) {
            "0x${address.toString(16).uppercase().padStart(2, '0')}"
        } else {
            "0x${address.toString(16).uppercase().padStart(4, '0')}"
        }

    fun codeHex(): String = "0x${commandByte.toString(16).uppercase().padStart(2, '0')}"

    fun modeBn(): String = if (addressMode == NecAddressMode.STANDARD) "৮-বিট" else "১৬-বিট"
}

/**
 * The ordered list of candidates for one button.
 *
 * Candidates are grouped address-block first (all command bytes of block one, then block
 * two …) because a set that reacts to any code of a block will react to the whole block,
 * which makes it easy for the user to follow what is being tried.
 */
data class ScanPlan(
    val target: RemoteCommand,
    val presetTitleBn: String,
    val candidates: List<ScanCandidate>,
) {
    val size: Int get() = candidates.size

    val isEmpty: Boolean get() = candidates.isEmpty()

    fun candidateAt(index: Int): ScanCandidate? = candidates.getOrNull(index)
}

/** Builds (and de-duplicates) a [ScanPlan] from an address list and a command byte list. */
object ScanPlanBuilder {
    fun build(
        target: RemoteCommand,
        addresses: List<ScanAddress>,
        commandBytes: List<Int>,
        presetTitleBn: String,
        carrierHz: Int = NecProtocol.CARRIER_FREQUENCY_HZ,
    ): ScanPlan {
        val candidates =
            addresses.flatMap { block ->
                commandBytes.map { byte ->
                    ScanCandidate(block.address, block.mode, byte, carrierHz)
                }
            }.distinctBy { it.wireBytes() }

        return ScanPlan(target, presetTitleBn, candidates)
    }
}
