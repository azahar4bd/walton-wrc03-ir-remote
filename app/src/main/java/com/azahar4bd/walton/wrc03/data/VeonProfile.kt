package com.azahar4bd.walton.wrc03.data

import com.azahar4bd.walton.wrc03.ir.NecAddressMode
import com.azahar4bd.walton.wrc03.model.CodeConfidence
import com.azahar4bd.walton.wrc03.model.IrCode
import com.azahar4bd.walton.wrc03.model.RemoteCommand
import com.azahar4bd.walton.wrc03.model.RemoteProfile

/**
 * Decoded NEC command facts for Veon_2445 / xm_1_4375, NOT a universal VEON table.
 * Provenance, byte order and semantic caveats: docs/VEON_CODES.md.
 * No Walton placeholders are used for missing VEON buttons.
 */
object VeonProfile {
    val PROFILE = RemoteProfile(
        id = "veon-2445-nec-addr08",
        nameBn = "VEON · 2445 / xm_1_4375 · 0x08",
        address = 0x08,
        addressMode = NecAddressMode.STANDARD,
        commands = mapOf(
            RemoteCommand.POWER to 0xD7,
            RemoteCommand.MUTE to 0xDF,
            RemoteCommand.SOURCE to 0xC3,
            RemoteCommand.UP to 0x92,
            RemoteCommand.DOWN to 0xD8,
            RemoteCommand.LEFT to 0x97,
            RemoteCommand.RIGHT to 0x9F,
            RemoteCommand.OK to 0x9B,
            RemoteCommand.VOLUME_UP to 0x83,
            RemoteCommand.VOLUME_DOWN to 0x86,
            RemoteCommand.CHANNEL_UP to 0x80,
            RemoteCommand.CHANNEL_DOWN to 0x8E,
            RemoteCommand.DIGIT_0 to 0xD9,
            RemoteCommand.DIGIT_1 to 0x95,
            RemoteCommand.DIGIT_2 to 0x99,
            RemoteCommand.DIGIT_3 to 0x9D,
            RemoteCommand.DIGIT_4 to 0xD6,
            RemoteCommand.DIGIT_5 to 0xDA,
            RemoteCommand.DIGIT_6 to 0xDE,
            RemoteCommand.DIGIT_7 to 0x96,
            RemoteCommand.DIGIT_8 to 0x9A,
            RemoteCommand.DIGIT_9 to 0x9E,
            // The source calls this "play"; a pause/toggle action is not established.
            RemoteCommand.PLAY_PAUSE to 0x8F,
            RemoteCommand.STOP to 0xCA,
            RemoteCommand.REWIND to 0x8B,
            RemoteCommand.FAST_FORWARD to 0x93,
            // "previous" in the source aliases "back", not necessarily a media key.
            // PREVIOUS and NEXT intentionally stay unmapped; use the code scanner.
        ).mapValues { IrCode(it.value, CodeConfidence.DOCUMENTED) },
        sourceNoteBn = "Mi Remote DB-এর Veon_2445 / xm_1_4375 কমিউনিটি তালিকা থেকে ডিকোড করা। " +
            "বাস্তব টিভিতে পরীক্ষা হয়নি; সব VEON মডেলের জন্য নয়। আগের/পরের কোড নেই (—)। " +
            "চালান/থামান বাটনটি উৎসের play কোড পাঠায়; pause নিশ্চিত নয়।",
    )
}
