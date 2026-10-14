package com.azahar4bd.walton.wrc03.data

import com.azahar4bd.walton.wrc03.ir.NecAddressMode
import com.azahar4bd.walton.wrc03.ir.NecProtocol
import com.azahar4bd.walton.wrc03.model.CodeConfidence
import com.azahar4bd.walton.wrc03.model.IrCode
import com.azahar4bd.walton.wrc03.model.RemoteCommand
import com.azahar4bd.walton.wrc03.model.RemoteProfile

/**
 * ---------------------------------------------------------------------------
 *  বাটন কোড টেবিল — community ভিত্তিক, Walton WRC-03-এ পরীক্ষা করা হয়নি
 * ---------------------------------------------------------------------------
 *
 * এই টেবিলটি দুই ভাগে ভাগ করা আছে:
 *
 * 1. `CodeConfidence.DOCUMENTED` — 6122-ফ্যামিলির (HX1838 / "user code 00FF" 21-কি
 *    হ্যান্ডসেট) প্রকাশিত NEC কোড শিট থেকে নেওয়া মান:
 *
 *        ON/OFF 0x45 · MODE 0x46 · MUTE 0x47 · VOL- 0x07 · VOL+ 0x15
 *        |<< 0x44 · >>| 0x40 · >|| 0x43 · 0 0x16
 *        1 0x0C · 2 0x18 · 3 0x5E · 4 0x08 · 5 0x1C
 *        6 0x5A · 7 0x42 · 8 0x52 · 9 0x4A
 *
 * 2. `CodeConfidence.GUESS` — ওই কোড শিটে নেই (চ্যানেল +/-, ডি-প্যাড, OK, STOP,
 *    REW/FF)। প্রোটোকল ঠিক, কিন্তু মানগুলো অনুমান; WRC-03-এর আসল রিমোট থেকে
 *    ক্যাপচার না করা পর্যন্ত এগুলো কাজ করবে কিনা কেউ জানে না।
 *
 * আসল কোড কীভাবে ক্যাপচার করে এখানে বসাবেন, সেটা README-এর
 * "কোড টেবিল কীভাবে ঠিক করবেন" অংশে লেখা আছে। ভুল হলে টিভি কিছুই করবে না —
 * কোনো ক্ষতি হবে না, শুধু বাটন কাজ করবে না।
 */
private val documented: Int.() -> IrCode = { IrCode(this, CodeConfidence.DOCUMENTED) }
private val guess: Int.() -> IrCode = { IrCode(this, CodeConfidence.GUESS) }

private val commandTable: Map<RemoteCommand, IrCode> =
    mapOf(
        // --- published 21-key NEC code sheet -----------------------------------
        RemoteCommand.POWER to 0x45.documented(),
        RemoteCommand.SOURCE to 0x46.documented(),
        RemoteCommand.MUTE to 0x47.documented(),
        RemoteCommand.VOLUME_DOWN to 0x07.documented(),
        RemoteCommand.VOLUME_UP to 0x15.documented(),
        RemoteCommand.PLAY_PAUSE to 0x43.documented(),
        RemoteCommand.PREVIOUS to 0x44.documented(),
        RemoteCommand.NEXT to 0x40.documented(),
        RemoteCommand.DIGIT_0 to 0x16.documented(),
        RemoteCommand.DIGIT_1 to 0x0C.documented(),
        RemoteCommand.DIGIT_2 to 0x18.documented(),
        RemoteCommand.DIGIT_3 to 0x5E.documented(),
        RemoteCommand.DIGIT_4 to 0x08.documented(),
        RemoteCommand.DIGIT_5 to 0x1C.documented(),
        RemoteCommand.DIGIT_6 to 0x5A.documented(),
        RemoteCommand.DIGIT_7 to 0x42.documented(),
        RemoteCommand.DIGIT_8 to 0x52.documented(),
        RemoteCommand.DIGIT_9 to 0x4A.documented(),
        // --- not in any sheet: placeholders, capture them from a real handset ---
        RemoteCommand.CHANNEL_UP to 0x1D.guess(),
        RemoteCommand.CHANNEL_DOWN to 0x1E.guess(),
        RemoteCommand.UP to 0x1A.guess(),
        RemoteCommand.DOWN to 0x1B.guess(),
        RemoteCommand.LEFT to 0x0A.guess(),
        RemoteCommand.RIGHT to 0x0B.guess(),
        RemoteCommand.OK to 0x0E.guess(),
        RemoteCommand.STOP to 0x0F.guess(),
        RemoteCommand.REWIND to 0x10.guess(),
        RemoteCommand.FAST_FORWARD to 0x11.guess(),
    )

private const val SOURCE_NOTE_BN =
    "কমিউনিটি কোড শিট (HX1838 / 21-কি, user code 00FF) থেকে নেওয়া " +
        "এবং কিছু অনুমান মিশ্রিত। Walton WRC-03-এ কোনো বাটনই পরীক্ষা করা হয়নি।"

/** All candidate profiles the user can switch between from the main screen. */
object RemoteProfiles {
    val PROFILE_A: RemoteProfile =
        RemoteProfile(
            id = "wrc03-nec-standard-addr00",
            nameBn = "প্রোফাইল ১ · ৮-বিট ঠিকানা 0x00",
            address = 0x00,
            addressMode = NecAddressMode.STANDARD,
            commands = commandTable,
            sourceNoteBn = SOURCE_NOTE_BN,
        )

    val PROFILE_B: RemoteProfile =
        RemoteProfile(
            id = "wrc03-nec-extended-addr00ff",
            nameBn = "প্রোফাইল ২ · ১৬-বিট ঠিকানা 0x00FF",
            address = 0x00FF,
            addressMode = NecAddressMode.EXTENDED,
            commands = commandTable,
            sourceNoteBn = SOURCE_NOTE_BN,
        )

    val PROFILE_C: RemoteProfile =
        RemoteProfile(
            id = "wrc03-nec-extended-addr807f",
            nameBn = "প্রোফাইল ৩ · ১৬-বিট ঠিকানা 0x807F",
            address = 0x807F,
            addressMode = NecAddressMode.EXTENDED,
            commands = commandTable,
            sourceNoteBn = SOURCE_NOTE_BN,
        )

    val ALL: List<RemoteProfile> = listOf(PROFILE_A, PROFILE_B, PROFILE_C)

    /** Shown on first launch. Same table as B/C, only the address block differs. */
    val DEFAULT: RemoteProfile = PROFILE_A

    val CARRIER_HZ: Int = NecProtocol.CARRIER_FREQUENCY_HZ

    fun byId(id: String): RemoteProfile = ALL.first { it.id == id }
}
