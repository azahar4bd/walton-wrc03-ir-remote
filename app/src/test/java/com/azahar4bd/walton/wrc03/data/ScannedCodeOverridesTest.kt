package com.azahar4bd.walton.wrc03.data

import com.azahar4bd.walton.wrc03.ir.NecAddressMode
import com.azahar4bd.walton.wrc03.model.CodeConfidence
import com.azahar4bd.walton.wrc03.model.RemoteCommand
import com.azahar4bd.walton.wrc03.model.ScannedCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the scanner writes has to change the remote without touching the shipped table:
 * only the profile that beams the same address block may pick the code up, and nothing
 * may start claiming the whole handset is verified.
 */
class ScannedCodeOverridesTest {
    private val shipped = RemoteProfiles.DEFAULT

    private fun scan(
        command: RemoteCommand,
        code: Int,
        foundAt: Long = 1L,
        address: Int = shipped.address,
        mode: NecAddressMode = shipped.addressMode,
    ) = ScannedCode(command, address, mode, code, shipped.carrierHz, foundAt)

    @Test
    fun aProfileWithoutScansIsReturnedAsItIs() {
        assertSame(shipped, shipped.withScannedCodes(emptyList()))
    }

    @Test
    fun aScanFromAnotherAddressBlockIsIgnored() {
        val elsewhere = scan(RemoteCommand.POWER, 0x11, address = 0x807F, mode = NecAddressMode.EXTENDED)

        val merged = shipped.withScannedCodes(listOf(elsewhere))

        assertSame(shipped, merged)
        assertEquals(0x45, merged.codeFor(RemoteCommand.POWER)!!.command)
        assertEquals(0, merged.scannedCount())
    }

    @Test
    fun aMatchingScanReplacesTheShippedGuess() {
        val merged = shipped.withScannedCodes(listOf(scan(RemoteCommand.CHANNEL_UP, 0x2A)))

        assertEquals(0x2A, merged.codeFor(RemoteCommand.CHANNEL_UP)!!.command)
        assertEquals(CodeConfidence.SCANNED, merged.codeFor(RemoteCommand.CHANNEL_UP)!!.confidence)
        assertEquals(1, merged.scannedCount())
        // the rest of the table, and the profile identity, stay put
        assertEquals(0x45, merged.codeFor(RemoteCommand.POWER)!!.command)
        assertEquals(CodeConfidence.DOCUMENTED, merged.codeFor(RemoteCommand.POWER)!!.confidence)
        assertEquals(shipped.id, merged.id)
        assertEquals(shipped.address, merged.address)
        assertEquals(shipped.addressMode, merged.addressMode)
    }

    @Test
    fun theShippedTableItselfIsNeverMutated() {
        shipped.withScannedCodes(listOf(scan(RemoteCommand.CHANNEL_UP, 0x2A)))

        assertEquals(0x1D, shipped.codeFor(RemoteCommand.CHANNEL_UP)!!.command)
        assertEquals(CodeConfidence.GUESS, shipped.codeFor(RemoteCommand.CHANNEL_UP)!!.confidence)
        assertFalse(shipped.verified)
    }

    @Test
    fun theNewestRecordWinsForTheSameButton() {
        val older = scan(RemoteCommand.OK, 0x01, foundAt = 10L)
        val newer = scan(RemoteCommand.OK, 0x02, foundAt = 20L)

        assertEquals(
            0x02,
            shipped.withScannedCodes(listOf(newer, older)).codeFor(RemoteCommand.OK)!!.command,
        )
        assertEquals(
            0x02,
            shipped.withScannedCodes(listOf(older, newer)).codeFor(RemoteCommand.OK)!!.command,
        )
    }

    @Test
    fun everyProfileOnlyPicksUpItsOwnScans() {
        val records =
            RemoteProfiles.ALL.map { profile ->
                ScannedCode(RemoteCommand.POWER, profile.address, profile.addressMode, 0x77, profile.carrierHz, 3L)
            }

        RemoteProfiles.ALL.forEach { profile ->
            val merged = profile.withScannedCodes(records)
            assertEquals(0x77, merged.codeFor(RemoteCommand.POWER)!!.command)
            assertEquals(1, merged.scannedCount())
        }
    }

    @Test
    fun aFullyScannedProfileStillDoesNotClaimToBeVerified() {
        val records = RemoteCommand.entries.map { scan(it, 0x10) }

        val merged = shipped.withScannedCodes(records)

        assertEquals(RemoteCommand.entries.size, merged.commands.size)
        assertEquals(RemoteCommand.entries.size, merged.scannedCount())
        assertFalse(
            "`verified` describes the shipped table, not one phone's scan results",
            merged.verified,
        )
        assertTrue(merged.sourceNoteBn.isNotBlank())
    }

    @Test
    fun scannedProfilesStillCoverEveryButtonWithOneByteCodes() {
        val merged = shipped.withScannedCodes(RemoteCommand.entries.take(5).map { scan(it, 0x33) })

        RemoteCommand.entries.forEach { command ->
            val code = merged.codeFor(command)
            assertTrue("$command lost its code", code != null)
            assertTrue(code!!.command in 0x00..0xFF)
        }
    }
}
