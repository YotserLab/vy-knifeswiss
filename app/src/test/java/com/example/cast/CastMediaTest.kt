package com.example.cast

import com.example.cast.media.CastMedia
import org.junit.Assert.assertEquals
import org.junit.Test

class CastMediaTest {

    @Test
    fun `mimeForPath maps common extensions case-insensitively`() {
        assertEquals("video/mp4", CastMedia.mimeForPath("/sdcard/Movies/clip.MP4"))
        assertEquals("video/x-matroska", CastMedia.mimeForPath("movie.mkv"))
        assertEquals("audio/mpeg", CastMedia.mimeForPath("song.mp3"))
        assertEquals("audio/flac", CastMedia.mimeForPath("song.flac"))
        assertEquals("image/jpeg", CastMedia.mimeForPath("pic.jpg"))
        assertEquals("image/png", CastMedia.mimeForPath("pic.png"))
        assertEquals("image/heic", CastMedia.mimeForPath("pic.heic"))
        assertEquals("image/heif", CastMedia.mimeForPath("pic.heif"))
        assertEquals("image/avif", CastMedia.mimeForPath("pic.avif"))
        assertEquals("image/svg+xml", CastMedia.mimeForPath("icon.svg"))
        assertEquals("video/mp2t", CastMedia.mimeForPath("stream.m2ts"))
        assertEquals("video/mp2t", CastMedia.mimeForPath("stream.mts"))
        assertEquals("application/x-mpegURL", CastMedia.mimeForPath("live.m3u8"))
        assertEquals("application/dash+xml", CastMedia.mimeForPath("live.mpd"))
    }

    @Test
    fun `mimeForPath falls back to octet-stream for unknown extension`() {
        assertEquals("application/octet-stream", CastMedia.mimeForPath("archive.xyz"))
        assertEquals("application/octet-stream", CastMedia.mimeForPath("noextension"))
    }

    @Test
    fun `kind is derived from mime type prefix`() {
        assertEquals(CastMedia.Kind.VIDEO, CastMedia.fromPath("a.mp4").kind)
        assertEquals(CastMedia.Kind.AUDIO, CastMedia.fromPath("a.mp3").kind)
        assertEquals(CastMedia.Kind.IMAGE, CastMedia.fromPath("a.png").kind)
        assertEquals(CastMedia.Kind.IMAGE, CastMedia.fromPath("a.xyz").kind)
    }

    @Test
    fun `kind classifies stream manifests as video`() {
        // HLS/DASH manifests carry application/* mime types but are streams.
        assertEquals(CastMedia.Kind.VIDEO, CastMedia.fromPath("live.m3u8").kind)
        assertEquals(CastMedia.Kind.VIDEO, CastMedia.fromPath("live.mpd").kind)
        assertEquals(
            CastMedia.Kind.VIDEO,
            CastMedia("/x", "x", "application/x-mpegURL").kind
        )
        // Other application/* payloads stay non-video.
        assertEquals(
            CastMedia.Kind.IMAGE,
            CastMedia("/x", "x", "application/octet-stream").kind
        )
    }

    @Test
    fun `fromPath derives title and mimeType`() {
        val media = CastMedia.fromPath("/sdcard/Movies/My Movie.mkv", durationMs = 1234L)
        assertEquals("My Movie.mkv", media.title)
        assertEquals("video/x-matroska", media.mimeType)
        assertEquals(1234L, media.durationMs)
    }
}
