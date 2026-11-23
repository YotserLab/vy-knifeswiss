package com.example.cast.media

import android.content.Context
import com.example.ui.screens.files.FileItem
import com.example.ui.screens.media.MediaStoreHelper

/**
 * Bridges [MediaStoreHelper] results into [CastMedia] so cast screens can
 * list castable content without knowing about the file-manager models.
 *
 * MediaStore queries here do not read duration, so returned items carry
 * `durationMs = 0` — cast sessions still work (TTL falls back to the
 * minimum and DIDL metadata omits the duration).
 */
class MediaLibraryQuery(private val context: Context) {

    suspend fun videos(): List<CastMedia> =
        MediaStoreHelper.queryVideos(context).map(::toCastMedia)

    suspend fun audios(): List<CastMedia> =
        MediaStoreHelper.queryAudio(context).map(::toCastMedia)

    suspend fun images(): List<CastMedia> =
        MediaStoreHelper.queryImages(context).map(::toCastMedia)

    private fun toCastMedia(item: FileItem) = CastMedia(
        path = item.path,
        title = item.name,
        mimeType = CastMedia.mimeForPath(item.path)
    )
}
