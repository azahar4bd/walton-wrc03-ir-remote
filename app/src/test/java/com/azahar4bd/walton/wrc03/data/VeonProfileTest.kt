package com.azahar4bd.walton.wrc03.data

import com.azahar4bd.walton.wrc03.ir.NecFrame
import com.azahar4bd.walton.wrc03.ir.NecAddressMode
import com.azahar4bd.walton.wrc03.ir.IrRemoteController
import com.azahar4bd.walton.wrc03.ir.IrSendResult
import com.azahar4bd.walton.wrc03.ir.FakeIrHardware
import com.azahar4bd.walton.wrc03.ir.SendFailure
import com.azahar4bd.walton.wrc03.model.CodeConfidence
import com.azahar4bd.walton.wrc03.model.RemoteCommand
import com.azahar4bd.walton.wrc03.model.ScannedCode
import org.junit.Assert.*
import org.junit.Test

class VeonProfileTest {
    private val veon = RemoteProfiles.VEON

    @Test
    fun veonIsDistinctFromWaltonAndNeverClaimsHardwareVerification() {
        assertTrue(RemoteProfiles.ALL.contains(veon))
        assertFalse(veon.verified)
        assertEquals(NecAddressMode.STANDARD, veon.addressMode)
        assertEquals(0x08, veon.address)
        assertEquals(26, veon.commands.size)
        assertTrue(veon.commands.values.all { it.confidence == CodeConfidence.DOCUMENTED })
        assertNotEquals(RemoteProfiles.DEFAULT.commands, veon.commands)
    }

    @Test
    fun decodedPowerAndVolumeHaveTheCorrectOnWireByteOrder() {
        mapOf(RemoteCommand.POWER to listOf(8, 247, 215, 40),
            RemoteCommand.VOLUME_UP to listOf(8, 247, 131, 124),
            RemoteCommand.VOLUME_DOWN to listOf(8, 247, 134, 121)).forEach { (command, wire) ->
            assertEquals(wire, NecFrame(veon.address, veon.codeFor(command)!!.command, veon.addressMode).wireBytes().toList())
        }
    }

    @Test
    fun absentMediaCodesAreNotReplacedWithInventedBytes() {
        val controller = IrRemoteController(FakeIrHardware())
        listOf(RemoteCommand.PREVIOUS, RemoteCommand.NEXT).forEach {
            assertNull(veon.codeFor(it))
            assertEquals(IrSendResult.Failed(SendFailure.UNKNOWN_COMMAND), controller.send(it, veon))
        }
        val scan = ScannedCode(RemoteCommand.NEXT, 8, NecAddressMode.STANDARD, 0x42)
        assertEquals(CodeConfidence.SCANNED, veon.withScannedCodes(listOf(scan)).codeFor(RemoteCommand.NEXT)!!.confidence)
    }
}
