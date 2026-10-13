package com.azahar4bd.walton.wrc03.model

/** Sections of the remote, in the order they are rendered on screen. */
enum class CommandGroup(
    val titleBn: String,
    val columns: Int,
) {
    POWER_AND_INPUT("পাওয়ার / সাউন্ড", 3),
    NAVIGATION("ডি-প্যাড", 3),
    VOLUME_CHANNEL("ভলিউম / চ্যানেল", 2),
    NUMBER_PAD("নম্বর প্যাড", 3),
    MEDIA("মিডিয়া", 3),
}

/**
 * Every button the app can transmit.
 *
 * @property labelBn Bengali caption shown on the button.
 * @property symbol glyph shown in front of the caption; equals [labelBn] for digits.
 * @property repeatable whether holding the button keeps sending (NEC repeat frames),
 *           the way real volume / channel / seek keys behave.
 */
enum class RemoteCommand(
    val group: CommandGroup,
    val labelBn: String,
    val symbol: String,
    val repeatable: Boolean = false,
) {
    POWER(CommandGroup.POWER_AND_INPUT, "পাওয়ার", "⏻"),
    MUTE(CommandGroup.POWER_AND_INPUT, "মিউট", "🔇"),
    SOURCE(CommandGroup.POWER_AND_INPUT, "সোর্স", "⇄"),

    UP(CommandGroup.NAVIGATION, "উপরে", "▲", repeatable = true),
    DOWN(CommandGroup.NAVIGATION, "নিচে", "▼", repeatable = true),
    LEFT(CommandGroup.NAVIGATION, "বামে", "◀", repeatable = true),
    RIGHT(CommandGroup.NAVIGATION, "ডানে", "▶", repeatable = true),
    OK(CommandGroup.NAVIGATION, "ওকে", "●"),

    VOLUME_UP(CommandGroup.VOLUME_CHANNEL, "ভলিউম", "＋", repeatable = true),
    VOLUME_DOWN(CommandGroup.VOLUME_CHANNEL, "ভলিউম", "－", repeatable = true),
    CHANNEL_UP(CommandGroup.VOLUME_CHANNEL, "চ্যানেল", "⏫", repeatable = true),
    CHANNEL_DOWN(CommandGroup.VOLUME_CHANNEL, "চ্যানেল", "⏬", repeatable = true),

    DIGIT_1(CommandGroup.NUMBER_PAD, "১", "১"),
    DIGIT_2(CommandGroup.NUMBER_PAD, "২", "২"),
    DIGIT_3(CommandGroup.NUMBER_PAD, "৩", "৩"),
    DIGIT_4(CommandGroup.NUMBER_PAD, "৪", "৪"),
    DIGIT_5(CommandGroup.NUMBER_PAD, "৫", "৫"),
    DIGIT_6(CommandGroup.NUMBER_PAD, "৬", "৬"),
    DIGIT_7(CommandGroup.NUMBER_PAD, "৭", "৭"),
    DIGIT_8(CommandGroup.NUMBER_PAD, "৮", "৮"),
    DIGIT_9(CommandGroup.NUMBER_PAD, "৯", "৯"),
    DIGIT_0(CommandGroup.NUMBER_PAD, "০", "০"),

    PLAY_PAUSE(CommandGroup.MEDIA, "চালান / থামান", "⏯"),
    STOP(CommandGroup.MEDIA, "থামান", "⏹"),
    PREVIOUS(CommandGroup.MEDIA, "আগের", "⏮", repeatable = true),
    NEXT(CommandGroup.MEDIA, "পরের", "⏭", repeatable = true),
    REWIND(CommandGroup.MEDIA, "রিওয়াইন্ড", "⏪", repeatable = true),
    FAST_FORWARD(CommandGroup.MEDIA, "ফরোয়ার্ড", "⏩", repeatable = true),
    ;

    /** Text drawn on the button: "⏻ পাওয়ার", or just "১" for the digits. */
    val buttonTextBn: String
        get() = if (symbol == labelBn) symbol else "$symbol $labelBn"
}
