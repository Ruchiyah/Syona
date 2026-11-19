package com.example.syona.data.preferences

import com.google.gson.Gson

/**
 * Read-only access to the legacy Gson blob format. Runtime storage now lives in Room/DataStore;
 * this exists only so [com.example.syona.data.legacy.LegacyMigrator] can parse the old state.
 */
object PreferencesManager {
    /** Parses stored JSON and repairs nulls Gson may have injected (see [sanitized]). */
    internal fun parseState(gson: Gson, json: String): PreferencesState =
        (gson.fromJson(json, PreferencesState::class.java) as PreferencesState?)?.sanitized()
            ?: PreferencesState()
}
