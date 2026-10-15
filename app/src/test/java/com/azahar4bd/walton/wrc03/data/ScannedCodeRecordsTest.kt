package com.azahar4bd.walton.wrc03.data

import com.azahar4bd.walton.wrc03.ir.NecAddressMode
import com.azahar4bd.walton.wrc03.model.RemoteCommand
import com.azahar4bd.walton.wrc03.model.ScannedCode
import org.junit.Assert.*
import org.junit.Test

class ScannedCodeRecordsTest {
    private val walton = ScannedCode(RemoteCommand.POWER, 0, NecAddressMode.STANDARD, 0x45, foundAtEpochMs = 1)
    private val veon = ScannedCode(RemoteCommand.POWER, 8, NecAddressMode.STANDARD, 0xD7, foundAtEpochMs = 2)

    @Test
    fun legacyKeysMigrateWithoutLosingWaltonWhenSavingVeon() {
        val legacy = ScannedCodeRecords.decode(mapOf("POWER" to walton.serialize()))
        val saved = ScannedCodeRecords.encode(legacy + veon)
        assertEquals(2, saved.size)
        assertFalse(saved.containsKey("POWER"))
        assertEquals(setOf(walton, veon), ScannedCodeRecords.decode(saved).toSet())
    }

    @Test
    fun newestWinsPerAddressEvenIfInputOrderIsReversed() {
        val newer = veon.copy(code = 0x21, foundAtEpochMs = 3)
        assertEquals(setOf(walton, newer), ScannedCodeRecords.merge(listOf(newer, walton, veon)).toSet())
        assertEquals(2, ScannedCodeRecords.merge(listOf(walton, veon, veon)).size)
    }

    @Test
    fun draftsAcrossDifferentTargetsAndPresetsSurviveSerialization() {
        val volume = veon.copy(command = RemoteCommand.VOLUME_UP, code = 0x83)
        val draft = ScannedCodeRecords.merge(listOf(veon) + listOf(volume, walton))
        assertEquals(draft, ScannedCodeRecords.decode(ScannedCodeRecords.encode(draft)))
        assertEquals(3, draft.size)
    }

    @Test
    fun malformedAndMismatchedPreferenceKeysAreIgnored() {
        val result = ScannedCodeRecords.decode(mapOf("POWER" to 123, "MUTE" to "junk",
            "POWER:wrong" to veon.serialize(), "UNKNOWN" to veon.serialize()))
        assertTrue(result.isEmpty())
    }

    @Test
    fun equivalentWireAddressesShareStorageAndOverridesButNotOtherCarriers() {
        val equivalent = veon.copy(address = 0xF708, addressMode = NecAddressMode.EXTENDED)
        assertEquals(veon.storageKey(), equivalent.storageKey())
        assertTrue(equivalent.appliesTo(RemoteProfiles.VEON))
        assertNotEquals(veon.storageKey(), veon.copy(carrierHz = 40000).storageKey())
        assertEquals(RemoteProfiles.ALL, RemoteProfiles.includingDiscovered(listOf(equivalent)))
    }

    @Test
    fun aCommonAddressWithNoShippedProfileIsStillUsableAfterSaving() {
        val discovered = walton.copy(address = 0, addressMode = NecAddressMode.EXTENDED)
        val profiles = RemoteProfiles.includingDiscovered(listOf(walton, veon, discovered))
        assertEquals(RemoteProfiles.ALL.size + 1, profiles.size)
        val active = profiles.last().withScannedCodes(listOf(discovered))
        assertEquals(0x45, active.codeFor(RemoteCommand.POWER)!!.command)
        assertNull(active.codeFor(RemoteCommand.MUTE))
        assertFalse(active.verified)
    }
}
