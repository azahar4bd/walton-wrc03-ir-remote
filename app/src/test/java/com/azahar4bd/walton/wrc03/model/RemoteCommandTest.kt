package com.azahar4bd.walton.wrc03.model

import com.azahar4bd.walton.wrc03.ui.RemoteKeypadLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCommandTest {
    @Test
    fun everyButtonHasABengaliCaptionAndAGlyph() {
        RemoteCommand.entries.forEach { command ->
            assertTrue("${command.name} needs a Bengali label", command.labelBn.isNotBlank())
            assertTrue("${command.name} needs a glyph", command.symbol.isNotBlank())
            assertTrue("${command.name} needs button text", command.buttonTextBn.isNotBlank())
        }
    }

    @Test
    fun theDigitsUseBengaliNumerals() {
        val numerals = listOf("১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯", "০")
        val digits =
            listOf(
                RemoteCommand.DIGIT_1, RemoteCommand.DIGIT_2, RemoteCommand.DIGIT_3,
                RemoteCommand.DIGIT_4, RemoteCommand.DIGIT_5, RemoteCommand.DIGIT_6,
                RemoteCommand.DIGIT_7, RemoteCommand.DIGIT_8, RemoteCommand.DIGIT_9,
                RemoteCommand.DIGIT_0,
            )
        assertEquals(numerals, digits.map { it.symbol })
    }

    @Test
    fun theKeypadShowsEveryCommandExactlyOnce() {
        val onScreen = RemoteKeypadLayout.allCommands()
        assertEquals(RemoteCommand.entries.size, onScreen.size)
        assertEquals(RemoteCommand.entries.toSet(), onScreen.toSet())
        assertEquals("no button may appear twice", onScreen.size, onScreen.toSet().size)
    }

    @Test
    fun eachSectionFillsWholeRows() {
        RemoteKeypadLayout.sections.forEach { group ->
            val cells = RemoteKeypadLayout.cells(group)
            assertEquals(
                "${group.name} does not fill ${group.columns} columns",
                0,
                cells.size % group.columns,
            )
        }
    }

    @Test
    fun onlyVolumeChannelAndSeekKeysRepeat() {
        val expected =
            setOf(
                RemoteCommand.UP, RemoteCommand.DOWN, RemoteCommand.LEFT, RemoteCommand.RIGHT,
                RemoteCommand.VOLUME_UP, RemoteCommand.VOLUME_DOWN,
                RemoteCommand.CHANNEL_UP, RemoteCommand.CHANNEL_DOWN,
                RemoteCommand.REWIND, RemoteCommand.FAST_FORWARD,
                RemoteCommand.PREVIOUS, RemoteCommand.NEXT,
            )
        assertEquals(expected, RemoteCommand.entries.filter { it.repeatable }.toSet())
    }
}
