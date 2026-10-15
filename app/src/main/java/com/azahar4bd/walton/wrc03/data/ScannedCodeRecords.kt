package com.azahar4bd.walton.wrc03.data

import com.azahar4bd.walton.wrc03.model.RemoteCommand
import com.azahar4bd.walton.wrc03.model.ScannedCode

/** Android-free storage/migration rules, also used for a scanner's unsaved draft. */
object ScannedCodeRecords {
    fun decode(entries: Map<String, *>): List<ScannedCode> = merge(
        entries.mapNotNull { (key, value) ->
            val command = RemoteCommand.entries.firstOrNull { it.name == key.substringBefore(':') }
                ?: return@mapNotNull null
            val record = ScannedCode.parse(command, value as? String ?: return@mapNotNull null)
                ?: return@mapNotNull null
            record.takeIf { key == command.name || key == it.storageKey() }
        },
    )

    /** Latest confirmation wins within a button/address/carrier, not across TVs. */
    fun merge(records: List<ScannedCode>): List<ScannedCode> =
        records.sortedBy { it.foundAtEpochMs }.associateBy { it.storageKey() }.values.toList()

    fun encode(records: List<ScannedCode>): Map<String, String> =
        merge(records).associate { it.storageKey() to it.serialize() }
}
