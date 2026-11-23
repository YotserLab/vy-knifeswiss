package com.example.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object InternalHttpFileServer {
    private const val TAG = "CastHttp"
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    private data class HostedEntry(
        val file: File,
        val expiresAt: Long,
        val isTemp: Boolean = false,
        val mimeType: String? = null
    )

    // Mutated from every handler thread (cleanupExpired is called per request)
    // while cast renderers hold parallel Range connections — must be concurrent.
    private val hostedFiles = ConcurrentHashMap<String, HostedEntry>()
    private const val TOKEN_TTL_MS = 15 * 60 * 1000L // ARQ-R14 15 minutes

    // The socket binds 0.0.0.0, so any LAN client can connect: bound the
    // request head before parsing to avoid unbounded reads.
    private const val MAX_REQUEST_HEAD_BYTES = 16 * 1024 // ARQ-R13 16 KB total head
    private const val MAX_HEADER_LINES = 64 // ARQ-R13
    private val HEAD_TERMINATOR = intArrayOf('\r'.code, '\n'.code, '\r'.code, '\n'.code)
    private const val RESPONSE_431 =
        "HTTP/1.1 431 Request Header Fields Too Large\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"

    // Sane media-type (type/subtype token chars, no whitespace/control) — anything
    // else is dropped to the octet-stream fallback instead of reaching Content-Type.
    private val MEDIA_TYPE_PATTERN =
        Regex("^[a-zA-Z0-9][a-zA-Z0-9!#\$&^_.+-]*/[a-zA-Z0-9][a-zA-Z0-9!#\$&^_.+-]*$")

    var port: Int = 0
        private set

    // Serialized: concurrent callers could both see isRunning == false and
    // double-bind (the second socket would clobber port/serverSocket).
    @Synchronized
    fun startServer(context: Context) {
        if (isRunning && serverSocket != null && !serverSocket!!.isClosed) return
        try {
            val bindAddr = InetSocketAddress("0.0.0.0", 0)
            serverSocket = ServerSocket().apply {
                reuseAddress = true
                bind(bindAddr)
            }
            port = serverSocket!!.localPort
            isRunning = true
            Thread {
                try {
                    while (isRunning) {
                        val client = serverSocket?.accept() ?: break
                        handleClient(client)
                    }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) e.printStackTrace()
                }
            }.start()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) e.printStackTrace()
        }
    }

    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) e.printStackTrace()
        }
        serverSocket = null
        cleanupTempFiles()
    }

    fun hostFile(
        file: File,
        isTemp: Boolean = false,
        mimeType: String? = null,
        ttlMs: Long = TOKEN_TTL_MS
    ): String {
        cleanupExpired()
        val token = UUID.randomUUID().toString()
        // Guard Content-Type against header injection: keep only well-formed
        // media types, otherwise fall back to application/octet-stream.
        val safeMimeType = mimeType?.takeIf { MEDIA_TYPE_PATTERN.matches(it) }
        hostedFiles[token] = HostedEntry(file, System.currentTimeMillis() + ttlMs, isTemp, safeMimeType)
        return token
    }

    /**
     * Extends the lifetime of a hosted [token]: replaces its entry keeping
     * the same file/isTemp/mimeType with a fresh `expiresAt = now + ttlMs`.
     * Used by the cast poll loop so a served URL never expires mid-playback.
     * The atomic [ConcurrentHashMap.replace] loses to a concurrent
     * [cleanupExpired] removal instead of resurrecting a dead token.
     *
     * @return false when the token is unknown (never hosted or already
     * expired), so callers can detect a dead URL.
     */
    // EXT-R14: token TTL is renewable mid-session via renew() without changing the URL
    fun renew(token: String, ttlMs: Long = TOKEN_TTL_MS): Boolean {
        val entry = hostedFiles[token] ?: return false
        // An expired-but-uncleaned entry must not be resurrected — its temp
        // file may already be gone.
        if (entry.expiresAt <= System.currentTimeMillis()) return false
        val renewed = entry.copy(expiresAt = System.currentTimeMillis() + ttlMs)
        return hostedFiles.replace(token, entry, renewed)
    }

    fun hostUri(
        context: Context,
        uri: Uri,
        fileName: String,
        mimeType: String? = null,
        ttlMs: Long = TOKEN_TTL_MS
    ): String? {
        return try {
            startServer(context)
            val safeFileName = fileName.replace("/", "_").replace("\\", "_").ifBlank { "transfer_${System.currentTimeMillis()}" }
            val cacheDir = File(context.cacheDir, "http_transfer")
            cacheDir.mkdirs()
            val tempFile = File(cacheDir, "${UUID.randomUUID()}_$safeFileName")
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output, bufferSize = 32768)
                }
            } ?: return null

            hostFile(tempFile, isTemp = true, mimeType = mimeType, ttlMs = ttlMs)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) e.printStackTrace()
            null
        }
    }

    private fun cleanupExpired() {
        val now = System.currentTimeMillis()
        hostedFiles.entries.removeIf { entry ->
            if (entry.value.expiresAt < now) {
                if (entry.value.isTemp) {
                    try { entry.value.file.delete() } catch (_: Exception) {}
                }
                true
            } else {
                false
            }
        }
    }

    private fun cleanupTempFiles() {
        hostedFiles.values.forEach { entry ->
            if (entry.isTemp) {
                try { entry.file.delete() } catch (_: Exception) {}
            }
        }
        hostedFiles.clear()
    }

    /**
     * Parses a single HTTP `Range: bytes=...` header into a byte range within
     * [0, fileLen-1]. Returns null for malformed headers, multi-range specs,
     * empty files and unsatisfiable ranges (start >= fileLen).
     */
    internal fun parseRange(header: String, fileLen: Long): LongRange? {
        if (fileLen <= 0) return null
        val spec = header.trim()
        if (!spec.startsWith("bytes=", ignoreCase = true)) return null
        val rangePart = spec.substring(6).trim()
        if (rangePart.contains(',')) return null // multi-range unsupported
        val dash = rangePart.indexOf('-')
        if (dash < 0) return null
        val startStr = rangePart.substring(0, dash).trim()
        val endStr = rangePart.substring(dash + 1).trim()
        return if (startStr.isEmpty()) {
            // suffix range: last N bytes
            val suffixLen = endStr.toLongOrNull() ?: return null
            if (suffixLen <= 0) return null
            (fileLen - suffixLen).coerceAtLeast(0)..(fileLen - 1)
        } else {
            val start = startStr.toLongOrNull() ?: return null
            if (start < 0 || start >= fileLen) return null
            val end = if (endStr.isEmpty()) {
                fileLen - 1
            } else {
                (endStr.toLongOrNull() ?: return null).coerceAtMost(fileLen - 1)
            }
            if (end < start) return null
            start..end
        }
    }

    /**
     * Reads the HTTP request head (request line + headers) up to and including
     * the terminating CRLFCRLF, without ever buffering more than
     * [MAX_REQUEST_HEAD_BYTES].
     *
     * @return the raw head bytes when terminated properly; an empty array when
     * the peer sent nothing and closed; `null` when the head was unterminated
     * or exceeded the byte budget.
     */
    private fun readRequestHead(input: InputStream): ByteArray? {
        val buf = ByteArrayOutputStream(2048)
        val chunk = ByteArray(2048)
        var matchLen = 0
        while (buf.size() < MAX_REQUEST_HEAD_BYTES) {
            val n = input.read(chunk, 0, minOf(chunk.size, MAX_REQUEST_HEAD_BYTES - buf.size()))
            if (n <= 0) break
            for (i in 0 until n) {
                val b = chunk[i].toInt() and 0xFF
                matchLen = when {
                    b == HEAD_TERMINATOR[matchLen] -> matchLen + 1
                    b == HEAD_TERMINATOR[0] -> 1
                    else -> 0
                }
                if (matchLen == HEAD_TERMINATOR.size) {
                    buf.write(chunk, 0, i + 1)
                    return buf.toByteArray()
                }
            }
            buf.write(chunk, 0, n)
        }
        return if (buf.size() == 0) ByteArray(0) else null
    }

    private fun writeResponse(out: OutputStream, response: String) {
        out.write(response.toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }

    private fun handleClient(client: Socket) {
        Thread { serveClient(client) }.start()
    }

    private fun serveClient(client: Socket) {
        try {
            client.soTimeout = 30000
            val out = client.getOutputStream()
            val headBytes = readRequestHead(client.getInputStream())
            if (headBytes == null) {
                // Unterminated or oversized request head — reject instead of
                // trusting anything after the byte budget.
                writeResponse(out, RESPONSE_431)
                return
            }
            if (headBytes.isEmpty()) return // peer went away silently

            val lines = String(headBytes, Charsets.ISO_8859_1).split("\r\n")
            val requestLine = lines.firstOrNull()
            val requestHeaders = mutableMapOf<String, String>()
            var headerLines = 0
            var headersExceeded = false
            for (i in 1 until lines.size) {
                val headerLine = lines[i]
                if (headerLine.isEmpty()) break
                if (++headerLines > MAX_HEADER_LINES) {
                    headersExceeded = true
                    break
                }
                val idx = headerLine.indexOf(':')
                if (idx > 0) {
                    requestHeaders[headerLine.substring(0, idx).trim().lowercase()] =
                        headerLine.substring(idx + 1).trim()
                }
            }
            if (headersExceeded) {
                writeResponse(out, RESPONSE_431)
                return
            }

            Log.d(TAG, "request from ${client.inetAddress.hostAddress}: $requestLine")
            if (requestLine != null && (requestLine.startsWith("GET") || requestLine.startsWith("HEAD"))) {
                val parts = requestLine.split(" ")
                val headOnly = parts.firstOrNull()?.equals("HEAD", ignoreCase = true) == true
                if (parts.size >= 2) {
                    val path = parts[1]
                    val token = path.substringBefore("?").removePrefix("/download/")

                    cleanupExpired()
                    val entry = hostedFiles[token]
                    val file = entry?.file

                    if (file != null && file.exists() && file.canRead()) {
                        val fileLength = file.length()
                        val mimeType = entry.mimeType ?: "application/octet-stream"
                        val rangeHeader = requestHeaders["range"]
                        val range = rangeHeader?.let { parseRange(it, fileLength) }

                        if (rangeHeader != null && range == null) {
                            val notSatisfiable = "HTTP/1.1 416 Range Not Satisfiable\r\n" +
                                    "Content-Range: bytes */$fileLength\r\n" +
                                    "Content-Length: 0\r\n" +
                                    "Accept-Ranges: bytes\r\n" +
                                    "Access-Control-Allow-Origin: *\r\n" +
                                    "Connection: close\r\n\r\n"
                            out.write(notSatisfiable.toByteArray(Charsets.ISO_8859_1))
                            out.flush()
                        } else if (range != null) {
                            val start = range.first
                            val end = range.last
                            val rangeLength = end - start + 1
                            val headers = "HTTP/1.1 206 Partial Content\r\n" +
                                    "Content-Type: $mimeType\r\n" +
                                    "Content-Range: bytes $start-$end/$fileLength\r\n" +
                                    "Content-Length: $rangeLength\r\n" +
                                    "Accept-Ranges: bytes\r\n" +
                                    "Access-Control-Allow-Origin: *\r\n" +
                                    "Connection: close\r\n\r\n"
                            out.write(headers.toByteArray(Charsets.ISO_8859_1))

                            if (!headOnly) {
                                RandomAccessFile(file, "r").use { raf ->
                                    raf.seek(start)
                                    val buffer = ByteArray(32768)
                                    var remaining = rangeLength
                                    while (remaining > 0) {
                                        val n = raf.read(
                                            buffer, 0,
                                            minOf(buffer.size.toLong(), remaining).toInt()
                                        )
                                        if (n < 0) break
                                        out.write(buffer, 0, n)
                                        remaining -= n
                                    }
                                    out.flush()
                                }
                            } else {
                                out.flush()
                            }
                        } else {
                            val headers = "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: $mimeType\r\n" +
                                    "Content-Length: $fileLength\r\n" + // CHAT-R09
                                    "Accept-Ranges: bytes\r\n" +
                                    "Access-Control-Allow-Origin: *\r\n" +
                                    "Connection: close\r\n\r\n"
                            out.write(headers.toByteArray(Charsets.ISO_8859_1))

                            if (!headOnly) {
                                file.inputStream().use { fileStream ->
                                    val buffer = ByteArray(32768)
                                    var bytesRead: Int
                                    while (fileStream.read(buffer).also { bytesRead = it } != -1) {
                                        out.write(buffer, 0, bytesRead)
                                    }
                                    out.flush()
                                }
                            } else {
                                out.flush()
                            }
                        }
                    } else {
                        Log.d(TAG, "404 for $requestLine")
                        val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
                        out.write(notFound.toByteArray(Charsets.ISO_8859_1))
                        out.flush()
                    }
                }
            } else if (requestLine != null) {
                // HEAD probing is answered above; anything else (OPTIONS,
                // POST, …) gets an explicit 405 instead of a silent close —
                // receivers treat a dead socket as a hard fetch failure.
                writeResponse(
                    out,
                    "HTTP/1.1 405 Method Not Allowed\r\nContent-Length: 0\r\n" +
                        "Allow: GET, HEAD\r\nAccess-Control-Allow-Origin: *\r\n" +
                        "Connection: close\r\n\r\n"
                )
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) e.printStackTrace()
            try {
                val err = "HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                client.getOutputStream().write(err.toByteArray(Charsets.ISO_8859_1))
                client.getOutputStream().flush()
            } catch (_: Exception) {}
        } finally {
            try {
                client.close()
            } catch (_: Exception) {}
        }
    }
}
