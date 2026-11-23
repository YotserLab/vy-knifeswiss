package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// MED-R15
@Entity(tableName = "media_playlists")
data class MediaPlaylist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
