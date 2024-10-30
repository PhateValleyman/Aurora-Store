package com.aurora.store.data.room

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * A helper class for doing migrations for the [AuroraDatabase].
 * @see [RoomModule]
 */
object MigrationHelper {

    // ADD ALL NEW MIGRATION STEPS HERE TOO
    val MIGRATION_1_4 = object : Migration(1, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateFrom3To4(db)
            migrateFrom4To5(db)
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) = migrateFrom3To4(db)
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) = migrateFrom4To5(db)
    }

    private const val TAG = "MigrationHelper"

    /**
     * Add targetSdk column to download and update table for checking if silent install is possible.
     */
    private fun migrateFrom3To4(database: SupportSQLiteDatabase) {
        database.beginTransaction()
        try {
            listOf("download", "update").forEach {
                database.execSQL("ALTER TABLE `$it` ADD COLUMN targetSdk INTEGER NOT NULL DEFAULT 1")
            }
            database.setTransactionSuccessful()
        } catch (exception: Exception) {
            Log.e(TAG, "Failed while migrating from database version 3 to 4", exception)
        } finally {
            database.endTransaction()
        }
    }

    /**
     * Add installation-related column to download table for enqueuing installs.
     */
    private fun migrateFrom4To5(database: SupportSQLiteDatabase) {
        database.beginTransaction()
        try {
            database.execSQL("ALTER TABLE `download` ADD COLUMN installer STRING")
            database.execSQL("ALTER TABLE `download` ADD COLUMN sessionId INTEGER")
            database.execSQL("ALTER TABLE `download` ADD COLUMN installProgress INTEGER")
            database.execSQL("ALTER TABLE `download` ADD COLUMN installedAt INTEGER")
            database.setTransactionSuccessful()
        } catch (exception: Exception) {
            Log.e(TAG, "Failed while migrating from database version 4 to 5", exception)
        } finally {
            database.endTransaction()
        }
    }
}
