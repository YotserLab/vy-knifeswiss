package com.example.cast.media

import android.content.Context
import android.util.Log
import com.example.service.InternalHttpFileServer
import com.example.utils.net.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Serves local media files to cast renderers over the LAN by wrapping
 * [InternalHttpFileServer]. Cast devices fetch the file over plain HTTP —
 * the returned URL embeds a random token whose TTL is the media duration
 * plus 30 minutes, with a 4-hour floor. The session manager additionally
 * renews the token on every position-poll tick
 * ([InternalHttpFileServer.renew]), so even a stalled poll leaves a
 * generous expiry instead of killing long movies mid-playback.
 */
object CastMediaServer {
    private const val TAG = "CastServe"

    /** URL handed to the cast device plus the hosted-file token behind it. */
    data class ServedMedia(val url: String, val token: String)

    /**
     * Hosts [media] on the internal HTTP server and returns the URL the
     * cast device should fetch together with the serving token, or null
     * when the file is missing/unreadable or no usable LAN endpoint exists
     * (server down or only a loopback IP — a `127.0.0.1` URL would surface
     * later as a misleading device-side refusal). Runs on [Dispatchers.IO]:
     * socket setup, file checks and interface enumeration must not run on
     * the main thread.
     */
    suspend fun serve(context: Context, media: CastMedia): ServedMedia? =
        withContext(Dispatchers.IO) {
            InternalHttpFileServer.startServer(context)
            val file = File(media.path)
            if (!file.exists() || !file.canRead()) {
                Log.w(TAG, "file unavailable: ${media.path}")
                return@withContext null
            }
            val port = InternalHttpFileServer.port
            val ip = NetworkUtils.getWifiOrLocalIpSync()
            if (port <= 0 || ip.isBlank() || ip.startsWith("127.") || ip == "::1") {
                Log.w(TAG, "no usable endpoint: ip=$ip port=$port")
                return@withContext null
            }
            val ttl = maxOf(media.durationMs + 30 * 60_000L, 4 * 60 * 60_000L) // MED-R08
            val token = InternalHttpFileServer.hostFile(file, mimeType = media.mimeType, ttlMs = ttl)
            ServedMedia("http://$ip:$port/download/$token", token).also {
                Log.d(TAG, "serving ${file.name} (${media.mimeType}, ${file.length()}B) -> ${it.url}")
            }
        }
}
