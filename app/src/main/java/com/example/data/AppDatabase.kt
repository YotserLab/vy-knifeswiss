package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.utils.crypto.DatabaseKeyManager
import com.example.transport.ObdSessionEntity
import com.example.transport.ObdLogEntryEntity
import com.example.transport.ObdDao
import net.sqlcipher.database.SupportFactory
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper

@Database(
    entities = [
        HistoryLog::class, 
        MediaPlaylist::class, 
        PlaylistItem::class,
        KanbanColumn::class,
        KanbanNote::class,
        SmartAlarm::class,
        ShoppingList::class,
        ShoppingItem::class,
        VaultEntry::class,
        TotpAccount::class,
        ObdSessionEntity::class,
        ObdLogEntryEntity::class
    ], 
    version = 6, 
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun kanbanDao(): KanbanDao
    abstract fun smartAlarmDao(): SmartAlarmDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun vaultDao(): VaultDao
    abstract fun totpDao(): TotpDao
    abstract fun obdDao(): ObdDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                net.sqlcipher.database.SQLiteDatabase.loadLibs(context.applicationContext) // COF-R01
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "net_toolbox_db"
                )
                .openHelperFactory(SafeSupportFactory(DatabaseKeyManager.getOrCreatePassphrase(context.applicationContext)))
                .fallbackToDestructiveMigration() // PLAT-R12
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Wrapper to recover from SQLCipher "file is not a database" errors
// which occur if the Android Keystore key is lost (e.g. via Auto Backup restore)
class SafeSupportFactory(private val passphrase: ByteArray) : SupportSQLiteOpenHelper.Factory {
    private val factory = SupportFactory(passphrase)

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val helper = factory.create(configuration)
        return object : SupportSQLiteOpenHelper by helper {
            override val writableDatabase: SupportSQLiteDatabase
                get() = try {
                    helper.writableDatabase
                } catch (e: Exception) {
                    if (e is android.database.sqlite.SQLiteException && e.message?.contains("file is not a database") == true) {
                        configuration.context.deleteDatabase(configuration.name)
                        helper.writableDatabase
                    } else throw e
                }

            override val readableDatabase: SupportSQLiteDatabase
                get() = try {
                    helper.readableDatabase
                } catch (e: Exception) {
                    if (e is android.database.sqlite.SQLiteException && e.message?.contains("file is not a database") == true) {
                        configuration.context.deleteDatabase(configuration.name)
                        helper.readableDatabase
                    } else throw e
                }
        }
    }
}
