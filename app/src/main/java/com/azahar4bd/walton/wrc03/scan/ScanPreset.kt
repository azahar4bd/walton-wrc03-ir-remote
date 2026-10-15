package com.azahar4bd.walton.wrc03.scan

import com.azahar4bd.walton.wrc03.data.RemoteProfiles
import com.azahar4bd.walton.wrc03.model.CodeConfidence
import com.azahar4bd.walton.wrc03.model.RemoteCommand
import com.azahar4bd.walton.wrc03.model.RemoteProfile

/**
 * How wide a sweep the user wants.
 *
 * Presets select documented frames or a cross product of address blocks × bytes; the count
 * shown in the UI comes from [planFor], so nobody has to guess how long a sweep takes.
 */
enum class ScanPreset(
    val titleBn: String,
    val detailBn: String,
) {
    /** Documented codes at their own profile addresses, selected profile first. */
    QUICK(
        "দ্রুত স্ক্যান",
        "প্রতিটি প্রোফাইলের নিজস্ব তথ্যসূত্রভিত্তিক কোড",
    ),

    /** Same short code list, but also the byte-swapped / zero address blocks. */
    COMMON_BLOCKS(
        "প্রচলিত ঠিকানা ব্লক",
        "সাধারণ NEC ঠিকানা ব্লক × প্রকাশিত কোড শিটের মান",
    ),

    /** The long one: every command byte under each shipped address block. */
    PROFILE_FULL(
        "প্রোফাইল ঠিকানা, পুরো বাইট",
        "সব প্রোফাইল ঠিকানা × 0x00–0xFF (সব কোড)",
    ),

    /** Only the currently selected profile's address block, all 256 command bytes. */
    CURRENT_ADDRESS_FULL(
        "শুধু এই প্রোফাইলের ঠিকানা",
        "বর্তমান প্রোফাইলের ঠিকানা × 0x00–0xFF (সব কোড)",
    ),
    /** Known target first, other VEON codes next, then every remaining byte. */
    VEON_SCAN(
        "VEON কোড স্ক্যান",
        "VEON 0x08: বাছাই করা বাটনের কোড আগে, তারপর বাকি ২৫৫টি কোড",
    ),
    ;

    fun addresses(profile: RemoteProfile): List<ScanAddress> =
        when (this) {
            QUICK, PROFILE_FULL -> ScanAddressBlocks.PROFILE
            COMMON_BLOCKS -> ScanAddressBlocks.COMMON
            VEON_SCAN -> listOf(ScanAddressBlocks.of(RemoteProfiles.VEON))
            CURRENT_ADDRESS_FULL -> listOf(ScanAddressBlocks.of(profile))
        }

    fun commandBytes(): List<Int> =
        when (this) {
            QUICK, COMMON_BLOCKS -> ScanCodeSets.DOCUMENTED_SHEET
            PROFILE_FULL, CURRENT_ADDRESS_FULL, VEON_SCAN -> ScanCodeSets.FULL_BYTE
        }

    fun planFor(
        target: RemoteCommand,
        profile: RemoteProfile,
    ): ScanPlan {
        if (this == QUICK) {
            // Do not send Walton's unrelated 21-key sheet as the VEON code table.
            val candidates = (listOf(profile) + RemoteProfiles.ALL).distinctBy { it.id }.flatMap { item ->
                val codes = RemoteCommand.entries.mapNotNull { item.codeFor(it) }
                    .filter { it.confidence == CodeConfidence.DOCUMENTED }
                codes.map { ScanCandidate(item.address, item.addressMode, it.command, item.carrierHz) }
            }.distinctBy { it.carrierHz to it.wireBytes() }
            return ScanPlan(target, titleBn, candidates)
        }
        val veon = RemoteProfiles.VEON
        val bytes = if (this == VEON_SCAN) {
            (listOfNotNull(veon.codeFor(target)?.command) +
                veon.commands.values.map { it.command } + ScanCodeSets.FULL_BYTE).distinct()
        } else {
            commandBytes()
        }
        return ScanPlanBuilder.build(
            target = target,
            addresses = addresses(profile),
            commandBytes = bytes,
            presetTitleBn = titleBn,
            carrierHz = if (this == VEON_SCAN) veon.carrierHz else profile.carrierHz,
        )
    }
}
