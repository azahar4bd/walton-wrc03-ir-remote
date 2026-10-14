package com.azahar4bd.walton.wrc03.scan

import com.azahar4bd.walton.wrc03.data.RemoteProfiles
import com.azahar4bd.walton.wrc03.ir.FakeIrHardware
import com.azahar4bd.walton.wrc03.ir.IrRemoteController
import com.azahar4bd.walton.wrc03.ir.NecEncoder
import com.azahar4bd.walton.wrc03.ir.SendFailure
import com.azahar4bd.walton.wrc03.model.RemoteCommand
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the sweep with a hand-cranked scheduler and a fake emitter, so every branch —
 * pulses, pause, skip, "found it", hardware failure — is checked without a phone.
 */
class CodeScanEngineTest {
    private val profile = RemoteProfiles.DEFAULT

    private class Harness(
        val engine: CodeScanEngine,
        val scheduler: ManualScanScheduler,
        val hardware: FakeIrHardware,
    ) {
        val plan: ScanPlan get() = engine.plan
    }

    private fun harness(
        preset: ScanPreset = ScanPreset.QUICK,
        target: RemoteCommand = RemoteCommand.POWER,
        intervalMs: Long = CodeScanEngine.DEFAULT_INTERVAL_MS,
        pulsesPerCandidate: Int = CodeScanEngine.PULSES_PER_CANDIDATE,
        hasEmitter: Boolean = true,
    ): Harness {
        val scheduler = ManualScanScheduler()
        val hardware = FakeIrHardware(hasEmitter = hasEmitter)
        val controller = IrRemoteController(hardware)
        val engine =
            CodeScanEngine(
                plan = preset.planFor(target, profile),
                transmitter =
                    ScanTransmitter { candidate ->
                        controller.send(target, candidate.frame(), candidate.carrierHz)
                    },
                scheduler = scheduler,
                initialIntervalMs = intervalMs,
                pulsesPerCandidate = pulsesPerCandidate,
            )
        return Harness(engine, scheduler, hardware)
    }

    @Test
    fun startBeamsTheFirstCandidateAndWaitsBeforeTheNext() {
        val h = harness(pulsesPerCandidate = 1)

        val snapshot = h.engine.start()

        assertEquals(ScanState.RUNNING, snapshot.state)
        assertEquals(0, h.engine.cursor)
        assertEquals(1, snapshot.sentCount)
        assertEquals(1, h.hardware.transmissions.size)
        assertArrayEquals(
            NecEncoder.encodeFrame(h.plan.candidates[0].frame()),
            h.hardware.transmissions[0].pattern,
        )
        assertEquals(38_000, h.hardware.transmissions[0].carrierFrequencyHz)
        assertEquals(CodeScanEngine.DEFAULT_INTERVAL_MS, h.scheduler.pendingDelayMs)
    }

    @Test
    fun eachCandidateIsSentTwiceLikeAHeldKey() {
        val h = harness()

        h.engine.start()
        assertEquals(1, h.hardware.transmissions.size)
        assertEquals(CodeScanEngine.PULSE_GAP_MS, h.scheduler.pendingDelayMs)

        h.scheduler.runPending()
        assertEquals(2, h.hardware.transmissions.size)
        assertArrayEquals(
            h.hardware.transmissions[0].pattern,
            h.hardware.transmissions[1].pattern,
        )
        assertEquals(CodeScanEngine.DEFAULT_INTERVAL_MS, h.scheduler.pendingDelayMs)
        assertEquals("pulses must not count as candidates", 1, h.engine.snapshot().sentCount)
    }

    @Test
    fun everyTickMovesToTheNextCandidate() {
        val h = harness(pulsesPerCandidate = 1)
        h.engine.start()

        h.scheduler.runPending()
        assertEquals(1, h.engine.cursor)
        h.scheduler.runPending()
        assertEquals(2, h.engine.cursor)
        assertEquals(3, h.hardware.transmissions.size)

        val wires = h.hardware.transmissions.map { it.pattern.toList() }
        assertEquals("consecutive candidates must differ", 3, wires.distinct().size)
    }

    @Test
    fun drainingTheSchedulerFinishesTheWholeSweep() {
        val h = harness(pulsesPerCandidate = 1)

        h.engine.start()
        h.scheduler.drain()

        assertEquals(ScanState.FINISHED, h.engine.state)
        assertFalse(h.scheduler.hasPending)
        assertEquals(h.plan.size, h.hardware.transmissions.size)

        val snapshot = h.engine.snapshot()
        assertEquals(h.plan.size, snapshot.sentCount)
        assertEquals(h.plan.size, snapshot.total)
        assertEquals(0L, snapshot.remainingMs)
        assertEquals(1f, snapshot.progress, 0.0001f)
    }

