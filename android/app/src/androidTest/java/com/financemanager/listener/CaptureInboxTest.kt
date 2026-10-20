package com.financemanager.listener

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.financemanager.listener.capture.*
import com.financemanager.listener.data.*
import com.financemanager.listener.parser.NotificationParserDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureInboxTest {
    private inline fun <T> AppDatabase.withDatabase(block: (AppDatabase) -> T): T = try { block(this) } finally { close() }
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun observation(key: String = "key-1", time: Long = 100, owner: String? = "owner-a",
                            text: String = "Persona Demo te envió S/ 12.30") =
        CaptureObservation.posted("com.bcp.innovacxion.yapeapp", key, time, time, System.currentTimeMillis(), owner,
            false, false, NotificationContent(text = text))

    @Test fun committedInboxSurvivesReopenBeforeProcessing() = runBlocking {
        val name = "synthetic-inbox-reopen.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            open().withDatabase { db -> db.captureInboxDao().insert(observation()) }
            open().withDatabase { db ->
                assertEquals(1, db.captureInboxDao().pending().size)
                NotificationInboxProcessor(db, NotificationParserDispatcher()).drain()
                assertEquals(1, db.transactionDao().getUnsyncedTransactions("owner-a").size)
                assertTrue(db.captureInboxDao().pending().isEmpty())
            }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun replayIsDuplicateButNewTimeWithSameActiveSourceRequiresReview() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build().withDatabase { db ->
            val inbox = db.captureInboxDao()
            inbox.insert(observation())
            inbox.insert(observation().copy(recovering = true, ownerKey = "owner-b"))
            val ambiguous = inbox.insert(observation(time = 101))
            NotificationInboxProcessor(db, NotificationParserDispatcher()).drain()
            assertEquals(1, db.transactionDao().getUnsyncedTransactions("owner-a").size)
            assertTrue(db.transactionDao().getUnsyncedTransactions("owner-b").isEmpty())
            assertEquals("REVIEW", inbox.get(ambiguous)?.status)
            assertEquals("REUSED_ACTIVE_SOURCE", inbox.get(ambiguous)?.reason)
            assertNotNull(inbox.get(ambiguous)?.text)
        }
    }
    @Test fun distinctKeysAndCodesCreateSeparatePaymentsWhileSummaryDoesNot() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build().withDatabase { db ->
            val inbox = db.captureInboxDao()
            inbox.insert(observation())
            inbox.insert(observation(key = "key-2"))
            inbox.insert(observation(time = 101, text = "Persona Demo te envió S/ 12.30. El cód. de seguridad es: 222"))
            val summary = inbox.insert(observation(key = "summary").copy(groupSummary = true))
            NotificationInboxProcessor(db, NotificationParserDispatcher()).drain()
            assertEquals(3, db.transactionDao().getUnsyncedTransactions("owner-a").size)
            assertEquals("IGNORED", inbox.get(summary)?.status)
        }
    }
    @Test fun removalAndReconciliationAreOrderedWithCaptures() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build().withDatabase { db ->
            val inbox = db.captureInboxDao()
            inbox.insert(observation())
            inbox.insert(CaptureObservation(observationKey = "remove", kind = "REMOVED", sourceKey = "key-1"))
            inbox.insert(observation(time = 101))
            NotificationInboxProcessor(db, NotificationParserDispatcher()).drain()
            assertEquals(2, db.transactionDao().getUnsyncedTransactions("owner-a").size)
        }
    }
    @Test fun atomicCompletionRollsBackWhenFinancialInsertFails() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build().withDatabase { db ->
            val id = db.captureInboxDao().insert(observation())
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER synthetic_failure BEFORE INSERT ON local_transactions BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
            try { NotificationInboxProcessor(db, NotificationParserDispatcher()).drain(); fail("Expected synthetic failure") } catch (_: android.database.sqlite.SQLiteException) { }
            assertEquals("PENDING", db.captureInboxDao().get(id)?.status)
            assertTrue(db.transactionDao().getUnsyncedTransactions("owner-a").isEmpty())
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER synthetic_failure")
            NotificationInboxProcessor(db, NotificationParserDispatcher()).drain()
            assertEquals("SAVED", db.captureInboxDao().get(id)?.status)
        }
    }
    @Test fun malformedOversizedAndUnsupportedCapturesNeverCreateMovements() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build().withDatabase { db ->
            val inbox = db.captureInboxDao()
            inbox.insert(observation().copy(packageName = "com.android.shell"))
            val limit = inbox.insert(observation().copy(truncated = true))
            val malformed = inbox.insert(observation().copy(lines = "999:x"))
            NotificationInboxProcessor(db, NotificationParserDispatcher()).drain()
            assertTrue(db.transactionDao().getUnsyncedTransactions("owner-a").isEmpty())
            assertEquals("CONTENT_LIMIT", inbox.get(limit)?.reason)
            assertEquals("INVALID_CONTENT_ENCODING", inbox.get(malformed)?.reason)
            assertEquals(2, inbox.counts("owner-a").first().review)
            assertEquals(0, inbox.counts("owner-b").first().review)
        }
    }

    @Test fun retentionRedactsEvidenceWithoutDroppingPendingOrFinancialRows() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build().withDatabase { db ->
            val inbox = db.captureInboxDao()
            val parsed = inbox.insert(observation())
            val ambiguous = inbox.insert(observation(time = 101))
            NotificationInboxProcessor(db, NotificationParserDispatcher()).drain()
            val pending = inbox.insert(observation(key = "unprocessed"))
            inbox.redactExpired(Long.MAX_VALUE)
            assertNull(inbox.get(parsed)?.text)
            assertNull(inbox.get(ambiguous)?.text)
            assertEquals("REVIEW", inbox.get(ambiguous)?.status)
            assertNotNull(inbox.get(pending)?.text)
            assertEquals("PENDING", inbox.get(pending)?.status)
            assertEquals(1, db.transactionDao().getUnsyncedTransactions("owner-a").size)
        }
    }

    @Test fun publicationDateUsesCaptureTimezoneEvenAfterSystemZoneChanges() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build().withDatabase { db ->
            db.captureInboxDao().insert(observation().copy(zoneId = "America/Lima"))
            NotificationInboxProcessor(db, NotificationParserDispatcher()).drain()
            assertEquals("1969-12-31T19:00:00.100", db.transactionDao().getUnsyncedTransactions("owner-a").single().transactionDate)
        }
    }
}
