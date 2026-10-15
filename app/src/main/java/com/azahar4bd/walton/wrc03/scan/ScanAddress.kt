package com.azahar4bd.walton.wrc03.scan

import com.azahar4bd.walton.wrc03.data.RemoteProfiles
import com.azahar4bd.walton.wrc03.ir.NecAddressMode
import com.azahar4bd.walton.wrc03.ir.NecFrame
import com.azahar4bd.walton.wrc03.model.CodeConfidence
import com.azahar4bd.walton.wrc03.model.RemoteCommand
import com.azahar4bd.walton.wrc03.model.RemoteProfile

/**
 * One NEC address block the scanner can sweep.
 *
 * Receivers cannot tell `0xFF` in [NecAddressMode.STANDARD] apart from `0x00FF` in
 * EXTENDED — both put `FF 00` on the wire — so blocks are compared (and de-duplicated)
 * by their wire bytes rather than by their numeric value.
 */
data class ScanAddress(
    val address: Int,
    val mode: NecAddressMode,
    val noteBn: String,
) {
    /** The two address bytes exactly as they leave the IR LED. */
    fun wireBytes(): List<Int> = NecFrame(address, 0x00, mode).wireBytes().take(2).toList()

    fun addressHex(): String =
        if (mode == NecAddressMode.STANDARD) {
            "0x${address.toString(16).uppercase().padStart(2, '0')}"
        } else {
            "0x${address.toString(16).uppercase().padStart(4, '0')}"
        }
}

/** Address blocks worth trying, in "most likely first" order. */
object ScanAddressBlocks {
    /** The distinct blocks the shipped profiles use. */
    val PROFILE: List<ScanAddress> =
        distinctOnWire(RemoteProfiles.ALL.map { profile ->
            ScanAddress(profile.address, profile.addressMode, profile.nameBn)
        })

    /**
     * [PROFILE] plus the byte-swapped / zero variants that 6122-family handsets are
     * commonly built with. Duplicates (same two wire bytes) are dropped.
     */
    val COMMON: List<ScanAddress> =
        distinctOnWire(
            PROFILE +
                listOf(
                    ScanAddress(0xFF, NecAddressMode.STANDARD, "0x00-এর উল্টো মান"),
                    ScanAddress(0x0000, NecAddressMode.EXTENDED, "১৬-বিট শূন্য ঠিকানা"),
                    ScanAddress(0xFF00, NecAddressMode.EXTENDED, "0x00FF-এর বাইট-উল্টো"),
                    ScanAddress(0x7F80, NecAddressMode.EXTENDED, "0x807F-এর বাইট-উল্টো"),
                ),
        )

    /** The block of whatever profile is currently selected on the main screen. */
    fun of(profile: RemoteProfile): ScanAddress =
        ScanAddress(profile.address, profile.addressMode, profile.nameBn)

    fun distinctOnWire(blocks: List<ScanAddress>): List<ScanAddress> =
        blocks.distinctBy { it.wireBytes() }
}

/** Command bytes the scanner can walk through. */
object ScanCodeSets {
    /** Every value one NEC command byte can hold. */
    val FULL_BYTE: List<Int> = (0x00..0xFF).toList()

    /**
     * Only the values published in the 6122-family code sheet, taken straight from the
     * shipped table so the two can never drift apart.
     */
    val DOCUMENTED_SHEET: List<Int> =
        RemoteCommand.entries
            .mapNotNull { command ->
                RemoteProfiles.DEFAULT
                    .codeFor(command)
                    ?.takeIf { it.confidence == CodeConfidence.DOCUMENTED }
                    ?.command
            }.distinct()
}
