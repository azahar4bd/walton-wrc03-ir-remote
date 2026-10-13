package com.azahar4bd.walton.wrc03.ui

import com.azahar4bd.walton.wrc03.ir.IrAvailability
import com.azahar4bd.walton.wrc03.ir.IrSendResult
import com.azahar4bd.walton.wrc03.ir.NecProtocol
import com.azahar4bd.walton.wrc03.model.RemoteCommand
import com.azahar4bd.walton.wrc03.model.RemoteProfile

/**
 * All user facing text that is built at runtime (status lines, the code sheet).
 *
 * Kept free of Android types so the wording is covered by unit tests.
 */
object RemoteText {
    fun statusTitle(state: IrAvailability): String =
        when (state) {
            IrAvailability.NotSupported -> "এই ডিভাইসে IR সার্ভিস নেই"
            IrAvailability.NoEmitter -> "এই ফোনে IR blaster নেই"
            is IrAvailability.CarrierUnsupported -> "38 kHz ক্যারিয়ার সমর্থিত নয়"
            is IrAvailability.Ready -> "IR blaster প্রস্তুত"
        }

    fun statusDetail(state: IrAvailability): String =
        when (state) {
            IrAvailability.NotSupported ->
                "ConsumerIrManager সিস্টেম সার্ভিস পাওয়া যায়নি। " +
                    "এই ফোন থেকে IR সিগন্যাল পাঠানো সম্ভব নয়।"

            IrAvailability.NoEmitter ->
                "ConsumerIrManager আছে, কিন্তু hasIrEmitter() false রিটার্ন করেছে — " +
                    "মানে ফোনে কোনো IR LED নেই। Xiaomi/Redmi, Huawei/Honor, OnePlus, Realme-এর " +
                    "কিছু নির্দিষ্ট মডেলে IR থাকে; Pixel বা বেশিরভাগ নতুন ফোনে থাকে না। " +
                    "বাটনগুলো দেখা যাবে, কিন্তু সিগন্যাল পাঠানো যাবে না।"

            is IrAvailability.CarrierUnsupported ->
                "ফোনে IR আছে, তবে সমর্থিত ক্যারিয়ার রেঞ্জ (${state.ranges.joinToString()}) " +
                    "${state.targetFrequencyHz} Hz কভার করে না।"

            is IrAvailability.Ready ->
                if (state.ranges.isEmpty()) {
                    "NEC (6122) সিগন্যাল ${state.targetFrequencyHz} Hz ক্যারিয়ারে পাঠানোর জন্য প্রস্তুত। " +
                        "ফোনটি কোনো নির্দিষ্ট ক্যারিয়ার রেঞ্জ জানায়নি।"
                } else {
                    "NEC (6122) সিগন্যাল ${state.targetFrequencyHz} Hz ক্যারিয়ারে পাঠানোর জন্য প্রস্তুত। " +
                        "সমর্থিত রেঞ্জ: ${state.ranges.joinToString()}"
                }
        }

    fun statusIcon(state: IrAvailability): String = if (state is IrAvailability.Ready) "✓" else "✕"

    /** One line under the keypad, e.g. "পাওয়ার → 0x45 · 38000 Hz · 67 পালস". */
    fun sentSummary(result: IrSendResult.Sent): String {
        val label = result.command.labelBn
        val hex = "0x${result.code.toString(16).uppercase().padStart(2, '0')}"
        val kind = if (result.isRepeat) "রিপিট" else "ফ্রেম"
        return "$label → $hex · ${result.carrierHz} Hz · ${result.pattern.size} পালস ($kind)"
    }

    fun profileSummary(profile: RemoteProfile): String {
        val mode = if (profile.addressMode == com.azahar4bd.walton.wrc03.ir.NecAddressMode.STANDARD) "৮-বিট" else "১৬-বিট"
        val hex = profile.addressHex()
        return "ঠিকানা $hex ($mode) · ক্যারিয়ার ${profile.carrierHz} Hz · " +
            if (profile.verified) "যাচাইকৃত" else "যাচাই হয়নি"
    }

    /** The full code sheet of a profile, shown in the "কোড তালিকা" dialog. */
    fun codeSheet(profile: RemoteProfile): String {
        val header =
            "প্রোফাইল: ${profile.nameBn}\n" +
                "${profileSummary(profile)}\n" +
                "উৎস: ${profile.sourceNoteBn}\n" +
                "প্রোটোকল: NEC (6122) · লিডার ${NecProtocol.LEADER_MARK_US}µs / ${NecProtocol.LEADER_SPACE_US}µs" +
                " · বার্স্ট ${NecProtocol.BIT_MARK_US}µs\n"

        val body =
            RemoteKeypadLayout.sections.joinToString("\n\n") { group ->
                val rows =
                    RemoteKeypadLayout.cells(group)
                        .filterNotNull()
                        .distinct()
                        .joinToString("\n") { command ->
                            val code = profile.codeFor(command)
                            val hex =
                                code?.let { "0x${it.command.toString(16).uppercase().padStart(2, '0')}" }
                                    ?: "—"
                            val confidence = code?.confidence?.labelBn ?: "নেই"
                            "  ${command.labelBn} ($hex) — $confidence"
                        }
                "[${group.titleBn}]\n$rows"
            }

        return "$header\n\n$body\n\n(?) বা \"অনুমান\" মানে এই কোড কোনো প্রকাশিত তালিকায় পাওয়া যায়নি।"
    }

    private fun RemoteProfile.addressHex(): String =
        if (addressMode == com.azahar4bd.walton.wrc03.ir.NecAddressMode.STANDARD) {
            "0x${address.toString(16).uppercase().padStart(2, '0')}"
        } else {
            "0x${address.toString(16).uppercase().padStart(4, '0')}"
        }
}

/** Convenience for the caller that already knows the command it pressed. */
fun RemoteCommand.isMappedIn(profile: RemoteProfile): Boolean = profile.codeFor(this) != null
