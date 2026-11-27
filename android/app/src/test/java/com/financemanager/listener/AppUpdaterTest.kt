package com.financemanager.listener

import com.financemanager.listener.update.parseAppUpdateJson
import com.financemanager.listener.update.verifyFileSha256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppUpdaterTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val validSha = "a".repeat(64)

    @Test
    fun parsesNewerValidReleaseAndIgnoresCurrentOrOlderVersions() {
        val json = """
            {
              "versionName": "1.9.0",
              "versionCode": 29,
              "downloadUrl": "https://github.com/Fran0808/fin-track/releases/latest/download/fintrack.apk",
              "sha256": "$validSha"
            }
        """.trimIndent()

        val newer = parseAppUpdateJson(json, currentVersionCode = 28)
        assertNotNull(newer)
        assertEquals("1.9.0", newer?.versionName)
        assertEquals(29, newer?.versionCode)

        assertNull(parseAppUpdateJson(json, currentVersionCode = 29))
        assertNull(parseAppUpdateJson(json, currentVersionCode = 30))
    }

    @Test
    fun rejectsUntrustedDownloadUrlOrInvalidHash() {
        val untrustedUrl = """
            {
              "versionName": "2.0.0",
              "versionCode": 30,
              "downloadUrl": "https://evil.example/fintrack.apk",
              "sha256": "$validSha"
            }
        """.trimIndent()
        assertNull(parseAppUpdateJson(untrustedUrl, currentVersionCode = 28))

        val invalidHash = """
            {
              "versionName": "2.0.0",
              "versionCode": 30,
              "downloadUrl": "https://github.com/Fran0808/fin-track/releases/latest/download/fintrack.apk",
              "sha256": "not-a-hash"
            }
        """.trimIndent()
        assertNull(parseAppUpdateJson(invalidHash, currentVersionCode = 28))
    }

    @Test
    fun verifiesFileSha256DigestAccurately() {
        val file = tempFolder.newFile("sample.apk")
        file.writeText("fintrack-test-payload")
        // SHA-256 of "fintrack-test-payload"
        val expected = java.security.MessageDigest.getInstance("SHA-256")
            .digest("fintrack-test-payload".toByteArray())
            .joinToString("") { "%02x".format(it) }

        assertTrue(verifyFileSha256(file, expected))
        assertFalse(verifyFileSha256(file, "0".repeat(64)))
    }
}