    @Test
    fun pauseStopsTheSweepAndResumePicksUpWhereItLeftOff() {
        val h = harness(pulsesPerCandidate = 1)
        h.engine.start()

        h.engine.pause()
        assertEquals(ScanState.PAUSED, h.engine.state)
        assertFalse(h.scheduler.hasPending)

        h.scheduler.scheduledDelays.clear()
        h.engine.resume()
        assertEquals(ScanState.RUNNING, h.engine.state)
        assertEquals(CodeScanEngine.DEFAULT_INTERVAL_MS, h.scheduler.pendingDelayMs)

        h.scheduler.runPending()
        assertEquals(1, h.engine.cursor)
    }

    @Test
    fun aStaleTickCannotSendAnythingAfterAPause() {
        val scheduler = LeakyScheduler()
        val hardware = FakeIrHardware()
        val controller = IrRemoteController(hardware)
        val engine =
            CodeScanEngine(
                plan = ScanPreset.QUICK.planFor(RemoteCommand.POWER, profile),
                transmitter =
                    ScanTransmitter { controller.send(RemoteCommand.POWER, it.frame(), it.carrierHz) },
                scheduler = scheduler,
                pulsesPerCandidate = 1,
            )

        engine.start()
        engine.pause()
        assertEquals(1, hardware.transmissions.size)

        // A Handler tick that was already in flight must not beam anything.
        scheduler.fireLast()

        assertEquals(1, hardware.transmissions.size)
        assertEquals(ScanState.PAUSED, engine.state)
        assertEquals(0, engine.cursor)
    }

    @Test
    fun markFoundRecordsTheCandidateInTheAirAndPauses() {
        val h = harness(pulsesPerCandidate = 1)
        h.engine.start()
        val candidate = h.plan.candidates[0]

        val snapshot = h.engine.markFound(1_700_000_000_000L)

        assertEquals(ScanState.PAUSED, snapshot.state)
        assertEquals(1, snapshot.found.size)
        val record = snapshot.found.single()
        assertEquals(RemoteCommand.POWER, record.command)
        assertEquals(candidate.address, record.address)
        assertEquals(candidate.addressMode, record.addressMode)
        assertEquals(candidate.commandByte, record.code)
        assertEquals(candidate.carrierHz, record.carrierHz)
        assertEquals(1_700_000_000_000L, record.foundAtEpochMs)
        assertFalse("the TV must not be blasted while the user reads", h.scheduler.hasPending)
    }

    @Test
    fun markingTheSameCandidateTwiceKeepsOneRecord() {
        val h = harness(pulsesPerCandidate = 1)
        h.engine.start()

        h.engine.markFound(1L)
        h.engine.resume()
        h.engine.markFound(2L)

        assertEquals(1, h.engine.found.size)
        assertEquals("the first hit wins", 1L, h.engine.found.single().foundAtEpochMs)
    }

    @Test
    fun severalButtonsCanBeCollectedInOneSweep() {
        val h = harness(pulsesPerCandidate = 1)
        h.engine.start()

        h.engine.markFound(1L)
        h.engine.resume()
        h.scheduler.runPending() // candidate 1
        h.engine.markFound(2L)

        assertEquals(2, h.engine.found.size)
        assertEquals(
            2,
            h.engine.found.map { it.code to it.address to it.addressMode }.distinct().size,
        )
    }

    @Test
    fun markFoundBeforeAnythingWasSentIsIgnored() {
        val h = harness()

        val snapshot = h.engine.markFound(5L)

        assertEquals(ScanState.IDLE, snapshot.state)
        assertTrue(snapshot.found.isEmpty())
        assertFalse(snapshot.canMarkFound)
    }

    @Test
    fun skipJumpsAheadWithoutWaitingForTheInterval() {
        val h = harness(pulsesPerCandidate = 1)
        h.engine.start()

        h.engine.skip()

        assertEquals(1, h.engine.cursor)
        assertEquals(2, h.hardware.transmissions.size)
    }

    @Test
    fun stopResetsTheCursorButKeepsWhatWasFound() {
        val h = harness(pulsesPerCandidate = 1)
        h.engine.start()
        h.engine.markFound(9L)

        val snapshot = h.engine.stop()

        assertEquals(ScanState.IDLE, snapshot.state)
        assertEquals(-1, h.engine.cursor)
        assertEquals(0, snapshot.sentCount)
        assertNull(snapshot.candidate)
        assertEquals(1, h.engine.found.size)
        assertFalse(h.scheduler.hasPending)
    }

    @Test
    fun startAfterAFinishedSweepWalksTheWholeListAgain() {
        val h = harness(pulsesPerCandidate = 1)
        h.engine.start()
        h.scheduler.drain()
        assertEquals(ScanState.FINISHED, h.engine.state)
        val firstPass = h.hardware.transmissions.size

        h.engine.start()
        h.scheduler.drain()

        assertEquals(ScanState.FINISHED, h.engine.state)
        assertEquals(firstPass * 2, h.hardware.transmissions.size)
    }

