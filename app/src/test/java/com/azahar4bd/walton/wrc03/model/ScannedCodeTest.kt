package com.azahar4bd.walton.wrc03.model

import com.azahar4bd.walton.wrc03.data.RemoteProfiles
import com.azahar4bd.walton.wrc03.ir.NecAddressMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * A scanned record is the only user-generated data in the app, and it is stored as a
 * flat line in shared preferences — so parsing has to survive garbage.
 */
class ScannedCodeTest {
    private val power =
        ScannedCode(
            command = RemoteCommand.POWER,
            address = 0x00FF,
            addressMode = NecAddressMode.EXTENDED,
            code = 0x45,
            carrierHz = 38_000,
            foundAtEpochMs = 1_700_000_000_000L,
        )

    @Test
    fun aRecordSurvivesTheTripThroughSharedPreferences() {
        val line = power.serialize()

        assertTrue(line.isNotBlank())
        assertFalse("no newlines, it is stored as one line", line.contains("\n"))
        assertEquals(power, ScannedCode.parse(RemoteCommand.POWER, line))
    }

    @Test
    fun everyButtonRoundTrips() {
        RemoteCommand.entries.forEach { command ->
            val record = ScannedCode(command, 0x00, NecAddressMode.STANDARD, 0x1A, 38_000, 7L)
            assertEquals(command, ScannedCode.parse(command, record.serialize())?.command)
            assertEquals(record, ScannedCode.parse(command, record.serialize()))
        }
    }

    @Test
    fun garbageLinesAreDroppedInsteadOfThrowing() {
        listOf(
            "",
            " ",
            "0",
            "0|EXTENDED",
            "0|EXTENDED|70|38000",
            "0|EXTENDED|70|38000|1|extra",
            "0|WIDE|70|38000|1",
            "a|EXTENDED|70|38000|1",
            "0|EXTENDED|zz|38000|1",
            "0|EXTENDED|70|fast|1",
            "0|EXTENDED|70|38000|soon",
            "0|EXTENDED|999|38000|1",
            "0|EXTENDED|-3|38000|1",
            "999|STANDARD|70|38000|1",
            "-1|EXTENDED|70|38000|1",
            "0|EXTENDED|70|0|1",
            "0|EXTENDED|70|-38000|1",
        ).forEach { raw ->
            assertNull("'$raw' should not parse", ScannedCode.parse(RemoteCommand.POWER, raw))
        }
    }

    @Test
    fun outOfRangeValuesAreRejectedWhenBuilt() {
        listOf<Pair<String, () -> ScannedCode>>(
            "command byte too wide" to { ScannedCode(RemoteCommand.POWER, 0x00, NecAddressMode.STANDARD, 0x1FF) },
            "negative command byte" to { ScannedCode(RemoteCommand.POWER, 0x00, NecAddressMode.STANDARD, -1) },
            "8 bit address too wide" to { ScannedCode(RemoteCommand.POWER, 0x1FF, NecAddressMode.STANDARD, 0x45) },
            "16 bit address too wide" to { ScannedCode(RemoteCommand.POWER, 0x1FFFF, NecAddressMode.EXTENDED, 0x45) },
        ).forEach { (name, build) ->
            try {
                build()
                fail("$name should have been rejected")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().isNotBlank())
            }
        }
    }

    @Test
    fun aRecordOnlyAppliesToTheAddressBlockItWasFoundUnder() {
        assertTrue(power.appliesTo(RemoteProfiles.PROFILE_B))
        assertFalse(power.appliesTo(RemoteProfiles.PROFILE_A))
        assertFalse(power.appliesTo(RemoteProfiles.PROFILE_C))

        val otherCarrier = power.copy(carrierHz = 40_000)
        assertFalse(otherCarrier.appliesTo(RemoteProfiles.PROFILE_B))
    }

    @Test
    fun hexLabelsAreZeroPadded() {
        assertEquals("0x00FF", power.addressHex())
        assertEquals("0x45", power.codeHex())

        val standard = ScannedCode(RemoteCommand.MUTE, 0x00, NecAddressMode.STANDARD, 0x07)
        assertEquals("0x00", standard.addressHex())
        assertEquals("0x07", standard.codeHex())
    }

    @Test
    fun aScannedRecordBecomesAScannedIrCode() {
        val code = power.toIrCode()

        assertEquals(0x45, code.command)
        assertEquals(CodeConfidence.SCANNED, code.confidence)
        assertEquals("স্ক্যানে পাওয়া", code.confidence.labelBn)
    }
}
