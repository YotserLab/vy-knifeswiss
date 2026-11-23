package com.example.ui.screens.media
import com.example.ui.screens.files.FileItem
import com.example.ui.screens.files.FileType

import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import com.example.R
import com.example.Res

// ARQ-R12
object MediaStoreHelper {

    suspend fun queryImages(context: Context): List<FileItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<FileItem>()
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED
        )
        try {
            context.contentResolver.query(uri, projection, null, null, "${MediaStore.Images.Media.DATE_MODIFIED} DESC")?.use { cursor ->
                val idxName = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val idxPath = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val idxSize = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val idxDate = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(idxPath) ?: continue
                    val size = cursor.getLong(idxSize)
                    list.add(FileItem(
                        name = cursor.getString(idxName) ?: path.substringAfterLast("/"),
                        path = path,
                        size = formatSize(size),
                        type = FileType.IMAGE,
                        isLocal = true,
                        lastModified = cursor.getLong(idxDate) * 1000L,
                        rawSize = size
                    ))
                }
            }
        } catch (_: Exception) {}
        list
    }

    suspend fun queryAudio(context: Context): List<FileItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<FileItem>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED
        )
        try {
            context.contentResolver.query(uri, projection, null, null, "${MediaStore.Audio.Media.DATE_MODIFIED} DESC")?.use { cursor ->
                val idxName = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val idxPath = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val idxSize = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val idxDate = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(idxPath) ?: continue
                    val size = cursor.getLong(idxSize)
                    list.add(FileItem(
                        name = cursor.getString(idxName) ?: path.substringAfterLast("/"),
                        path = path,
                        size = formatSize(size),
                        type = FileType.AUDIO,
                        isLocal = true,
                        lastModified = cursor.getLong(idxDate) * 1000L,
                        rawSize = size
                    ))
                }
            }
        } catch (_: Exception) {}
        list
    }

    suspend fun queryVideos(context: Context): List<FileItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<FileItem>()
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED
        )
        try {
            context.contentResolver.query(uri, projection, null, null, "${MediaStore.Video.Media.DATE_MODIFIED} DESC")?.use { cursor ->
                val idxName = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val idxPath = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val idxSize = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val idxDate = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(idxPath) ?: continue
                    val size = cursor.getLong(idxSize)
                    list.add(FileItem(
                        name = cursor.getString(idxName) ?: path.substringAfterLast("/"),
                        path = path,
                        size = formatSize(size),
                        type = FileType.VIDEO,
                        isLocal = true,
                        lastModified = cursor.getLong(idxDate) * 1000L,
                        rawSize = size
                    ))
                }
            }
        } catch (_: Exception) {}
        list
    }

    suspend fun queryDocuments(context: Context): List<FileItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<FileItem>()
        val docExtensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "json", "md", "xml", "html")
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        val selection = MediaStore.Files.FileColumns.MEDIA_TYPE + " != " + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE +
                " AND " + MediaStore.Files.FileColumns.MEDIA_TYPE + " != " + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO +
                " AND " + MediaStore.Files.FileColumns.MEDIA_TYPE + " != " + MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO
        try {
            context.contentResolver.query(uri, projection, selection, null, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")?.use { cursor ->
                val idxName = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val idxPath = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val idxSize = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val idxDate = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(idxPath) ?: continue
                    val ext = path.substringAfterLast(".", "").lowercase(Locale.ROOT)
                    if (ext !in docExtensions) continue
                    val size = cursor.getLong(idxSize)
                    list.add(FileItem(
                        name = cursor.getString(idxName) ?: path.substringAfterLast("/"),
                        path = path,
                        size = formatSize(size),
                        type = FileType.DOCUMENT,
                        isLocal = true,
                        lastModified = cursor.getLong(idxDate) * 1000L,
                        rawSize = size
                    ))
                }
            }
        } catch (_: Exception) {}
        list
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> Res.get(R.string.n_1f_gb).format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> Res.get(R.string.n_1f_mb).format(bytes / 1_048_576.0)
        bytes >= 1024 -> Res.get(R.string.x_1024_kb_2, (bytes / 1024))
        else -> Res.get(R.string.x_b, bytes)
    }
}
