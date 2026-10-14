package com.azahar4bd.walton.wrc03.data

import android.content.Context
import com.azahar4bd.walton.wrc03.model.RemoteCommand
import com.azahar4bd.walton.wrc03.model.ScannedCode

/**
 * Where the codes found by the scanner live between launches.
 *
 * One shared-preference entry per button (`POWER`, `VOLUME_UP`, …) holding the compact
 * line produced by [ScannedCode.serialize]. Malformed or out-of-range lines are dropped
 * on read, so a corrupted preference can never crash the remote.
 */
class ScannedCodeStore(
    context: Context,
) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Every stored record, in keypad order. */
    fun all(): List<ScannedCode> = RemoteCommand.entries.mapNotNull { forCommand(it) }

    fun forCommand(command: RemoteCommand): ScannedCode? =
        prefs.getString(command.name, null)?.let { raw -> ScannedCode.parse(command, raw) }

    fun save(record: ScannedCode) {
        prefs.edit().putString(record.command.name, record.serialize()).apply()
    }

    fun saveAll(records: List<ScannedCode>) {
        val editor = prefs.edit()
        records.forEach { editor.putString(it.command.name, it.serialize()) }
        editor.apply()
    }

    fun clear(command: RemoteCommand) {
        prefs.edit().remove(command.name).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun count(): Int = all().size

    companion object {
        const val PREFS_NAME = "scanned_codes"
    }
}
