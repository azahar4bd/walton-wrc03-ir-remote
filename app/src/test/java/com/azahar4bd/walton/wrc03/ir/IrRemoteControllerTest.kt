package com.azahar4bd.walton.wrc03.ir

import com.azahar4bd.walton.wrc03.data.RemoteProfiles
import com.azahar4bd.walton.wrc03.model.CodeConfidence
import com.azahar4bd.walton.wrc03.model.CommandGroup
import com.azahar4bd.walton.wrc03.model.IrCode
import com.azahar4bd.walton.wrc03.model.RemoteCommand
import com.azahar4bd.walton.wrc03.model.RemoteProfile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IrRemoteControllerTest {
    private val profile = RemoteProfiles.DEFAULT

    @Test
    fun aPressBeamsTheProfileCodeAt38KHz() {
        val hardware = FakeIrHardware(hasEmitter = true)
        val controller = IrRemoteController(hardware)

        val result = controller.send(RemoteCommand.POWER, profile)

        assertTrue(result is IrSendResult.Sent)
        val sent = result as IrSendResult.Sent
        assertEquals(0x45, sent.code)
        assertEquals(38_000, sent.carrierHz)
        assertEquals(67, sent.pattern.size)
        assertEquals(RemoteCommand.POWER, sent.command)
        assertEquals(38_000, hardware.lastTransmission().carrierFrequencyHz)
        assertTrue(hardware.lastTransmission().pattern.size == 67)
    }

    @Test
    fun everyButtonUsesItsOwnPattern() {
        val hardware = FakeIrHardware(hasEmitter = true)
        val controller = IrRemoteController(hardware)

        val patterns =
            RemoteCommand.entries.associateWith { command ->
                controller.send(command, profile) as IrSendResult.Sent
            }

        assertEquals(RemoteCommand.entries.size, patterns.size)
        assertEquals(
            "two different buttons must not produce the same frame",
            patterns.size,
            patterns.values.map { it.pattern.toList() }.distinct().size,
        )
        patterns.values.forEach { sent -> assertEquals(38_000, sent.carrierHz) }
    }

    @Test
    fun holdingAKeySendsTheThreeEntryRepeatFrame() {
        val hardware = FakeIrHardware(hasEmitter = true)
        val controller = IrRemoteController(hardware)

        val result = controller.sendRepeat(RemoteCommand.VOLUME_UP, profile)

        assertTrue(result is IrSendResult.Sent)
        val sent = result as IrSendResult.Sent
        assertTrue(sent.isRepeat)
        assertEquals(3, sent.pattern.size)
        assertArrayEquals(intArrayOf(9_000, 2_250, 560), sent.pattern)
    }

    @Test
    fun aPhoneWithoutAnEmitterDoesNotSendAnything() {
        val hardware = FakeIrHardware(hasEmitter = false)
        val controller = IrRemoteController(hardware)

        val result = controller.send(RemoteCommand.POWER, profile)

        assertEquals(IrSendResult.Failed(SendFailure.NO_IR_HARDWARE), result)
        assertEquals(0, hardware.transmissions.size)
    }

    @Test
    fun aMissingIrServiceIsReported() {
        val controller = IrRemoteController(null)
        assertEquals(IrSendResult.Failed(SendFailure.NO_IR_HARDWARE), controller.send(RemoteCommand.MUTE, profile))
    }

    @Test
    fun aBlasterWithout38KHzIsRefused() {
        val hardware =
            FakeIrHardware(
                hasEmitter = true,
                carrierRanges = listOf(CarrierRange(56_000, 60_000)),
            )
        val controller = IrRemoteController(hardware)

        assertEquals(
            IrSendResult.Failed(SendFailure.CARRIER_UNSUPPORTED),
            controller.send(RemoteCommand.POWER, profile),
        )
        assertEquals(0, hardware.transmissions.size)
    }

    @Test
    fun aCommandMissingFromTheProfileIsReported() {
        val empty =
            RemoteProfile(
                id = "empty",
                nameBn = "খালি",
                address = 0x00,
                addressMode = NecAddressMode.STANDARD,
                commands = emptyMap(),
                sourceNoteBn = "",
            )
        val controller = IrRemoteController(FakeIrHardware(hasEmitter = true))

        assertEquals(
            IrSendResult.Failed(SendFailure.UNKNOWN_COMMAND),
            controller.send(RemoteCommand.POWER, empty),
        )
    }

    @Test
    fun aDriverExceptionIsTurnedIntoAFailure() {
        val hardware = FakeIrHardware(hasEmitter = true, failure = IllegalStateException("driver gone"))
        val controller = IrRemoteController(hardware)

        assertEquals(
            IrSendResult.Failed(SendFailure.HARDWARE_ERROR),
            controller.send(RemoteCommand.POWER, profile),
        )
    }

    @Test
    fun nothingIsSentWhenTheProfileIsMissingACode() {
        val partial =
            RemoteProfile(
                id = "partial",
                nameBn = "আংশিক",
                address = 0x00,
                addressMode = NecAddressMode.STANDARD,
                commands = mapOf(RemoteCommand.POWER to IrCode(0x45, CodeConfidence.DOCUMENTED)),
                sourceNoteBn = "",
            )
        assertTrue(partial.codeFor(RemoteCommand.POWER) != null)
        assertNull(partial.codeFor(RemoteCommand.MUTE))
    }

    @Test
    fun theControllerReportsHardwareStateForTheProfileCarrier() {
        val controller = IrRemoteController(FakeIrHardware(hasEmitter = true))
        assertTrue(controller.availability(profile.carrierHz) is IrAvailability.Ready)
    }

    @Test
    fun groupsCoverEveryCommandInTheProfiles() {
        RemoteCommand.entries.forEach { command ->
            assertTrue("${command.name} must be part of a section", CommandGroup.entries.any { it == command.group })
        }
    }
}
