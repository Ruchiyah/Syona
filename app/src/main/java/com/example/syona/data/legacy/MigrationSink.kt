package com.example.syona.data.legacy

interface MigrationSink {
    /**
     * Writes the whole bundle in one DB transaction with add-only semantics: rows that already
     * exist (e.g. created or edited by the user after a failed run) are never overwritten (R8).
     */
    suspend fun writeAll(bundle: MigrationBundle, photoPath: String?)
}
