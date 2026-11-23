package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "vault_entries")
data class VaultEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val username: String = "",
    val encryptedPassword: String = "",
    val url: String = "",
    val notes: String = "",
    val category: String = "login",        // login | card | note | identity | custom
    val customCategory: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_entries ORDER BY isFavorite DESC, title ASC")
    fun getAll(): Flow<List<VaultEntry>>

    @Query("SELECT * FROM vault_entries WHERE title LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY isFavorite DESC, title ASC")
    fun search(query: String): Flow<List<VaultEntry>>

    @Query("SELECT * FROM vault_entries WHERE category = :category ORDER BY isFavorite DESC, title ASC")
    fun getByCategory(category: String): Flow<List<VaultEntry>>

    @Query("SELECT * FROM vault_entries WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavorites(): Flow<List<VaultEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: VaultEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<VaultEntry>)

    @Update
    suspend fun update(entry: VaultEntry)

    @Query("DELETE FROM vault_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM vault_entries")
    suspend fun count(): Int
}
