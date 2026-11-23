package com.example.cast.control

import com.example.cast.discovery.CastDevice
import com.example.cast.media.CastMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * Builds Roku ECP (External Control Protocol) request paths. The Roku media
 * player app (id 15985) plays arbitrary URLs passed as launch parameters:
 * `t` = media type (v=video, a=audio, p=photo), `u` = media URL.
 */
object RokuEcp {
    fun launchPath(kind: CastMedia.Kind, mediaUrl: String, title: String): String {
        val t = when (kind) {
            CastMedia.Kind.VIDEO -> "v"
            CastMedia.Kind.AUDIO -> "a"
            CastMedia.Kind.IMAGE -> "p"
        }
        return "/launch/15985?t=$t" +
            "&u=${URLEncoder.encode(mediaUrl, "UTF-8")}" +
            "&videoName=${URLEncoder.encode(title, "UTF-8")}"
    }
}

/**
 * Cast session for Roku devices over ECP (port 8060). ECP only offers
 * app-launch and remote-keypress endpoints: no seeking, no volume, no
 * position feedback — [supportsSeek] and [supportsVolume] are false.
 */
class RokuSession(override val device: CastDevice) : CastSession {
    override val supportsSeek = false // MED-R11
    override val supportsVolume = false // MED-R11
    override val supportsPositionInfo = false

    /** Posts an ECP path; Roku answers 200/202/204 on success. */
    private suspend fun ecp(path: String): Boolean {
        val port = device.port.takeIf { it > 0 } ?: 8060
        val code = withContext(Dispatchers.IO) {
            LanHttpClient.post("http://${device.ip}:$port$path", "", "text/plain")
        }?.code
        return code == 200 || code == 202 || code == 204
    }

    override suspend fun load(media: CastMedia, url: String): Boolean =
        ecp(RokuEcp.launchPath(media.kind, url, media.title))

    /** Play is a toggle on Roku — used for both play and pause. */
    override suspend fun play(): Boolean = ecp("/keypress/Play")

    override suspend fun pause(): Boolean = ecp("/keypress/Play")

    override suspend fun seek(positionMs: Long): Boolean = false

    /** Back exits playback on the Roku media player. */
    override suspend fun stop(): Boolean = ecp("/keypress/Back")

    override fun close() {}
}
