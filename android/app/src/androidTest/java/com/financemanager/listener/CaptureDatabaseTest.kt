package com.financemanager.listener

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.financemanager.listener.data.AppDatabase
import com.financemanager.listener.data.LocalTransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureDatabaseTest {
    private inline fun <T> AppDatabase.withDatabase(block: (AppDatabase) -> T): T = try { block(this) } finally { close() }
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun entry(hash: String = "event-1", key: String = "key-1", owner: String? = "owner-a") = LocalTransactionEntity(
        amount = "50.00", flowType = "INCOME", contactName = "Test Sender", transactionDate = "2026-10-07T12:00:00",
        transactionHash = hash, rawText = "Test Sender te envió S/ 50.00", ownerKey = owner,
        sourceEventId = hash, sourceKey = key, sourceFingerprint = "content", sourceActive = true
    )

    @Test fun recoveryUpdatesAndDistinctNotificationsAreIdempotent() {
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build().withDatabase { db ->
            val dao = db.transactionDao()
            assertTrue(dao.persistNotification(entry(), true) > 0)
            assertEquals(-1L, dao.persistNotification(entry(), true))
            assertEquals(-1L, dao.persistNotification(entry(hash = "updated-time"), false))
            assertTrue(dao.persistNotification(entry(hash = "event-2", key = "key-2"), false) > 0)
            dao.retireSource("key-1")
            assertTrue(dao.persistNotification(entry(hash = "event-3"), false) > 0)
            assertEquals(3, dao.getUnsyncedTransactions("owner-a").size)
        }
    }

    @Test fun replayCannotAssignOldEventToAnotherOwner() {
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build().withDatabase { db ->
            val dao = db.transactionDao()
            dao.persistNotification(entry(), false)
            assertEquals(-1L, dao.persistNotification(entry(owner = "owner-b"), true))
            assertTrue(dao.getUnsyncedTransactions("owner-b").isEmpty())
            dao.markAsSynced("event-1", "owner-b")
            assertEquals(1, dao.getUnsyncedTransactions("owner-a").size)
        }
    }

    @Test fun legacyRecoveryPreservesHashAndRequiresExplicitOwnerReview() {
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build().withDatabase { db ->
            val dao = db.transactionDao()
            val id = dao.insert(entry(hash = "legacy-hash", owner = null).copy(sourceEventId = null, sourceKey = null, sourceFingerprint = null, sourceActive = false))
            assertEquals(-1L, dao.persistNotification(entry(), true))
            val saved = requireNotNull(dao.findEvent("event-1"))
            assertEquals("legacy-hash", saved.transactionHash)
            assertNull(saved.ownerKey)
            assertTrue(dao.getUnsyncedTransactions("owner-a").isEmpty())
            assertEquals(1, dao.assignReviewedEntry(id, "owner-a"))
            assertEquals(0, dao.assignReviewedEntry(id, "owner-b"))
            assertEquals("legacy-hash", dao.getUnsyncedTransactions("owner-a").single().transactionHash)
        }
    }

    @Test fun pendingCountIncludesRecordsOutsideVisibleFifty() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build().withDatabase { db ->
            val dao = db.transactionDao()
            dao.insert(entry().copy(createdAt = 0))
            repeat(51) { dao.insert(entry("synced-$it", "key-$it").copy(isSynced = true, createdAt = it + 1L)) }
            assertEquals(50, dao.getRecentTransactionsFlow("owner-a").first().size)
            assertEquals(1, dao.pendingCount("owner-a").first())
        }
    }

    @Test fun migrationPreservesExistingRowsAndRoomValidatesSchema() = runBlocking {
        val name = "capture-migration-test.db"
        context.deleteDatabase(name)
        try {
            val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE local_transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, amount TEXT NOT NULL, flowType TEXT NOT NULL, contactName TEXT NOT NULL, channel TEXT NOT NULL, transactionDate TEXT NOT NULL, transactionHash TEXT NOT NULL, rawText TEXT NOT NULL, isSynced INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                        db.execSQL("CREATE UNIQUE INDEX index_local_transactions_transactionHash ON local_transactions(transactionHash)")
                        db.execSQL("INSERT INTO local_transactions VALUES (1, '50.00', 'INCOME', 'Test Sender', 'YAPE', '2026-10-07T12:00:00', 'old-hash', 'test', 0, 1)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build())
            helper.writableDatabase
            helper.close()
            Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_1_2).build().withDatabase { db ->
                val saved = db.transactionDao().unassignedTransactions().first().single()
                assertEquals("old-hash", saved.transactionHash)
                assertEquals("50.00", saved.amount)
                assertFalse(saved.isSynced)
                assertNull(saved.ownerKey)
                assertNull(saved.sourceEventId)
                assertFalse(saved.sourceActive)
            }
        } finally { context.deleteDatabase(name) }
    }
}
