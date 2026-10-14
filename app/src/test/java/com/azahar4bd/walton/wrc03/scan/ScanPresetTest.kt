package com.azahar4bd.walton.wrc03.scan

import com.azahar4bd.walton.wrc03.data.RemoteProfiles
import com.azahar4bd.walton.wrc03.ir.NecAddressMode
import com.azahar4bd.walton.wrc03.ir.NecEncoder
import com.azahar4bd.walton.wrc03.ir.NecProtocol
import com.azahar4bd.walton.wrc03.model.RemoteCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sweep is the risky part of code scan mode: too narrow and it never finds the code,
 * too wide and the user waits forever. These tests pin both the shape and the size.
 */
class ScanPresetTest {
    private val profile = RemoteProfiles.DEFAULT

    @Test
    fun theQuickSweepIsTheProfileBlocksTimesThePublishedSheet() {
        val plan = ScanPreset.QUICK.planFor(RemoteCommand.POWER, profile)

        assertEquals(ScanAddressBlocks.PROFILE.size * ScanCodeSets.DOCUMENTED_SHEET.size, plan.size)
        assertEquals(3 * 18, plan.size)
        assertEquals(RemoteCommand.POWER, plan.target)
        assertEquals(ScanPreset.QUICK.titleBn, plan.presetTitleBn)
    }

    @Test
    fun theShortCodeListIsExactlyThePublishedSheet() {
        assertEquals(18, ScanCodeSets.DOCUMENTED_SHEET.size)
        assertEquals(
            ScanCodeSets.DOCUMENTED_SHEET.size,
            ScanCodeSets.DOCUMENTED_SHEET.toSet().size,
        )
        assertTrue("POWER 0x45 must be swept first-ish", ScanCodeSets.DOCUMENTED_SHEET.contains(0x45))
        assertTrue(ScanCodeSets.DOCUMENTED_SHEET.contains(0x16))
        assertEquals(256, ScanCodeSets.FULL_BYTE.size)
        assertEquals(0x00, ScanCodeSets.FULL_BYTE.first())
        assertEquals(0xFF, ScanCodeSets.FULL_BYTE.last())
    }

    @Test
    fun theProfileBlocksAreTheShippedProfiles() {
        assertEquals(RemoteProfiles.ALL.size, ScanAddressBlocks.PROFILE.size)
        RemoteProfiles.ALL.forEach { shipped ->
            assertTrue(
                "${shipped.id} is not swept",
                ScanAddressBlocks.PROFILE.any { it.address == shipped.address && it.mode == shipped.addressMode },
            )
        }
    }

    @Test
    fun commonBlocksDropAddressesThatBeamTheSameBytes() {
        val wires = ScanAddressBlocks.COMMON.map { it.wireBytes() }

        assertEquals("two blocks put the same bytes in the air", wires.size, wires.distinct().size)
        assertTrue(ScanAddressBlocks.COMMON.size >= ScanAddressBlocks.PROFILE.size)
        // 0xFF STANDARD and 0x00FF EXTENDED are the same frame, so only one survives.
        assertEquals(
            ScanAddressBlocks.PROFILE.size + 2,
            ScanAddressBlocks.COMMON.size,
        )
    }

    @Test
    fun noPlanEverSendsTheSameFrameTwice() {
        ScanPreset.entries.forEach { preset ->
            RemoteProfiles.ALL.forEach { candidate ->
                val plan = preset.planFor(RemoteCommand.POWER, candidate)
                val wires = plan.candidates.map { it.wireBytes() }

                assertEquals(
                    "${preset.name}/${candidate.id} repeats a frame",
                    wires.size,
                    wires.distinct().size,
                )
            }
        }
    }

    @Test
    fun everyCandidateEncodesIntoAFullNecFrame() {
        ScanPreset.entries.forEach { preset ->
            preset.planFor(RemoteCommand.MUTE, profile).candidates.forEach { candidate ->
                val pattern = NecEncoder.encodeFrame(candidate.frame())

                assertEquals(NecProtocol.FRAME_PATTERN_LENGTH, pattern.size)
                assertEquals(profile.carrierHz, candidate.carrierHz)
                assertEquals(candidate.commandByte, candidate.frame().command)
                assertTrue(candidate.codeHex().startsWith("0x"))
                assertTrue(candidate.addressHex().startsWith("0x"))
            }
        }
    }

    @Test
    fun candidatesStayInsideTheirAddressMode() {
        ScanPreset.entries.forEach { preset ->
            preset.planFor(RemoteCommand.OK, profile).candidates.forEach { candidate ->
                when (candidate.addressMode) {
                    NecAddressMode.STANDARD -> assertTrue(candidate.address in 0x00..0xFF)
                    NecAddressMode.EXTENDED -> assertTrue(candidate.address in 0x0000..0xFFFF)
                }
            }
        }
    }

    @Test
    fun theCurrentAddressSweepOnlyUsesTheSelectedProfile() {
        RemoteProfiles.ALL.forEach { candidate ->
            val plan = ScanPreset.CURRENT_ADDRESS_FULL.planFor(RemoteCommand.POWER, candidate)

            assertEquals(256, plan.size)
            plan.candidates.forEach {
                assertEquals(candidate.address, it.address)
                assertEquals(candidate.addressMode, it.addressMode)
            }
        }
    }

    @Test
    fun theFullByteSweepsCoverEveryProfileBlock() {
        val plan = ScanPreset.PROFILE_FULL.planFor(RemoteCommand.POWER, profile)

        assertEquals(ScanAddressBlocks.PROFILE.size * 256, plan.size)
        assertEquals(
            ScanAddressBlocks.PROFILE.size,
            plan.candidates.map { it.address to it.addressMode }.distinct().size,
        )
    }

    @Test
    fun aSweepRunsAddressBlockFirst() {
        val plan = ScanPreset.QUICK.planFor(RemoteCommand.POWER, profile)
        val sheetSize = ScanCodeSets.DOCUMENTED_SHEET.size
        val firstBlock = plan.candidates.take(sheetSize)

        assertEquals(1, firstBlock.map { it.address to it.addressMode }.distinct().size)
        assertEquals(ScanCodeSets.DOCUMENTED_SHEET, firstBlock.map { it.commandByte })
        assertEquals(
            ScanCodeSets.DOCUMENTED_SHEET,
            plan.candidates.drop(sheetSize).take(sheetSize).map { it.commandByte },
        )
    }

    @Test
    fun everyPresetHasBengaliCopyAndANonEmptyPlan() {
        ScanPreset.entries.forEach { preset ->
            assertTrue(preset.titleBn.isNotBlank())
            assertTrue(preset.detailBn.isNotBlank())
            assertTrue(preset.addresses(profile).isNotEmpty())
            assertTrue(preset.commandBytes().isNotEmpty())
            assertTrue("${preset.name} builds nothing", preset.planFor(RemoteCommand.POWER, profile).size > 0)
        }
    }

    @Test
    fun aPlanLooksUpCandidatesByIndexAndOutOfRangeIsNull() {
        val plan = ScanPreset.QUICK.planFor(RemoteCommand.POWER, profile)

        assertEquals(plan.candidates.first(), plan.candidateAt(0))
        assertEquals(plan.candidates.last(), plan.candidateAt(plan.size - 1))
        assertNull(plan.candidateAt(-1))
        assertNull(plan.candidateAt(plan.size))
    }
}
