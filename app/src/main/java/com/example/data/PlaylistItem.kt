package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlist_items")
data class PlaylistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val name: String,
    val path: String,
    val size: String,
    val type: String, // "AUDIO", "VIDEO", "IMAGE"
    val isLocal: Boolean = true
)
