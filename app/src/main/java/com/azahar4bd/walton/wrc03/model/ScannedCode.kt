package com.azahar4bd.walton.wrc03.model

import com.azahar4bd.walton.wrc03.ir.NecAddressMode
import com.azahar4bd.walton.wrc03.ir.NecProtocol
import com.azahar4bd.walton.wrc03.ir.NecFrame

/**
 * One button code the user discovered with the in-app code scanner (see package `scan`).
 *
 * The shipped table is unverified, so the honest way to get a working remote is to sweep
 * candidate NEC frames at the TV and keep whatever made it react. A record therefore has
 * to remember the *whole* frame — address block, address convention and command byte —
 * because a code found under address `0x00FF` means nothing to a profile using `0x00`.
 *
 * Pure Kotlin (no Android types) so serialisation and profile merging stay unit testable.
 */
data class ScannedCode(
    val command: RemoteCommand,
    val address: Int,
    val addressMode: NecAddressMode,
    val code: Int,
    val carrierHz: Int = NecProtocol.CARRIER_FREQUENCY_HZ,
    val foundAtEpochMs: Long = 0L,
) {
    init {
        when (addressMode) {
            NecAddressMode.STANDARD ->
                require(address in 0x00..0xFF) {
                    "STANDARD address must fit in one byte, got 0x${address.toString(16)}"
                }

            NecAddressMode.EXTENDED ->
                require(address in 0x0000..0xFFFF) {
                    "EXTENDED address must fit in 16 bits, got 0x${address.toString(16)}"
                }
        }
        require(carrierHz > 0) { "carrier must be positive" }
        require(code in 0x00..0xFF) { "code must fit in one byte, got 0x${code.toString(16)}" }
    }

    /** A record only overrides a profile that beams the very same address block. */
    fun appliesTo(profile: RemoteProfile): Boolean =
        NecFrame(profile.address, 0, profile.addressMode).wireBytes().take(2) ==
            NecFrame(address, 0, addressMode).wireBytes().take(2) &&
            profile.carrierHz == carrierHz

    /** Stable key per button + on-wire address + carrier; v0.2 command-only keys migrate. */
    fun storageKey(): String {
        val bytes = NecFrame(address, 0, addressMode).wireBytes().take(2)
        return "${command.name}:${bytes[0]}:${bytes[1]}:$carrierHz"
    }

    fun toIrCode(): IrCode = IrCode(code, CodeConfidence.SCANNED)

    /** `"address|MODE|code|carrier|timestamp"` — decimal, `|` separated, one line. */
    fun serialize(): String =
        listOf(
            address.toString(),
            addressMode.name,
            code.toString(),
            carrierHz.toString(),
            foundAtEpochMs.toString(),
        ).joinToString(SEPARATOR)

    fun addressHex(): String =
        if (addressMode == NecAddressMode.STANDARD) {
            "0x${address.toString(16).uppercase().padStart(2, '0')}"
        } else {
            "0x${address.toString(16).uppercase().padStart(4, '0')}"
        }

    fun codeHex(): String = "0x${code.toString(16).uppercase().padStart(2, '0')}"

    companion object {
        private const val SEPARATOR = "|"
        private const val FIELD_COUNT = 5

        /** Inverse of [serialize]. Returns null for anything malformed instead of throwing. */
        fun parse(
            command: RemoteCommand,
            raw: String,
        ): ScannedCode? {
            val fields = raw.split(SEPARATOR)
            if (fields.size != FIELD_COUNT) return null

            val address = fields[0].toIntOrNull() ?: return null
            val mode = NecAddressMode.entries.firstOrNull { it.name == fields[1] } ?: return null
            val code = fields[2].toIntOrNull() ?: return null
            val carrier = fields[3].toIntOrNull() ?: return null
            val timestamp = fields[4].toLongOrNull() ?: return null

            if (code !in 0x00..0xFF) return null
            if (carrier <= 0) return null
            val addressRange = if (mode == NecAddressMode.STANDARD) 0x00..0xFF else 0x0000..0xFFFF
            if (address !in addressRange) return null

            return ScannedCode(command, address, mode, code, carrier, timestamp)
        }
    }
}
