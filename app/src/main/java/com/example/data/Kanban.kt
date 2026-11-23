package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "kanban_columns")
data class KanbanColumn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int // PROD-R01
)

@Entity(tableName = "kanban_notes")
data class KanbanNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val columnId: Long,
    val position: Int = 0,
    val colorHex: String = "#FFF9C4", // PROD-R02, PROD-R03 default alert sticky color
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface KanbanDao {
    @Query("SELECT * FROM kanban_columns ORDER BY position ASC")
    fun getAllColumns(): Flow<List<KanbanColumn>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertColumn(column: KanbanColumn): Long

    @Update
    suspend fun updateColumn(column: KanbanColumn)

    @Query("DELETE FROM kanban_columns WHERE id = :columnId")
    suspend fun deleteColumn(columnId: Long)

    @Query("SELECT * FROM kanban_notes ORDER BY position ASC, timestamp DESC")
    fun getAllNotes(): Flow<List<KanbanNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: KanbanNote): Long

    @Update
    suspend fun updateNote(note: KanbanNote)

    @Query("DELETE FROM kanban_notes WHERE id = :noteId")
    suspend fun deleteNote(noteId: Long)

    @Query("DELETE FROM kanban_notes WHERE columnId = :columnId")
    suspend fun deleteNotesByColumn(columnId: Long)
}
