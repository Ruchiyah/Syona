package com.example.syona.data.legacy

interface MigrationSink {
    /** Writes the whole bundle in one DB transaction with idempotent (id-keyed) semantics. */
    suspend fun writeAll(bundle: MigrationBundle, photoPath: String?)
}
