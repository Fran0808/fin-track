package com.financemanager.listener.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [LocalTransactionEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

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
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wallet_pulse_db"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
