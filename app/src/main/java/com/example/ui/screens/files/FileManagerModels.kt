package com.example.ui.screens.files

enum class FileType {
    AUDIO, VIDEO, IMAGE, DOCUMENT, PDF, DIRECTORY, GENERAL
}

data class FileItem(
    val name: String,
    val path: String,
    val size: String,
    val type: FileType,
    val isLocal: Boolean = true,
    val contentString: String = "",
    val lastModified: Long = 0L,
    val rawSize: Long = 0L
)

fun getFileTypeFromName(name: String, isDir: Boolean): FileType {
    if (isDir) return FileType.DIRECTORY
    val ext = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
    return when (ext) {
        "mp3", "wav", "m4a", "ogg", "flac" -> FileType.AUDIO
        "mp4", "mkv", "avi", "3gp", "mov" -> FileType.VIDEO
        "jpg", "jpeg", "png", "gif", "webp" -> FileType.IMAGE
        "pdf" -> FileType.PDF
        "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "json", "xml", "epub" -> FileType.DOCUMENT
        else -> FileType.GENERAL
    }
}

data class NetworkConnection(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val protocol: String, // FTP, FTPS, SFTP, WebDAV, SMB
    val host: String,
    val port: Int = 21,
    val user: String = "",
    val password: String = "",
    val remotePath: String = "/",
    val isConnected: Boolean = false
)

fun NetworkConnection.toSaveString(): String =
    "$id;$name;$protocol;$host;$port;$user;$password;$remotePath;$isConnected"

fun String.toNetworkConnection(): NetworkConnection? {
    val parts = split(";")
    if (parts.size < 9) return null
    return NetworkConnection(
        id = parts[0].toLongOrNull() ?: System.currentTimeMillis(),
        name = parts[1],
        protocol = parts[2],
        host = parts[3],
        port = parts[4].toIntOrNull() ?: 21,
        user = parts[5],
        password = parts[6],
        remotePath = parts[7],
        isConnected = parts[8].toBoolean()
    )
}
