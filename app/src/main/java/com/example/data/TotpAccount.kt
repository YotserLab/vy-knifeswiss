package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "totp_accounts")
data class TotpAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val issuer: String,
    val accountName: String,
    val encryptedSecret: String,
    val algorithm: String = "SHA1",   // SHA1 | SHA256 | SHA512
    val digits: Int = 6,
    val period: Int = 30,
    val iconColor: String = "#00D4FF",
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface TotpDao {
    @Query("SELECT * FROM totp_accounts ORDER BY position ASC, createdAt ASC")
    fun getAll(): Flow<List<TotpAccount>>

    @Query("SELECT * FROM totp_accounts")
    suspend fun getAllList(): List<TotpAccount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: TotpAccount): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<TotpAccount>)

    @Update
    suspend fun update(account: TotpAccount)

    @Query("DELETE FROM totp_accounts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM totp_accounts")
    suspend fun deleteAll()

    @Query("UPDATE totp_accounts SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)
}
