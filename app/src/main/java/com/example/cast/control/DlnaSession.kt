package com.example.cast.control

import com.example.cast.discovery.CastDevice
import com.example.cast.media.CastMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DLNA/UPnP media control via AVTransport (+ RenderingControl for volume).
 * Talks SOAP over [LanHttpClient] — raw TCP, required because
 * `usesCleartextTraffic=false` blocks platform HTTP stacks on cleartext LAN.
 */
// MED-R10
class DlnaSession(override val device: CastDevice) : CastSession {

    /** Posts a SOAP action; returns the raw response (null on transport error/empty URL). */
    private suspend fun post(
        controlUrl: String, serviceType: String,
        action: String, args: Map<String, String>
    ): LanHttpClient.Response? {
        if (controlUrl.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            LanHttpClient.post(
                url = controlUrl,
                body = UpnpSoap.envelope(action, serviceType, mapOf("InstanceID" to "0") + args),
                contentType = "text/xml",
                extraHeaders = mapOf("SOAPACTION" to "\"$serviceType#$action\"")
            )
        }
    }

    /** Posts a SOAP action; true only on HTTP 200. */
    private suspend fun soap(
        controlUrl: String, serviceType: String,
        action: String, args: Map<String, String>
    ): Boolean = post(controlUrl, serviceType, action, args)?.code == 200

    private suspend fun avt(action: String, args: Map<String, String> = emptyMap()): Boolean =
        soap(device.avTransportControlUrl, UpnpSoap.AVT, action, args)

    override suspend fun load(media: CastMedia, url: String): Boolean {
        // Envelope args are emitted verbatim: CurrentURI must be escaped, and
        // CurrentURIMetaData is the whole DIDL-Lite document escaped as a string.
        val metadata = UpnpSoap.escape(
            UpnpSoap.didlLite(media.title, url, media.mimeType, media.kind, media.durationMs)
        )
        val ok = avt(
            "SetAVTransportURI",
            mapOf("CurrentURI" to UpnpSoap.escape(url), "CurrentURIMetaData" to metadata)
        )
        return ok && play()
    }

    override suspend fun play(): Boolean = avt("Play", mapOf("Speed" to "1"))

    override suspend fun pause(): Boolean = avt("Pause")

    override suspend fun stop(): Boolean = avt("Stop")

    override suspend fun seek(positionMs: Long): Boolean = avt(
        "Seek",
        mapOf("Unit" to "REL_TIME", "Target" to UpnpSoap.msToHms(positionMs))
    )

    override suspend fun setVolume(level: Int): Boolean = soap(
        device.renderingControlUrl, UpnpSoap.RCS, "SetVolume",
        mapOf("Channel" to "Master", "DesiredVolume" to level.coerceIn(0, 100).toString())
    )

    /** Reads `CurrentVolume` via RenderingControl GetVolume; null on any failure. */
    override suspend fun getVolume(): Int? {
        val resp = post(
            device.renderingControlUrl, UpnpSoap.RCS, "GetVolume",
            mapOf("Channel" to "Master")
        )
        if (resp == null || resp.code != 200) return null
        return UpnpSoap.parseVolume(resp.body)
    }

    /**
     * RelTime/TrackDuration come from `GetPositionInfo`; the transport state
     * lives in `GetTransportInfoResponse` (AVTransport:1 has no
     * CurrentTransportState in GetPositionInfo), so a second call is made —
     * if it fails, state falls back to "UNKNOWN".
     */
    override suspend fun positionInfo(): PositionInfo? {
        if (device.avTransportControlUrl.isEmpty()) return null
        val pos = post(device.avTransportControlUrl, UpnpSoap.AVT, "GetPositionInfo", emptyMap())
        if (pos == null || pos.code != 200) return null
        val info = UpnpSoap.parsePositionInfo(pos.body)
        val ti = post(device.avTransportControlUrl, UpnpSoap.AVT, "GetTransportInfo", emptyMap())
        val state = if (ti != null && ti.code == 200) {
            UpnpSoap.parsePositionInfo(ti.body).state
        } else {
            "UNKNOWN"
        }
        return info.copy(state = state)
    }

    /** Non-suspend no-op: the interface contract is that callers [stop] first. */
    override fun close() {}
}
