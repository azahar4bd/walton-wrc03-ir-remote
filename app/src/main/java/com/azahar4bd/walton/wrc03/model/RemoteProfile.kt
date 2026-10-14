package com.azahar4bd.walton.wrc03.model

import com.azahar4bd.walton.wrc03.ir.NecAddressMode
import com.azahar4bd.walton.wrc03.ir.NecProtocol

/**
 * How much we trust a single [code].
 *
 * The whole Walton WRC-03 table is unverified (see README), but the distinction still
 * matters: some values come from published NEC code sheets, the rest are placeholders
 * somebody has to confirm with a real WRC-03 handset.
 */
enum class CodeConfidence(
    val labelBn: String,
) {
    /** Published in a community / datasheet code list for a 6122-family handset. */
    DOCUMENTED("তথ্যসূত্র"),

    /** Best guess: right protocol, unknown value. Needs capturing from a real remote. */
    GUESS("অনুমান"),
}

/** One button's IR payload inside a profile. */
data class IrCode(
    val command: Int,
    val confidence: CodeConfidence,
)

/**
 * A complete button -> code table for one IR handset.
 *
 * @property verified true only once somebody has confirmed every key against a real
 *                    Walton WRC-03 TV. Nothing in this repository is verified yet.
 */
data class RemoteProfile(
    val id: String,
    val nameBn: String,
    val address: Int,
    val addressMode: NecAddressMode,
    val carrierHz: Int = NecProtocol.CARRIER_FREQUENCY_HZ,
    val commands: Map<RemoteCommand, IrCode>,
    val sourceNoteBn: String,
    val verified: Boolean = false,
) {
    fun codeFor(command: RemoteCommand): IrCode? = commands[command]
}
