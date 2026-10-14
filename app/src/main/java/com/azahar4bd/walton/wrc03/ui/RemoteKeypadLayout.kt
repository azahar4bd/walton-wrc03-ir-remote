package com.azahar4bd.walton.wrc03.ui

import com.azahar4bd.walton.wrc03.model.CommandGroup
import com.azahar4bd.walton.wrc03.model.RemoteCommand

/**
 * Where each button sits on screen.
 *
 * Pure data (no Android imports) so the layout can be unit tested: every
 * [RemoteCommand] must appear exactly once across all sections.
 */
object RemoteKeypadLayout {
    /** Sections in screen order. */
    val sections: List<CommandGroup> = CommandGroup.entries

    /**
     * Ordered cells of one section. `null` means "leave this cell empty", which is how
     * the D-pad cross and the bottom row of the number pad are shaped.
     */
    fun cells(group: CommandGroup): List<RemoteCommand?> =
        when (group) {
            CommandGroup.POWER_AND_INPUT ->
                listOf(
                    RemoteCommand.POWER,
                    RemoteCommand.MUTE,
                    RemoteCommand.SOURCE,
                )

            CommandGroup.NAVIGATION ->
                listOf(
                    null, RemoteCommand.UP, null,
                    RemoteCommand.LEFT, RemoteCommand.OK, RemoteCommand.RIGHT,
                    null, RemoteCommand.DOWN, null,
                )

            CommandGroup.VOLUME_CHANNEL ->
                listOf(
                    RemoteCommand.VOLUME_UP, RemoteCommand.VOLUME_DOWN,
                    RemoteCommand.CHANNEL_UP, RemoteCommand.CHANNEL_DOWN,
                )

            CommandGroup.NUMBER_PAD ->
                listOf(
                    RemoteCommand.DIGIT_1, RemoteCommand.DIGIT_2, RemoteCommand.DIGIT_3,
                    RemoteCommand.DIGIT_4, RemoteCommand.DIGIT_5, RemoteCommand.DIGIT_6,
                    RemoteCommand.DIGIT_7, RemoteCommand.DIGIT_8, RemoteCommand.DIGIT_9,
                    null, RemoteCommand.DIGIT_0, null,
                )

            CommandGroup.MEDIA ->
                listOf(
                    RemoteCommand.PLAY_PAUSE, RemoteCommand.STOP,
                    RemoteCommand.PREVIOUS, RemoteCommand.NEXT,
                    RemoteCommand.REWIND, RemoteCommand.FAST_FORWARD,
                )
        }

    /** Every command reachable from the UI, in display order. */
    fun allCommands(): List<RemoteCommand> = sections.flatMap { cells(it).filterNotNull() }
}
