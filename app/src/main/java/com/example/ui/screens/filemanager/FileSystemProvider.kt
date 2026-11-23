package com.example.ui.screens.filemanager

import com.example.ui.screens.files.FileItem
import com.example.ui.screens.files.FileType
import com.example.ui.screens.files.NetworkConnection
import com.example.ui.screens.files.RemoteFileClient
import com.example.ui.screens.files.RemoteResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import com.example.R
import com.example.Res

interface FileSystemProvider {
    val connectionName: String
    suspend fun listFiles(path: String): List<FileItem>
    suspend fun delete(file: FileItem): Boolean
}

class LocalFileSystemProvider : FileSystemProvider {
    override val connectionName = Res.get(R.string.meu_dispositivo_2)

    override suspend fun listFiles(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<FileItem>()
        val dir = File(path)
        val files = dir.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory) {
                    val count = file.listFiles()?.size ?: 0
                    list.add(FileItem(file.name, file.absolutePath, Res.get(R.string.x_itens_2, count), FileType.DIRECTORY, true, "", file.lastModified(), 0L))
                } else if (file.isFile) {
                    val ext = file.extension.lowercase(Locale.ROOT)
                    val sizeStr = Res.get(R.string.file_length_1024_kb, ((file.length() / 1024)))
                    val type = when (ext) {
                        "mp3", "wav", "m4a", "ogg" -> FileType.AUDIO
                        "mp4", "mkv", "avi", "3gp" -> FileType.VIDEO
                        "jpg", "jpeg", "png", "gif", "webp" -> FileType.IMAGE
                        "txt", "csv", "json", "pdf", "md" -> FileType.DOCUMENT
                        else -> FileType.GENERAL
                    }
                    list.add(FileItem(file.name, file.absolutePath, sizeStr, type, true, "", file.lastModified(), file.length()))
                }
            }
        }
        list.sortedWith(compareByDescending<FileItem> { it.type == FileType.DIRECTORY }.thenBy { it.name.lowercase() })
    }

    override suspend fun delete(file: FileItem): Boolean = withContext(Dispatchers.IO) {
        try {
            File(file.path).delete()
        } catch (e: Exception) {
            false
        }
    }
}

class RemoteFileSystemProvider(private val connection: NetworkConnection) : FileSystemProvider {
    override val connectionName = connection.name
    private val client = RemoteFileClient(connection)

    override suspend fun listFiles(path: String): List<FileItem> {
        val result = client.listDirectory(path)
        if (result is RemoteResult.Success) {
            return result.data
        } else {
            throw Exception((result as RemoteResult.Error).message)
        }
    }

    override suspend fun delete(file: FileItem): Boolean {
        // Remote deletion not yet implemented in base RemoteFileClient
        return false
    }
}
