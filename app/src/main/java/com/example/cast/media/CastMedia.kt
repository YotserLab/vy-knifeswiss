package com.example.cast.media

data class CastMedia(
    val path: String,
    val title: String,
    val mimeType: String,
    val durationMs: Long = 0
) {
    enum class Kind { VIDEO, AUDIO, IMAGE }
    val kind: Kind
        get() = when {
            mimeType.startsWith("video/") -> Kind.VIDEO
            mimeType.startsWith("audio/") -> Kind.AUDIO
            // HLS/DASH manifests are video streams despite the
            // application/* mime type.
            mimeType.startsWith("application/") &&
                mimeType.lowercase() in STREAM_MANIFEST_MIMES -> Kind.VIDEO
            else -> Kind.IMAGE
        }

    companion object {
        private val STREAM_MANIFEST_MIMES = setOf(
            "application/x-mpegurl", "application/dash+xml"
        )

        private val MIME = mapOf(
            "mp4" to "video/mp4", "m4v" to "video/mp4", "mkv" to "video/x-matroska",
            "webm" to "video/webm", "avi" to "video/x-msvideo", "mov" to "video/quicktime",
            "3gp" to "video/3gpp", "ts" to "video/mp2t", "mpeg" to "video/mpeg",
            "mpg" to "video/mpeg", "wmv" to "video/x-ms-wmv", "flv" to "video/x-flv",
            "m2ts" to "video/mp2t", "mts" to "video/mp2t",
            "m3u8" to "application/x-mpegURL", "mpd" to "application/dash+xml",
            "mp3" to "audio/mpeg", "aac" to "audio/aac", "m4a" to "audio/mp4",
            "ogg" to "audio/ogg", "opus" to "audio/ogg", "flac" to "audio/flac",
            "wav" to "audio/wav", "wma" to "audio/x-ms-wma",
            "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png",
            "gif" to "image/gif", "bmp" to "image/bmp", "webp" to "image/webp",
            "heic" to "image/heic", "heif" to "image/heif", "avif" to "image/avif",
            "svg" to "image/svg+xml"
        )

        fun mimeForPath(path: String): String =
            MIME[path.substringAfterLast('.', "").lowercase()] ?: "application/octet-stream"

        fun fromPath(path: String, durationMs: Long = 0) =
            CastMedia(path, path.substringAfterLast('/'), mimeForPath(path), durationMs)
    }
}
