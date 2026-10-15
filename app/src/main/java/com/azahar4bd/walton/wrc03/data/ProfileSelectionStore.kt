package com.azahar4bd.walton.wrc03.data

import android.content.Context

class ProfileSelectionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("remote_settings", Context.MODE_PRIVATE)

    var selectedId: String
        get() = prefs.getString("profile_id", null) ?: RemoteProfiles.DEFAULT.id
        set(value) { prefs.edit().putString("profile_id", value).apply() }
}
