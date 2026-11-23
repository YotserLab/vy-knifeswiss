package com.example.plugin.core

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

object PluginIconResolver {
    fun resolve(name: String?): ImageVector {
        return when (name?.lowercase()?.trim()) {
            "wifi" -> Icons.Default.Wifi
            "search" -> Icons.Default.Search
            "network_check", "networkcheck", "ping" -> Icons.Default.NetworkCheck
            "device_hub", "devicehub", "subnet" -> Icons.Default.DeviceHub
            "speed", "speedtest" -> Icons.Default.Speed
            "info" -> Icons.Default.Info
            "chat" -> Icons.AutoMirrored.Filled.Chat
            "share", "mesh" -> Icons.Default.Share
            "screen_rotation", "level" -> Icons.Default.ScreenRotation
            "explore", "compass" -> Icons.Default.Explore
            "settings_applications", "qr" -> Icons.Default.SettingsApplications
            "memory", "cpu" -> Icons.Default.Memory
            "calculate", "calculator" -> Icons.Default.Calculate
            "directions_car", "obd" -> Icons.Default.DirectionsCar
            "lock", "vault" -> Icons.Default.Lock
            "phonelink_lock", "totp", "2fa" -> Icons.Default.PhonelinkLock
            "security", "shield" -> Icons.Default.Security
            "folder", "files" -> Icons.Default.Folder
            "developer_mode", "terminal" -> Icons.Default.DeveloperMode
            "history" -> Icons.Default.History
            "assignment", "notes", "kanban" -> Icons.AutoMirrored.Filled.Assignment
            "shopping_cart", "cart" -> Icons.Default.ShoppingCart
            "schedule", "alarm" -> Icons.Default.Schedule
            "backup", "cloud" -> Icons.Default.Backup
            "mic", "audio" -> Icons.Default.Mic
            "hearing", "transcribe" -> Icons.Default.Hearing
            "sms" -> Icons.Default.Sms
            "cleaning_services", "cleaner" -> Icons.Default.CleaningServices
            "sync" -> Icons.Default.Sync
            "videocam", "recorder" -> Icons.Default.Videocam
            "perm_media", "media" -> Icons.Default.PermMedia
            "launch" -> Icons.Default.Launch
            "brush", "whiteboard" -> Icons.Default.Brush
            "play_circle", "player" -> Icons.Default.PlayCircle
            "edit_note", "editor" -> Icons.Default.EditNote
            "code", "web" -> Icons.Default.Code
            "public", "globe" -> Icons.Default.Public
            "dns" -> Icons.Default.Dns
            "analytics" -> Icons.Default.Analytics
            else -> Icons.Default.Extension
        }
    }
}
