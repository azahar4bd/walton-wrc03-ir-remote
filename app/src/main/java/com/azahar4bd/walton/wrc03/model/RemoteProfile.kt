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

    /** Found on this very TV by the in-app code scanner — the user saw the set react. */
    SCANNED("স্ক্যানে পাওয়া"),
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

    /**
     * Same profile, but with the codes the user discovered by scanning layered on top.
     *
     * Only records that were found under *this* address block apply (see
     * [ScannedCode.appliesTo]); the rest are ignored. When several records name the same
     * button, the newest one wins. Nothing is verified in bulk: a profile stays
     * `verified = false` until the shipped table itself has been confirmed.
     */
    fun withScannedCodes(scanned: List<ScannedCode>): RemoteProfile {
        val applicable = scanned.filter { it.appliesTo(this) }
        if (applicable.isEmpty()) return this

        val merged = commands.toMutableMap()
        applicable.sortedBy { it.foundAtEpochMs }.forEach { record ->
            merged[record.command] = record.toIrCode()
        }
        return copy(commands = merged)
    }

    /** How many buttons of this profile are driven by a scanned (user confirmed) code. */
    fun scannedCount(): Int = commands.values.count { it.confidence == CodeConfidence.SCANNED }
}
