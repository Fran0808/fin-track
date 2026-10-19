package com.financemanager.listener.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [LocalTransactionEntity::class, CaptureObservation::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun captureInboxDao(): CaptureInboxDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Legacy rows have no proven owner. Preserve them without assigning a new account.
                db.execSQL("ALTER TABLE local_transactions ADD COLUMN ownerKey TEXT")
                db.execSQL("ALTER TABLE local_transactions ADD COLUMN sourceEventId TEXT")
                db.execSQL("ALTER TABLE local_transactions ADD COLUMN sourceKey TEXT")
                db.execSQL("ALTER TABLE local_transactions ADD COLUMN sourceFingerprint TEXT")
                db.execSQL("ALTER TABLE local_transactions ADD COLUMN sourceActive INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE UNIQUE INDEX index_local_transactions_sourceEventId ON local_transactions(sourceEventId)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS capture_inbox (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, observationKey TEXT NOT NULL,
                    kind TEXT NOT NULL, packageName TEXT NOT NULL, sourceKey TEXT NOT NULL,
                    postTime INTEGER NOT NULL, eventTime INTEGER NOT NULL, receivedAt INTEGER NOT NULL,
                    ownerKey TEXT, recovering INTEGER NOT NULL, groupSummary INTEGER NOT NULL,
                    truncated INTEGER NOT NULL, title TEXT, text TEXT, bigText TEXT, lines TEXT NOT NULL,
                    status TEXT NOT NULL, reason TEXT, transactionHash TEXT, zoneId TEXT NOT NULL)""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_capture_inbox_observationKey ON capture_inbox(observationKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_capture_inbox_status_id ON capture_inbox(status, id)")
            }
        }
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wallet_pulse_db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
