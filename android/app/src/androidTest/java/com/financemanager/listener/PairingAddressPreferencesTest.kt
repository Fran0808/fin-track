package com.financemanager.listener

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.financemanager.listener.data.AppDatabase
import com.financemanager.listener.data.LocalTransactionEntity
import com.financemanager.listener.data.PairingPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PairingAddressPreferencesTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "pairing_address_test_${UUID.randomUUID()}_"
    private val preferenceNames = mutableSetOf<String>()
    private val context = object : ContextWrapper(target) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolatedName = prefix + name
            preferenceNames.add(isolatedName)
            return target.getSharedPreferences(isolatedName, mode)
        }
    }
    private val oldAddress = "http://192.0.2.10:8080"
    private val newAddress = "http://192.0.2.20:8080"

    @After fun removeDisposablePreferences() {
        preferenceNames.forEach { target.deleteSharedPreferences(it) }
    }

    @Test fun correctedAddressKeepsPendingHashesAndOwnershipWithoutRewritingRows() {
        PairingPreferences.savePairing(context, "test-token", oldAddress, "test@example.test", 1)
        val original = requireNotNull(PairingPreferences.session(context))
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.transactionDao()
            val entry = LocalTransactionEntity(amount = "50.00", flowType = "INCOME", contactName = "Test Sender",
                transactionDate = "2026-10-16T12:00:00", transactionHash = "pending-hash", rawText = "test", ownerKey = original.ownerKey)
            dao.insert(entry)
            dao.insert(entry.copy(transactionHash = "another-owner-hash", ownerKey = "another-owner"))
            dao.insert(entry.copy(transactionHash = "unassigned-hash", ownerKey = null))
            PairingPreferences.updateVerifiedServer(context, original, newAddress, 1)
            val updated = requireNotNull(PairingPreferences.session(context))
            assertEquals(newAddress + "/", updated.serverUrl)
            assertEquals(original.ownerKey, updated.ownerKey)
            assertEquals(original.token, updated.token)
            assertEquals(original.startedAt, updated.startedAt)
            val pending = dao.getUnsyncedTransactions(updated.ownerKey).single()
            assertEquals("pending-hash", pending.transactionHash)
            assertFalse(pending.isSynced)
            assertEquals(1, dao.getUnsyncedTransactions("another-owner").size)
            assertEquals("unassigned-hash", runBlocking { dao.unassignedTransactions().first() }.single().transactionHash)
        } finally {
            db.close()
        }
    }

    @Test fun addressHistorySurvivesUnpairingAndCredentialRotationButIsScopedToUserAndServer() {
        PairingPreferences.savePairing(context, "test-token", oldAddress, "test@example.test", 1)
        val original = requireNotNull(PairingPreferences.session(context))
        PairingPreferences.updateVerifiedServer(context, original, newAddress, 1)
        PairingPreferences.clearPairing(context)
        assertNull(PairingPreferences.session(context))
        assertNull(PairingPreferences.getPairingToken(context))
        assertNull(PairingPreferences.getPairedEmail(context))
        PairingPreferences.savePairing(context, "rotated-test-token", newAddress, "test@example.test", 1)
        assertEquals(original.ownerKey, PairingPreferences.session(context)?.ownerKey)
        PairingPreferences.savePairing(context, "other-user-token", newAddress, "other@example.test", 2)
        assertNotEquals(original.ownerKey, PairingPreferences.session(context)?.ownerKey)
        PairingPreferences.savePairing(context, "other-server-token", "https://example.test", "test@example.test", 1)
        assertNotEquals(original.ownerKey, PairingPreferences.session(context)?.ownerKey)
    }

    @Test fun mismatchedUserAndStaleVerificationLeavePairingUnchanged() {
        PairingPreferences.savePairing(context, "test-token", oldAddress, "test@example.test", 1)
        val original = requireNotNull(PairingPreferences.session(context))
        assertThrows(IllegalArgumentException::class.java) {
            PairingPreferences.updateVerifiedServer(context, original, newAddress, 2)
        }
        assertEquals(original, PairingPreferences.session(context))
        PairingPreferences.clearPairing(context)
        assertThrows(IllegalStateException::class.java) {
            PairingPreferences.updateVerifiedServer(context, original, newAddress, 1)
        }
        assertNull(PairingPreferences.session(context))
    }
}
