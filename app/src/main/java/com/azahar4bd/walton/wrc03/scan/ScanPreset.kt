package com.azahar4bd.walton.wrc03.scan

import com.azahar4bd.walton.wrc03.model.RemoteCommand
import com.azahar4bd.walton.wrc03.model.RemoteProfile

/**
 * How wide a sweep the user wants.
 *
 * Every preset is a cross product of address blocks × command bytes; the candidate count
 * shown in the UI comes from [planFor], so nobody has to guess how long a sweep takes.
 */
enum class ScanPreset(
    val titleBn: String,
    val detailBn: String,
) {
    /** ~1 minute: the shipped profile blocks against the published 6122 code sheet. */
    QUICK(
        "দ্রুত স্ক্যান",
        "তিনটি প্রোফাইল ঠিকানা × প্রকাশিত কোড শিটের মান",
    ),

    /** Same short code list, but also the byte-swapped / zero address blocks. */
    COMMON_BLOCKS(
        "প্রচলিত ঠিকানা ব্লক",
        "সাধারণ NEC ঠিকানা ব্লক × প্রকাশিত কোড শিটের মান",
    ),

    /** The long one: every command byte under each shipped address block. */
    PROFILE_FULL(
        "প্রোফাইল ঠিকানা, পুরো বাইট",
        "তিনটি প্রোফাইল ঠিকানা × 0x00–0xFF (সব কোড)",
    ),

    /** Only the currently selected profile's address block, all 256 command bytes. */
    CURRENT_ADDRESS_FULL(
        "শুধু এই প্রোফাইলের ঠিকানা",
        "বর্তমান প্রোফাইলের ঠিকানা × 0x00–0xFF (সব কোড)",
    ),
    ;

    fun addresses(profile: RemoteProfile): List<ScanAddress> =
        when (this) {
            QUICK, PROFILE_FULL -> ScanAddressBlocks.PROFILE
            COMMON_BLOCKS -> ScanAddressBlocks.COMMON
            CURRENT_ADDRESS_FULL -> listOf(ScanAddressBlocks.of(profile))
        }

    fun commandBytes(): List<Int> =
        when (this) {
            QUICK, COMMON_BLOCKS -> ScanCodeSets.DOCUMENTED_SHEET
            PROFILE_FULL, CURRENT_ADDRESS_FULL -> ScanCodeSets.FULL_BYTE
        }

    fun planFor(
        target: RemoteCommand,
        profile: RemoteProfile,
    ): ScanPlan =
        ScanPlanBuilder.build(
            target = target,
            addresses = addresses(profile),
            commandBytes = commandBytes(),
            presetTitleBn = titleBn,
            carrierHz = profile.carrierHz,
        )
}
