package com.example.cast.control

import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.charset.Charset

/**
 * Minimal HTTP/1.1 client over raw TCP sockets.
 *
 * All LAN (DLNA/SSDP/Roku/DIAL) HTTP traffic must go through raw sockets —
 * never HttpURLConnection/OkHttp — because the app sets
 * `usesCleartextTraffic=false`, which blocks plain-HTTP requests done by the
 * platform HTTP stacks on Android 9+.
 */
object LanHttpClient {
    data class Response(val code: Int, val headers: Map<String, String>, val body: String)

    fun get(url: String, timeoutMs: Int = 5000): Response? =
        request("GET", url, null, "text/plain", emptyMap(), timeoutMs)

    fun post(url: String, body: String, contentType: String,
             extraHeaders: Map<String, String> = emptyMap(), timeoutMs: Int = 8000): Response? =
        request("POST", url, body, contentType, extraHeaders, timeoutMs)

    private fun request(method: String, url: String, body: String?, contentType: String,
                        extraHeaders: Map<String, String>, timeoutMs: Int): Response? {
        return try {
            val uri = URI(url)
            val host = uri.host ?: return null
            val port = if (uri.port > 0) uri.port else 80
            val path = (uri.rawPath ?: "/") + (uri.rawQuery?.let { "?$it" } ?: "")
            extraHeaders.forEach { (k, v) ->
                require('\r' !in k && '\n' !in k && '\r' !in v && '\n' !in v) {
                    "CR/LF not allowed in header: $k"
                }
            }
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                socket.soTimeout = timeoutMs
                val out: OutputStream = socket.getOutputStream()
                val sb = StringBuilder("$method $path HTTP/1.1\r\nHost: $host:$port\r\nConnection: close\r\n")
                if (body != null) {
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    sb.append("Content-Type: $contentType; charset=\"utf-8\"\r\nContent-Length: ${bytes.size}\r\n")
                }
                extraHeaders.forEach { (k, v) -> sb.append("$k: $v\r\n") }
                sb.append("\r\n")
                out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
                if (body != null) out.write(body.toByteArray(Charsets.UTF_8))
                out.flush()

                readResponse(socket.getInputStream())
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (BuildConfig.DEBUG) e.printStackTrace()
            null
        }
    }

    /**
     * Reads an HTTP response at the byte level so the body can be decoded with
     * the correct charset (UTF-8 by default, or the `charset=` parameter of
     * Content-Type). Reads exactly Content-Length bytes when present; otherwise
     * falls back to read-until-close (we always send `Connection: close`).
     */
    private fun readResponse(input: InputStream): Response? {
        val buf = ByteArrayOutputStream()
        val tmp = ByteArray(8192)
        var headEnd = -1
        var sepLen = 0
        var code = -1
        var headers: Map<String, String> = emptyMap()
        var contentLength = -1

        while (true) {
            val n = try {
                input.read(tmp)
            } catch (_: SocketTimeoutException) {
                break // keep whatever was received so far
            }
            if (n < 0) break
            buf.write(tmp, 0, n)

            if (headEnd < 0) {
                val bytes = buf.toByteArray()
                val (idx, len) = findHeaderEnd(bytes)
                if (idx >= 0) {
                    headEnd = idx
                    sepLen = len
                    val head = String(bytes, 0, idx, Charsets.ISO_8859_1)
                    val parsed = parseHead(head) ?: return null
                    code = parsed.first
                    headers = parsed.second
                    contentLength = headers["content-length"]?.toIntOrNull()?.coerceAtLeast(0) ?: -1
                }
            }
            if (headEnd >= 0 && contentLength >= 0 &&
                buf.size() - headEnd - sepLen >= contentLength) {
                break
            }
        }

        if (headEnd < 0) return null
        val bytes = buf.toByteArray()
        val bodyStart = headEnd + sepLen
        val chunked = headers["transfer-encoding"]
            ?.contains("chunked", ignoreCase = true) == true
        val bodyBytes = if (contentLength >= 0) {
            bytes.copyOfRange(bodyStart, minOf(bodyStart + contentLength, bytes.size))
        } else if (chunked) {
            // No usable Content-Length: the body is chunk-framed — de-frame
            // it before charset decoding (desc.xml and SOAP replies from
            // some renderers arrive chunked).
            decodeChunked(bytes.copyOfRange(bodyStart, bytes.size))
        } else {
            bytes.copyOfRange(bodyStart, bytes.size)
        }
        val body = String(bodyBytes, charsetFor(headers["content-type"]))
        return Response(code, headers, body)
    }

    /**
     * De-frames a `Transfer-Encoding: chunked` body: parses
     * `<hexlen>[;ext]\r\n<data>\r\n` blocks until the `0`-length chunk;
     * trailer headers (if any) are dropped. Lenient on truncated tails —
     * whatever arrived complete is returned instead of failing outright.
     */
    internal fun decodeChunked(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var pos = 0
        while (true) {
            var lineEnd = -1
            var i = pos
            while (i + 1 < bytes.size) {
                if (bytes[i] == '\r'.code.toByte() && bytes[i + 1] == '\n'.code.toByte()) {
                    lineEnd = i
                    break
                }
                i++
            }
            if (lineEnd < 0) break
            val sizeField = String(bytes, pos, lineEnd - pos, Charsets.ISO_8859_1)
                .substringBefore(';').trim()
            val len = sizeField.toIntOrNull(16) ?: break
            pos = lineEnd + 2
            if (len == 0) break // last-chunk — trailer (if present) is ignored
            if (pos + len > bytes.size) {
                out.write(bytes, pos, bytes.size - pos) // truncated tail: keep it
                break
            }
            out.write(bytes, pos, len)
            pos += len
            if (pos + 1 < bytes.size &&
                bytes[pos] == '\r'.code.toByte() && bytes[pos + 1] == '\n'.code.toByte()
            ) {
                pos += 2
            }
        }
        return out.toByteArray()
    }

    /** Returns the index of the header/body separator and its length (4 for CRLF, 2 for LF-only). */
    private fun findHeaderEnd(b: ByteArray): Pair<Int, Int> {
        var i = 0
        while (i + 3 < b.size) {
            if (b[i] == '\r'.code.toByte() && b[i + 1] == '\n'.code.toByte() &&
                b[i + 2] == '\r'.code.toByte() && b[i + 3] == '\n'.code.toByte()) {
                return i to 4
            }
            i++
        }
        i = 0
        while (i + 1 < b.size) {
            if (b[i] == '\n'.code.toByte() && b[i + 1] == '\n'.code.toByte()) return i to 2
            i++
        }
        return -1 to 0
    }

    private fun parseHead(head: String): Pair<Int, Map<String, String>>? {
        val lines = head.split(Regex("\r?\n"))
        val code = lines.firstOrNull()?.trim()
            ?.split(Regex("\\s+"))?.getOrNull(1)?.toIntOrNull() ?: return null
        val headers = lines.drop(1).mapNotNull {
            val i = it.indexOf(':'); if (i < 0) null else it.substring(0, i).trim().lowercase() to it.substring(i + 1).trim()
        }.toMap()
        return code to headers
    }

    private fun charsetFor(contentType: String?): Charset {
        val name = contentType?.let {
            Regex("(?i)charset=\\s*\"?([^;\"\\s]+)").find(it)?.groupValues?.get(1)
        }
        return name?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
    }
}
