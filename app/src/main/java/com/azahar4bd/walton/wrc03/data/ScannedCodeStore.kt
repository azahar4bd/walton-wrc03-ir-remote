package com.azahar4bd.walton.wrc03.data

import android.content.Context
import com.azahar4bd.walton.wrc03.model.RemoteCommand
import com.azahar4bd.walton.wrc03.model.ScannedCode

/** Per-button AND address storage. Reads v0.2 command-only keys without losing them. */
class ScannedCodeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun all(): List<ScannedCode> = ScannedCodeRecords.decode(prefs.all)

    fun save(record: ScannedCode) = saveAll(listOf(record))

    fun saveAll(records: List<ScannedCode>) = replace(all() + records)

    fun clear(command: RemoteCommand) = replace(all().filterNot { it.command == command })

    fun clearAll() = replace(emptyList())

    fun count(): Int = all().size

    private fun replace(records: List<ScannedCode>) {
        val editor = prefs.edit().clear()
        ScannedCodeRecords.encode(records).forEach { (key, value) -> editor.putString(key, value) }
        editor.apply()
    }

    companion object {
        const val PREFS_NAME = "scanned_codes"
    }
}