    @Test
    fun aPhoneWithoutAnEmitterFailsTheSweepInBengali() {
        val h = harness(hasEmitter = false)

        val snapshot = h.engine.start()

        assertEquals(ScanState.FAILED, snapshot.state)
        assertEquals(SendFailure.NO_IR_HARDWARE.messageBn, snapshot.failureMessageBn)
        assertEquals(0, h.hardware.transmissions.size)
        assertFalse(h.scheduler.hasPending)
        assertTrue(snapshot.isFinished)
    }

    @Test
    fun aFailedSweepCanBeRestartedButStillReportsTheHardware() {
        val h = harness(hasEmitter = false)
        h.engine.start()
        assertEquals(ScanState.FAILED, h.engine.state)

        h.engine.start()

        assertEquals(ScanState.FAILED, h.engine.state)
        assertEquals(SendFailure.NO_IR_HARDWARE.messageBn, h.engine.snapshot().failureMessageBn)
        assertEquals(0, h.hardware.transmissions.size)
    }

    @Test
    fun theListenerSeesEveryStateChange() {
        val h = harness(pulsesPerCandidate = 1)
        val seen = mutableListOf<ScanState>()
        h.engine.listener = { seen += it.state }

        h.engine.start()
        h.scheduler.runPending()
        h.engine.pause()
        h.engine.resume()
        h.scheduler.drain()

        assertEquals(ScanState.RUNNING, seen.first())
        assertEquals(ScanState.FINISHED, seen.last())
        assertTrue(seen.contains(ScanState.PAUSED))
        assertTrue(seen.size >= 4)
    }

    @Test
    fun theIntervalCanBeRetunedWhileTheSweepRuns() {
        val h = harness(pulsesPerCandidate = 1)
        h.engine.start()

        h.engine.intervalMs = 2_500L
        h.scheduler.runPending()

        assertEquals(2_500L, h.scheduler.pendingDelayMs)
        assertEquals(2_500L, h.engine.snapshot().intervalMs)
    }

    @Test
    fun theRemainingTimeShrinksAsTheSweepRuns() {
        val h = harness(pulsesPerCandidate = 1)
        val before = h.engine.snapshot().remainingMs

        h.engine.start()
        val afterFirst = h.engine.snapshot().remainingMs

        assertTrue(afterFirst < before)
        assertEquals((h.plan.size - 1) * CodeScanEngine.DEFAULT_INTERVAL_MS, afterFirst)

        h.scheduler.drain()
        assertEquals(0L, h.engine.snapshot().remainingMs)
    }

    @Test
    fun twoPulsesPerCandidateAreBudgetedIntoTheRemainingTime() {
        val h = harness(pulsesPerCandidate = 2)
        val expected =
            h.plan.size * (CodeScanEngine.DEFAULT_INTERVAL_MS + CodeScanEngine.PULSE_GAP_MS)

        assertEquals(expected, h.engine.snapshot().remainingMs)
    }

    @Test
    fun theTargetButtonIsPartOfEverySnapshot() {
        val h = harness(target = RemoteCommand.VOLUME_UP, preset = ScanPreset.CURRENT_ADDRESS_FULL)

        assertEquals(RemoteCommand.VOLUME_UP, h.engine.snapshot().target)
        assertEquals(256, h.engine.snapshot().total)
    }

    @Test
    fun anIntervalOutsideTheSaneRangeIsRejected() {
        val tooFast =
            runCatching { harness(intervalMs = CodeScanEngine.MIN_INTERVAL_MS - 1) }
        val tooSlow =
            runCatching { harness(intervalMs = CodeScanEngine.MAX_INTERVAL_MS + 1) }

        assertTrue(tooFast.exceptionOrNull() is IllegalArgumentException)
        assertTrue(tooSlow.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun anEmptyPlanFinishesImmediately() {
        val scheduler = ManualScanScheduler()
        val hardware = FakeIrHardware()
        val controller = IrRemoteController(hardware)
        val engine =
            CodeScanEngine(
                plan = ScanPlan(RemoteCommand.POWER, "খালি", emptyList()),
                transmitter = ScanTransmitter { controller.send(RemoteCommand.POWER, it.frame(), it.carrierHz) },
                scheduler = scheduler,
            )

        val snapshot = engine.start()

        assertEquals(ScanState.FINISHED, snapshot.state)
        assertEquals(0, hardware.transmissions.size)
        assertEquals(0f, snapshot.progress, 0.0001f)
        assertFalse(snapshot.canMarkFound)
    }

    /** Keeps callbacks even after [cancel], so a test can replay a late tick. */
    private class LeakyScheduler : ScanScheduler {
        val actions = mutableListOf<() -> Unit>()

        override fun schedule(
            delayMs: Long,
            action: () -> Unit,
        ) {
            actions += action
        }

        override fun cancel() = Unit

        fun fireLast() = actions.last().invoke()
    }
}
