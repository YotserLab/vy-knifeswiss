package com.example.cast.control

import com.example.cast.media.CastMedia

/** Transport position/duration/state extracted from a UPnP response body. */
data class PositionInfo(val relTimeMs: Long, val durationMs: Long, val state: String)

/**
 * Pure builders/parsers for the UPnP SOAP control layer (AVTransport and
 * RenderingControl). No I/O here — sessions send the strings through
 * [LanHttpClient] and feed response bodies back into the parse functions.
 */
object UpnpSoap {
    const val AVT = "urn:schemas-upnp-org:service:AVTransport:1"
    const val RCS = "urn:schemas-upnp-org:service:RenderingControl:1"

    /**
     * Builds a SOAP envelope for [action] on [serviceType].
     *
     * IMPORTANT: argument VALUES are emitted verbatim — they are NOT
     * XML-escaped here. Callers must pass pre-escaped values where needed
     * (e.g. [escape] for URLs, or the already-escaped DIDL-Lite string for
     * CurrentURIMetaData). This keeps simple args like "0"/"Master" clean
     * while allowing nested XML payloads to be escaped exactly once.
     */
    fun envelope(action: String, serviceType: String, args: Map<String, String>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        sb.append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"")
        sb.append(" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">")
        sb.append("<s:Body><u:").append(action)
        sb.append(" xmlns:u=\"").append(serviceType).append("\">")
        args.forEach { (k, v) -> sb.append('<').append(k).append('>').append(v).append("</").append(k).append('>') }
        sb.append("</u:").append(action).append("></s:Body></s:Envelope>")
        return sb.toString()
    }

    /**
     * Builds a DIDL-Lite metadata document for a single media item.
     * [title] and [url] are XML-escaped inside the document. The `duration`
     * attribute (H:MM:SS) is emitted only when [durationMs] > 0.
     */
    fun didlLite(title: String, url: String, mime: String, kind: CastMedia.Kind, durationMs: Long): String {
        val upnpClass = when (kind) {
            CastMedia.Kind.VIDEO -> "object.item.videoItem"
            CastMedia.Kind.AUDIO -> "object.item.audioItem.musicTrack"
            CastMedia.Kind.IMAGE -> "object.item.imageItem.photo"
        }
        val sb = StringBuilder()
        sb.append("<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\"")
        sb.append(" xmlns:dc=\"http://purl.org/dc/elements/1.1/\"")
        sb.append(" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">")
        sb.append("<item id=\"0\" parentID=\"-1\" restricted=\"1\">")
        sb.append("<dc:title>").append(escape(title)).append("</dc:title>")
        sb.append("<upnp:class>").append(upnpClass).append("</upnp:class>")
        sb.append("<res protocolInfo=\"http-get:*:").append(escape(mime)).append(":*\"")
        if (durationMs > 0) sb.append(" duration=\"").append(msToHms(durationMs)).append("\"")
        sb.append('>').append(escape(url)).append("</res>")
        sb.append("</item></DIDL-Lite>")
        return sb.toString()
    }

    /** XML-escapes &, <, >, " and ' — '&' first so entities are not double-escaped. */
    fun escape(s: String): String = buildString(s.length + 16) {
        for (c in s) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }

    /**
     * Parses "H:MM:SS" or "MM:SS" to milliseconds. Tolerates fractional
     * seconds ("0:00:05.500"). Returns 0 for empty/malformed input (e.g. the
     * common "NOT_IMPLEMENTED" sentinel).
     */
    fun parseHmsToMs(hms: String): Long {
        val parts = hms.trim().split(":")
        if (parts.size !in 2..3) return 0L
        val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return 0L else 0L
        val minutes = (if (parts.size == 3) parts[1] else parts[0]).toLongOrNull() ?: return 0L
        val secStr = if (parts.size == 3) parts[2] else parts[1]
        val seconds = secStr.substringBefore('.').toLongOrNull() ?: return 0L
        val frac = secStr.substringAfter('.', "")
        val fracMs = if (frac.isEmpty()) 0L
        else frac.take(3).padEnd(3, '0').toLongOrNull() ?: 0L
        if (hours < 0 || minutes < 0 || seconds < 0) return 0L
        return hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + fracMs
    }

    /** Formats milliseconds as "H:MM:SS" (inverse of [parseHmsToMs], fractional ms truncated). */
    fun msToHms(ms: Long): String {
        val total = ms.coerceAtLeast(0) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    }

    /**
     * Extracts `<RelTime>`, `<TrackDuration>` and `<CurrentTransportState>`
     * from a SOAP response body. Missing/unparseable fields default to
     * 0 ms / "UNKNOWN".
     */
    fun parsePositionInfo(body: String): PositionInfo = PositionInfo(
        relTimeMs = tagValue(body, "RelTime")?.let { parseHmsToMs(it) } ?: 0L,
        durationMs = tagValue(body, "TrackDuration")?.let { parseHmsToMs(it) } ?: 0L,
        state = tagValue(body, "CurrentTransportState") ?: "UNKNOWN"
    )

    /** Extracts `<CurrentVolume>` as Int, or null when absent/non-numeric. */
    fun parseVolume(body: String): Int? = tagValue(body, "CurrentVolume")?.toIntOrNull()

    /**
     * Substring-based tag text extractor — tolerant of namespace prefixes
     * (`<u:RelTime>`) and attributes (`<TrackDuration foo="1">`), unlike a
     * strict XML parser which would choke on the ad-hoc bodies some DLNA
     * renderers emit. Returns the raw inner text of the first matching tag.
     */
    private fun tagValue(body: String, tag: String): String? {
        var i = 0
        while (i < body.length) {
            val lt = body.indexOf('<', i)
            if (lt < 0 || lt + 1 >= body.length) return null
            val next = body[lt + 1]
            if (next == '/' || next == '!' || next == '?') { i = lt + 1; continue }
            val gt = body.indexOf('>', lt)
            if (gt < 0) return null
            // Tag name = chars after '<' up to the first whitespace, '/' or '>';
            // a colon inside it is a namespace prefix, colons later are attrs.
            var nameEnd = lt + 1
            while (nameEnd < gt && !body[nameEnd].isWhitespace() && body[nameEnd] != '/') nameEnd++
            val name = body.substring(lt + 1, nameEnd)
            if (name.substringAfterLast(':') == tag) {
                if (body[gt - 1] == '/') return null // self-closing tag: no inner text
                val close = body.indexOf("</", gt + 1)
                if (close < 0) return null
                return body.substring(gt + 1, close).trim()
            }
            i = gt + 1
        }
        return null
    }
}
