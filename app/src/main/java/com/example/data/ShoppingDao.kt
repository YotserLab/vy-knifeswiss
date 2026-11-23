package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_list ORDER BY timestamp DESC")
    fun getAllLists(): Flow<List<ShoppingList>>

    @Insert
    suspend fun insertList(list: ShoppingList): Long

    @Delete
    suspend fun deleteList(list: ShoppingList)

    @Query("SELECT * FROM shopping_item WHERE listId = :listId ORDER BY id ASC")
    fun getItemsForList(listId: Long): Flow<List<ShoppingItem>>

    @Insert
    suspend fun insertItem(item: ShoppingItem): Long
    
    @Insert
    suspend fun insertItems(items: List<ShoppingItem>)

    @Update
    suspend fun updateItem(item: ShoppingItem)

    @Delete
    suspend fun deleteItem(item: ShoppingItem)
    
    @Query("""
        SELECT name FROM shopping_item 
        GROUP BY name 
        ORDER BY COUNT(name) DESC 
        LIMIT 20
    """)
    fun getTopSuggestedItems(): Flow<List<String>>
}
