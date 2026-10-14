package com.azahar4bd.walton.wrc03.ui

import com.azahar4bd.walton.wrc03.data.RemoteProfiles
import com.azahar4bd.walton.wrc03.ir.CarrierRange
import com.azahar4bd.walton.wrc03.ir.IrAvailability
import com.azahar4bd.walton.wrc03.ir.IrSendResult
import com.azahar4bd.walton.wrc03.ir.NecAddressMode
import com.azahar4bd.walton.wrc03.ir.NecEncoder
import com.azahar4bd.walton.wrc03.ir.SendFailure
import com.azahar4bd.walton.wrc03.model.CodeConfidence
import com.azahar4bd.walton.wrc03.model.RemoteCommand
import com.azahar4bd.walton.wrc03.model.ScannedCode
import com.azahar4bd.walton.wrc03.scan.ScanPlan
import com.azahar4bd.walton.wrc03.scan.ScanPreset
import com.azahar4bd.walton.wrc03.scan.ScanSnapshot
import com.azahar4bd.walton.wrc03.scan.ScanState
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

    // --- code scan mode ------------------------------------------------------

    private val plan = ScanPreset.QUICK.planFor(RemoteCommand.POWER, RemoteProfiles.DEFAULT)
    private val emptyPlan = ScanPlan(RemoteCommand.POWER, "খালি", emptyList())

    private fun snapshot(
        state: ScanState = ScanState.RUNNING,
        sentCount: Int = 3,
        found: List<ScannedCode> = emptyList(),
        failureMessageBn: String? = null,
    ) = ScanSnapshot(
        state = state,
        target = RemoteCommand.POWER,
        candidate = plan.candidateAt(sentCount - 1),
        sentCount = sentCount,
        total = plan.size,
        intervalMs = 1_200L,
        remainingMs = (plan.size - sentCount) * 1_200L,
        found = found,
        failureMessageBn = failureMessageBn,
    )

    @Test
    fun thePlanSummaryStatesHowManyCodesAndHowLong() {
        val summary = RemoteText.scanPlanSummary(plan, 1_200L)

        assertTrue(summary.contains(plan.size.toString()))
        assertTrue(summary.contains("1.2"))
        assertTrue(summary.contains("সেকেন্ড"))
        assertTrue(RemoteText.scanPlanSummary(emptyPlan, 1_200L).isNotBlank())
        assertTrue(RemoteText.scanPlanSummary(emptyPlan, 1_200L).contains("নেই"))
    }

    @Test
    fun theProgressLineCountsWhatWentOutAndWhatIsLeft() {
        val line = RemoteText.scanProgress(snapshot(sentCount = 3))

        assertTrue(line.contains("3"))
        assertTrue(line.contains(plan.size.toString()))
        assertTrue(line.contains("সেকেন্ড"))
    }

    @Test
    fun theCandidateLineShowsTheWholeFrame() {
        val candidate = plan.candidates.first()
        val line = RemoteText.scanCandidateLine(candidate)

        assertTrue(line.contains(candidate.addressHex()))
        assertTrue(line.contains(candidate.codeHex()))
        assertTrue(line.contains(candidate.modeBn()))
        assertTrue(RemoteText.scanCandidateLine(null).isNotBlank())
    }

    @Test
    fun everyStateHasALineAndAStartPauseLabel() {
        ScanState.entries.forEach { state ->
            assertTrue(RemoteText.scanStateLine(snapshot(state = state, sentCount = 1)).isNotBlank())
            assertTrue(RemoteText.scanStartPauseLabel(state).isNotBlank())
        }
        assertEquals("পজ করুন", RemoteText.scanStartPauseLabel(ScanState.RUNNING))
        assertEquals("আবার শুরু", RemoteText.scanStartPauseLabel(ScanState.PAUSED))
        assertEquals("স্ক্যান শুরু করুন", RemoteText.scanStartPauseLabel(ScanState.IDLE))
        assertEquals("নতুন করে শুরু", RemoteText.scanStartPauseLabel(ScanState.FINISHED))
    }

    @Test
    fun aFailedSweepShowsTheHardwareReasonInTheStateLine() {
        val failed =
            snapshot(
                state = ScanState.FAILED,
                sentCount = 0,
                failureMessageBn = SendFailure.NO_IR_HARDWARE.messageBn,
            )

        assertEquals(SendFailure.NO_IR_HARDWARE.messageBn, RemoteText.scanStateLine(failed))
    }

    @Test
    fun foundCodesAreListedWithTheirFrame() {
        val record =
            ScannedCode(RemoteCommand.POWER, 0x00FF, NecAddressMode.EXTENDED, 0x45, 38_000, 1L)

        val sheet = RemoteText.scanFoundSheet(listOf(record))
        assertTrue(sheet.contains(RemoteCommand.POWER.labelBn))
        assertTrue(sheet.contains("0x00FF"))
        assertTrue(sheet.contains("0x45"))
        assertTrue(RemoteText.scanFoundSheet(emptyList()).isNotBlank())
    }

    @Test
    fun theMainScreenLineTellsHowManyCodesWereFound() {
        assertTrue(RemoteText.scannedCodesSummary(0).isNotBlank())
        assertTrue(RemoteText.scannedCodesSummary(2).contains("2"))
        assertTrue(RemoteText.scannedCodesSummary(2).contains("✓"))
    }

    @Test
    fun presetLabelsCarryTheTitleAndTheDetail() {
        ScanPreset.entries.forEach { preset ->
            val label = RemoteText.scanPresetLabel(preset)
            assertTrue(label.contains(preset.titleBn))
            assertTrue(label.contains(preset.detailBn))
        }
    }

    @Test
    fun durationsRoundUpToWholeSecondsAndMinutes() {
        assertEquals("0 সেকেন্ড", RemoteText.durationBn(0))
        assertEquals("1 সেকেন্ড", RemoteText.durationBn(1))
        assertEquals("1 সেকেন্ড", RemoteText.durationBn(1_000))
        assertEquals("2 সেকেন্ড", RemoteText.durationBn(1_001))
        assertEquals("1 মিনিট", RemoteText.durationBn(60_000))
        assertEquals("1 মিনিট 5 সেকেন্ড", RemoteText.durationBn(65_000))
        assertEquals("12 মিনিট 48 সেকেন্ড", RemoteText.durationBn(768_000))
        assertEquals("1.2", RemoteText.secondsBn(1_200))
        assertEquals("0.6", RemoteText.secondsBn(600))
        assertEquals("3.0", RemoteText.secondsBn(3_000))
    }

    @Test
    fun theCodeSheetExplainsWhatAScannedCodeMeans() {
        val scanned =
            RemoteProfiles.DEFAULT.withScannedCodes(
                listOf(ScannedCode(RemoteCommand.POWER, 0x00, NecAddressMode.STANDARD, 0x45, 38_000, 1L)),
            )

        val sheet = RemoteText.codeSheet(scanned)
        assertTrue(sheet.contains(CodeConfidence.SCANNED.labelBn))
        assertTrue(RemoteText.codeSheet(RemoteProfiles.DEFAULT).contains(CodeConfidence.SCANNED.labelBn))
    }

}
