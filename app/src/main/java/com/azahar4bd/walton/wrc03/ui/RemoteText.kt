package com.azahar4bd.walton.wrc03.ui

import com.azahar4bd.walton.wrc03.ir.IrAvailability
import com.azahar4bd.walton.wrc03.ir.NecAddressMode
import com.azahar4bd.walton.wrc03.ir.IrSendResult
import com.azahar4bd.walton.wrc03.ir.NecProtocol
import com.azahar4bd.walton.wrc03.model.CodeConfidence
import com.azahar4bd.walton.wrc03.model.RemoteCommand
import com.azahar4bd.walton.wrc03.model.RemoteProfile
import com.azahar4bd.walton.wrc03.model.ScannedCode
import com.azahar4bd.walton.wrc03.scan.CodeScanEngine
import com.azahar4bd.walton.wrc03.scan.ScanCandidate
import com.azahar4bd.walton.wrc03.scan.ScanPlan
import com.azahar4bd.walton.wrc03.scan.ScanPreset
import com.azahar4bd.walton.wrc03.scan.ScanSnapshot
import com.azahar4bd.walton.wrc03.scan.ScanState

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
        val mode = if (profile.addressMode == NecAddressMode.STANDARD) "৮-বিট" else "১৬-বিট"
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

        return "$header\n\n$body\n\n" +
            "(?) বা \"অনুমান\" মানে এই কোড কোনো প্রকাশিত তালিকায় পাওয়া যায়নি। " +
            "\"${CodeConfidence.SCANNED.labelBn}\" মানে কোড স্ক্যান মোডে টিভি সাড়া দিয়েছে।"
    }

    // --- code scan mode ------------------------------------------------------

    /** How wide the sweep is, and roughly how long it will take. */
    fun scanPlanSummary(
        plan: ScanPlan,
        intervalMs: Long,
    ): String {
        if (plan.isEmpty) return "এই স্ক্যানে পাঠানোর মতো কোনো কোড নেই।"

        val pulses = CodeScanEngine.PULSES_PER_CANDIDATE
        val perCandidate = intervalMs + ((pulses - 1) * CodeScanEngine.PULSE_GAP_MS)
        return "প্রার্থী কোড ${plan.size}টি · প্রতিটি $pulses বার যাবে · " +
            "বিরতি ${secondsBn(intervalMs)} সেকেন্ড · " +
            "আনুমানিক সময় ${durationBn(plan.size * perCandidate)}"
    }

    /** "12 / 54 পাঠানো হয়েছে · বাকি ~50 সেকেন্ড" */
    fun scanProgress(snapshot: ScanSnapshot): String =
        "${snapshot.sentCount} / ${snapshot.total} পাঠানো হয়েছে · বাকি ~${durationBn(snapshot.remainingMs)}"

    fun scanStateLine(snapshot: ScanSnapshot): String =
        when (snapshot.state) {
            ScanState.FAILED -> snapshot.failureMessageBn ?: snapshot.state.labelBn
            ScanState.PAUSED ->
                if (snapshot.found.isEmpty()) {
                    snapshot.state.labelBn
                } else {
                    "${snapshot.state.labelBn} · কোড পাওয়া গেছে ${snapshot.found.size}টি"
                }

            ScanState.IDLE, ScanState.RUNNING, ScanState.FINISHED -> snapshot.state.labelBn
        }

    /** The frame currently in the air, e.g. "ঠিকানা 0x00FF (১৬-বিট) · কোড 0x45". */
    fun scanCandidateLine(candidate: ScanCandidate?): String =
        candidate?.let { "ঠিকানা ${it.addressHex()} (${it.modeBn()}) · কোড ${it.codeHex()}" }
            ?: "এখনো কোনো কোড পাঠানো হয়নি"

    fun scanFoundLine(record: ScannedCode): String =
        "${record.command.labelBn} → ঠিকানা ${record.addressHex()} · কোড ${record.codeHex()}"

    fun scanFoundSheet(records: List<ScannedCode>): String =
        if (records.isEmpty()) {
            "এখনো কোনো কোড পাওয়া যায়নি। টিভি সাড়া দিলে সঙ্গে সঙ্গে \"টিভি সাড়া দিয়েছে\" বাটন চাপুন।"
        } else {
            records.joinToString("\n") { scanFoundLine(it) }
        }

    /** Main-screen line: how much of the table the user has already confirmed. */
    fun scannedCodesSummary(count: Int): String =
        if (count <= 0) {
            "স্ক্যানে এখনো কোনো কোড পাওয়া যায়নি।"
        } else {
            "স্ক্যানে পাওয়া $countটি কোড সংরক্ষিত আছে — ওই বাটনগুলোতে ✓ চিহ্ন দেখা যায়।"
        }

    fun scanStartPauseLabel(state: ScanState): String =
        when (state) {
            ScanState.IDLE -> "স্ক্যান শুরু করুন"
            ScanState.RUNNING -> "পজ করুন"
            ScanState.PAUSED -> "আবার শুরু"
            ScanState.FINISHED, ScanState.FAILED -> "নতুন করে শুরু"
        }

    fun scanPresetLabel(preset: ScanPreset): String = "${preset.titleBn} — ${preset.detailBn}"

    /** "1.2", for a millisecond count that is always a multiple of 100. */
    fun secondsBn(millis: Long): String = "${millis / 1000}.${(millis % 1000) / 100}"

    /** Whole seconds / minutes, rounded up: "45 সেকেন্ড", "1 মিনিট 5 সেকেন্ড". */
    fun durationBn(totalMs: Long): String {
        val seconds = (totalMs + 999) / 1000
        val minutes = seconds / 60
        val rest = seconds % 60
        return when {
            minutes <= 0 -> "$rest সেকেন্ড"
            rest == 0L -> "$minutes মিনিট"
            else -> "$minutes মিনিট $rest সেকেন্ড"
        }
    }

    private fun RemoteProfile.addressHex(): String =
        if (addressMode == NecAddressMode.STANDARD) {
            "0x${address.toString(16).uppercase().padStart(2, '0')}"
        } else {
            "0x${address.toString(16).uppercase().padStart(4, '0')}"
        }
}

/** Convenience for the caller that already knows the command it pressed. */
fun RemoteCommand.isMappedIn(profile: RemoteProfile): Boolean = profile.codeFor(this) != null
