package com.financemanager.listener

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BackupPolicyTest {
    private fun xml(path: String) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File("src/main/$path"))

    private fun assertExclusions(container: Element) {
        val excludes = container.getElementsByTagName("exclude")
        val rules = (0 until excludes.length).map { excludes.item(it) as Element }
            .map { it.getAttribute("domain") to it.getAttribute("path") }.toSet()
        assertTrue(rules.contains("sharedpref" to "wallet_pulse_pairing_prefs.xml"))
        for (suffix in listOf("", "-wal", "-shm", "-journal")) {
            assertTrue(rules.contains("database" to "wallet_pulse_db$suffix"))
        }
    }

    @Test fun credentialsAndFinancialDatabaseAreExcludedInLegacyBackupRules() {
        assertExclusions(xml("res/xml/backup_rules.xml").documentElement)
    }

    @Test fun credentialsAreExcludedFromBothCloudBackupAndDeviceTransfer() {
        val document = xml("res/xml/data_extraction_rules.xml")
        for (mode in listOf("cloud-backup", "device-transfer")) {
            assertExclusions(document.getElementsByTagName(mode).item(0) as Element)
        }
    }

    @Test fun applicationDisablesBackupAndReferencesTheReviewedPolicies() {
        val app = xml("AndroidManifest.xml").getElementsByTagName("application").item(0) as Element
        val android = "http://schemas.android.com/apk/res/android"
        assertEquals("false", app.getAttributeNS(android, "allowBackup"))
        assertEquals("@xml/backup_rules", app.getAttributeNS(android, "fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", app.getAttributeNS(android, "dataExtractionRules"))
        assertEquals("@xml/network_security_config", app.getAttributeNS(android, "networkSecurityConfig"))
    }
}
