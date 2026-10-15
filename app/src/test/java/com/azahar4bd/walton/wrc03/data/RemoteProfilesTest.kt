package com.azahar4bd.walton.wrc03.data

import com.azahar4bd.walton.wrc03.ir.NecAddressMode
import com.azahar4bd.walton.wrc03.ir.NecProtocol
import com.azahar4bd.walton.wrc03.model.CodeConfidence
import com.azahar4bd.walton.wrc03.model.RemoteCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the shape of the code table, and — just as important — documents out loud that
 * none of it has been checked against a real Walton WRC-03 handset yet.
 */
class RemoteProfilesTest {
    @Test
    fun theDefaultProfileIsOneOfTheCandidates() {
        assertTrue(RemoteProfiles.ALL.contains(RemoteProfiles.DEFAULT))
        assertTrue(RemoteProfiles.ALL.isNotEmpty())
    }

    @Test
    fun profileIdsAreUnique() {
        assertEquals(RemoteProfiles.ALL.size, RemoteProfiles.ALL.map { it.id }.toSet().size)
        RemoteProfiles.ALL.forEach { assertEquals(it, RemoteProfiles.byId(it.id)) }
    }

    @Test
    fun legacyWaltonProfilesCoverEveryButton() {
        listOf(RemoteProfiles.PROFILE_A, RemoteProfiles.PROFILE_B, RemoteProfiles.PROFILE_C).forEach { profile ->
            RemoteCommand.entries.forEach { command ->
                assertTrue(
                    "${profile.id} is missing ${command.name}",
                    profile.codeFor(command) != null,
                )
            }
        }
    }

    @Test
    fun everyCodeFitsInOneByte() {
        RemoteProfiles.ALL.forEach { profile ->
            profile.commands.forEach { (command, code) ->
                assertTrue(
                    "${profile.id}/${command.name} = ${code.command} is out of range",
                    code.command in 0x00..0xFF,
                )
            }
        }
    }

    @Test
    fun codesAreUniqueInsideAProfile() {
        RemoteProfiles.ALL.forEach { profile ->
            val values = profile.commands.values.map { it.command }
            assertEquals(
                "${profile.id} maps two buttons to the same code",
                values.size,
                values.toSet().size,
            )
        }
    }

    @Test
    fun addressesFitTheirAddressMode() {
        RemoteProfiles.ALL.forEach { profile ->
            when (profile.addressMode) {
                NecAddressMode.STANDARD -> assertTrue(profile.address in 0x00..0xFF)
                NecAddressMode.EXTENDED -> assertTrue(profile.address in 0x0000..0xFFFF)
            }
            assertEquals(NecProtocol.CARRIER_FREQUENCY_HZ, profile.carrierHz)
            assertEquals(38_000, profile.carrierHz)
        }
    }

    @Test
    fun thePublished21KeyCodesArePresentVerbatim() {
        val table = RemoteProfiles.DEFAULT.commands
        assertEquals(0x45, table[RemoteCommand.POWER]!!.command)
        assertEquals(0x46, table[RemoteCommand.SOURCE]!!.command)
        assertEquals(0x47, table[RemoteCommand.MUTE]!!.command)
        assertEquals(0x07, table[RemoteCommand.VOLUME_DOWN]!!.command)
        assertEquals(0x15, table[RemoteCommand.VOLUME_UP]!!.command)
        assertEquals(0x43, table[RemoteCommand.PLAY_PAUSE]!!.command)
        assertEquals(0x44, table[RemoteCommand.PREVIOUS]!!.command)
        assertEquals(0x40, table[RemoteCommand.NEXT]!!.command)
        assertEquals(0x16, table[RemoteCommand.DIGIT_0]!!.command)
        assertEquals(0x0C, table[RemoteCommand.DIGIT_1]!!.command)
        assertEquals(0x18, table[RemoteCommand.DIGIT_2]!!.command)
        assertEquals(0x5E, table[RemoteCommand.DIGIT_3]!!.command)
        assertEquals(0x08, table[RemoteCommand.DIGIT_4]!!.command)
        assertEquals(0x1C, table[RemoteCommand.DIGIT_5]!!.command)
        assertEquals(0x5A, table[RemoteCommand.DIGIT_6]!!.command)
        assertEquals(0x42, table[RemoteCommand.DIGIT_7]!!.command)
        assertEquals(0x52, table[RemoteCommand.DIGIT_8]!!.command)
        assertEquals(0x4A, table[RemoteCommand.DIGIT_9]!!.command)
    }

    @Test
    fun publishedCodesAreMarkedAsDocumented() {
        val documented =
            RemoteProfiles.DEFAULT.commands.filter { it.value.confidence == CodeConfidence.DOCUMENTED }
        assertEquals(18, documented.size)
    }

    @Test
    fun keysWithNoPublishedCodeAreFlaggedAsGuesses() {
        val guesses =
            RemoteProfiles.DEFAULT.commands.filter { it.value.confidence == CodeConfidence.GUESS }
        assertEquals(10, guesses.size)
        listOf(
            RemoteCommand.CHANNEL_UP, RemoteCommand.CHANNEL_DOWN,
            RemoteCommand.UP, RemoteCommand.DOWN, RemoteCommand.LEFT, RemoteCommand.RIGHT,
            RemoteCommand.OK, RemoteCommand.STOP, RemoteCommand.REWIND, RemoteCommand.FAST_FORWARD,
        ).forEach { command ->
            assertEquals(
                "$command must be flagged as a guess",
                CodeConfidence.GUESS,
                RemoteProfiles.DEFAULT.codeFor(command)!!.confidence,
            )
        }
    }

    @Test
    fun noProfileClaimsToBeVerifiedYet() {
        RemoteProfiles.ALL.forEach { profile ->
            assertFalse(
                "${profile.id} is marked verified — only do that after testing on a real WRC-03",
                profile.verified,
            )
            assertTrue(profile.sourceNoteBn.isNotBlank())
            assertTrue(profile.nameBn.isNotBlank())
        }
    }
}
