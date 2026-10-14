package com.azahar4bd.walton.wrc03.ui

import com.azahar4bd.walton.wrc03.data.RemoteProfiles
import com.azahar4bd.walton.wrc03.ir.CarrierRange
import com.azahar4bd.walton.wrc03.ir.IrAvailability
import com.azahar4bd.walton.wrc03.ir.IrSendResult
import com.azahar4bd.walton.wrc03.ir.NecEncoder
import com.azahar4bd.walton.wrc03.model.RemoteCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteTextTest {
    private val allStates: List<IrAvailability> =
        listOf(
            IrAvailability.NotSupported,
            IrAvailability.NoEmitter,
            IrAvailability.CarrierUnsupported(listOf(CarrierRange(56_000, 60_000)), 38_000),
            IrAvailability.Ready(listOf(CarrierRange(30_000, 60_000)), 38_000),
            IrAvailability.Ready(emptyList(), 38_000),
        )

    @Test
    fun everyHardwareStateHasABengaliTitleAndDetail() {
        allStates.forEach { state ->
            assertTrue(RemoteText.statusTitle(state).isNotBlank())
            assertTrue(RemoteText.statusDetail(state).isNotBlank())
        }
    }

    @Test
    fun onlyAReadyBlasterGetsTheTick() {
        assertEquals("✓", RemoteText.statusIcon(IrAvailability.Ready(emptyList(), 38_000)))
        allStates.filter { it !is IrAvailability.Ready }.forEach { state ->
            assertEquals("✕", RemoteText.statusIcon(state))
        }
    }

    @Test
    fun theNoEmitterMessageNamesTheApiThatDecidedIt() {
        val detail = RemoteText.statusDetail(IrAvailability.NoEmitter)
        assertTrue(detail.contains("hasIrEmitter"))
        assertTrue(detail.contains("IR"))
    }

    @Test
    fun theUnsupportedCarrierMessageListsTheRanges() {
        val state = IrAvailability.CarrierUnsupported(listOf(CarrierRange(56_000, 60_000)), 38_000)
        val detail = RemoteText.statusDetail(state)
        assertTrue(detail.contains("56"))
        assertTrue(detail.contains("60"))
    }

    @Test
    fun theSentSummaryShowsTheButtonTheHexAndTheCarrier() {
        val profile = RemoteProfiles.DEFAULT
        val code = profile.codeFor(RemoteCommand.POWER)!!.command
        val sent =
            IrSendResult.Sent(
                command = RemoteCommand.POWER,
                code = code,
                carrierHz = profile.carrierHz,
                pattern = NecEncoder.encodeFrame(profile.address, code, profile.addressMode),
            )

        val summary = RemoteText.sentSummary(sent)
        assertTrue(summary.contains(RemoteCommand.POWER.labelBn))
        assertTrue(summary.contains("0x45"))
        assertTrue(summary.contains("38000"))
        assertTrue(summary.contains("67"))
    }

    @Test
    fun theProfileSummaryStatesTheAddressTheCarrierAndTheVerificationState() {
        val summary = RemoteText.profileSummary(RemoteProfiles.DEFAULT)
        assertTrue(summary.contains("0x00"))
        assertTrue(summary.contains("38000"))
        assertTrue(summary.contains("যাচাই"))
    }

    @Test
    fun theCodeSheetListsEveryButtonOfEverySection() {
        val sheet = RemoteText.codeSheet(RemoteProfiles.DEFAULT)
        RemoteCommand.entries.forEach { command ->
            assertTrue("code sheet is missing ${command.name}", sheet.contains(command.labelBn))
        }
        RemoteKeypadLayout.sections.forEach { group ->
            assertTrue(sheet.contains(group.titleBn))
        }
        assertTrue(sheet.contains("NEC"))
        assertTrue(sheet.contains(RemoteProfiles.DEFAULT.nameBn))
    }
}
