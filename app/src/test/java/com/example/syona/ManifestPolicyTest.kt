package com.example.syona

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the "fully offline" promise: the merged manifest must never request INTERNET. */
class ManifestPolicyTest {

    @Test
    fun mergedDebugManifestDoesNotRequestInternet() {
        val root = File("build/intermediates")
        val manifest = root.walkTopDown()
            .filter { it.isFile && it.name == "AndroidManifest.xml" }
            .firstOrNull { f -> f.path.contains("merged_manifest") && f.path.contains("debug") }
        assertTrue("Merged debug manifest not found under ${root.absolutePath}", manifest != null)

        val text = manifest!!.readText()
        assertTrue("Unexpected manifest content", text.contains("<manifest"))
        val internet = Regex("""<uses-permission[^>]*android:name\s*=\s*"android\.permission\.INTERNET"""")
        assertFalse("INTERNET permission found in ${manifest.path}", internet.containsMatchIn(text))
    }
}
