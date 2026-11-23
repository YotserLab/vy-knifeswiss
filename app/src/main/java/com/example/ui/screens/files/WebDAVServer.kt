package com.example.ui.screens.files

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import com.example.utils.net.NetworkUtils
import java.text.SimpleDateFormat
import java.security.SecureRandom
import java.util.Base64
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.example.R
import com.example.Res

class WebDAVServer(private val scope: CoroutineScope, private val baseDir: File) {

    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null
    var isRunning: Boolean = false
        private set
    val log: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private var sessionPassword: String = ""

    private fun generateSessionPassword(): String {
        val bytes = ByteArray(12)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun start(port: Int = 8080) { // ARQ-R04
        if (isRunning) return
        isRunning = true
        sessionPassword = generateSessionPassword()
        log.clear()
        log.add(Res.get(R.string.iniciando_webdav_na_porta_x, port))
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket().apply { bind(InetSocketAddress(NetworkUtils.getWifiOrLocalIpSync(), port)) }
                val ip = getLocalIp()
                log.add(Res.get(R.string.webdav_ativo_http_x_x, ip, port))
                log.add(Res.get(R.string.user_android_senha_x, sessionPassword))
                log.add(Res.get(R.string.conecte_via_gerenciador_de_arquivos_usan))
                log.add(Res.get(R.string.url_http_x_x, ip, port))
                while (isActive && isRunning) {
                    val client = serverSocket?.accept() ?: break
                    log.add(Res.get(R.string.conexao_de_x, (client.inetAddress.hostAddress)))
                    launch(Dispatchers.IO) { handleClient(client) }
                }
            } catch (e: Exception) {
                if (isRunning) log.add(Res.get(R.string.erro_webdav_x, (e.message)))
                isRunning = false
            }
        }
    }

    fun stop() {
        isRunning = false
        serverJob?.cancel()
        try { serverSocket?.close() } catch (_: Exception) {}
        log.add(Res.get(R.string.webdav_parado))
    }

    fun getIp(): String = getLocalIp()

    private fun isAuthorized(authHeader: String?): Boolean {
        if (authHeader.isNullOrEmpty() || !authHeader.startsWith("Basic ", ignoreCase = true)) return false
        val encoded = authHeader.substring(6).trim()
        val decoded = try {
            String(Base64.getDecoder().decode(encoded), Charsets.UTF_8) // ARQ-R05
        } catch (_: Exception) { return false }
        val parts = decoded.split(":", limit = 2)
        if (parts.size != 2) return false
        return parts[0] == "android" && parts[1] == sessionPassword
    }

    private fun isWithinBaseDir(file: File): Boolean {
        return try {
            val canonicalBase = baseDir.canonicalFile
            val canonicalTarget = file.canonicalFile
            var parent: File? = canonicalTarget
            while (parent != null) {
                if (parent == canonicalBase) return true
                parent = parent.parentFile
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun getLocalIp(): String {
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces()
            while (ifaces.hasMoreElements()) {
                val iface = ifaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress)
                        return addr.hostAddress ?: "127.0.0.1"
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 30000
            val reader = BufferedReader(InputStreamReader(socket.inputStream))
            val out = socket.outputStream

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.trim().split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val rawPath = parts[1]

            val headers = mutableMapOf<String, String>()
            var line = reader.readLine()
            while (!line.isNullOrEmpty()) {
                val idx = line.indexOf(':')
                if (idx > 0) {
                    headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                }
                line = reader.readLine()
            }

            if (!isAuthorized(headers["authorization"])) {
                sendResponse(out, 401, "Unauthorized", mapOf( // ARQ-R04
                    "WWW-Authenticate" to "Basic realm=\"VYKnifeSwiss\"",
                    "Content-Length" to "0"
                ), null)
                return
            }

            val decodedPath = java.net.URLDecoder.decode(rawPath, "UTF-8").trimEnd('/')
            val targetFile = if (decodedPath.isEmpty() || decodedPath == "/") baseDir
                            else File(baseDir, decodedPath.removePrefix("/"))

            log.add("$method $rawPath")

            if (!isWithinBaseDir(targetFile)) {
                sendError(out, 403, "Forbidden")
                return
            }

            when (method) {
                "OPTIONS" -> {
                    sendResponse(out, 200, "OK", mapOf(
                        "Allow" to "OPTIONS, GET, HEAD, PUT, DELETE, MKCOL, PROPFIND, MOVE, COPY",
                        "DAV" to "1",
                        "Content-Length" to "0"
                    ), null)
                }
                "PROPFIND" -> {
                    val depth = headers["depth"] ?: "0"
                    val xml = buildPropfindXml(targetFile, decodedPath, depth)
                    sendResponse(out, 207, "Multi-Status", mapOf(
                        "Content-Type" to "application/xml; charset=utf-8",
                        "Content-Length" to xml.toByteArray().size.toString()
                    ), xml.toByteArray())
                }
                "GET", "HEAD" -> {
                    if (!targetFile.exists()) {
                        sendError(out, 404, Res.get(R.string.not_found))
                    } else if (targetFile.isDirectory) {
                        val html = buildDirectoryListing(targetFile, decodedPath)
                        sendResponse(out, 200, "OK", mapOf(
                            "Content-Type" to "text/html; charset=utf-8",
                            "Content-Length" to html.toByteArray().size.toString()
                        ), if (method == "GET") html.toByteArray() else null)
                    } else {
                        val mime = getMimeType(targetFile.name)
                        if (method == "GET") {
                            sendResponseWithFile(out, 200, "OK", targetFile, mime)
                        } else {
                            sendResponse(out, 200, "OK", mapOf(
                                "Content-Type" to mime,
                                "Content-Length" to targetFile.length().toString()
                            ), null)
                        }
                    }
                }
                "PUT" -> {
                    val contentLength = headers["content-length"]?.toLongOrNull() ?: -1L
                    targetFile.parentFile?.mkdirs()
                    val buf = ByteArray(8192)
                    val rawInputStream = socket.inputStream
                    targetFile.outputStream().use { fos ->
                        if (contentLength >= 0) {
                            var remaining = contentLength
                            while (remaining > 0) {
                                val toRead = minOf(buf.size.toLong(), remaining).toInt()
                                val read = rawInputStream.read(buf, 0, toRead)
                                if (read < 0) break
                                fos.write(buf, 0, read)
                                remaining -= read
                            }
                        } else {
                            var read: Int
                            while (rawInputStream.read(buf).also { read = it } != -1) {
                                fos.write(buf, 0, read)
                            }
                        }
                    }
                    sendResponse(out, 201, "Created", mapOf("Content-Length" to "0"), null)
                    log.add(Res.get(R.string.uploaded_x, (targetFile.name)))
                }
                "DELETE" -> {
                    if (!targetFile.exists()) {
                        sendError(out, 404, Res.get(R.string.not_found))
                    } else {
                        val ok = if (targetFile.isDirectory) targetFile.deleteRecursively() else targetFile.delete()
                        if (ok) sendResponse(out, 204, "No Content", mapOf("Content-Length" to "0"), null)
                        else sendError(out, 403, "Forbidden")
                    }
                }
                "MKCOL" -> {
                    if (targetFile.exists()) {
                        sendError(out, 405, "Method Not Allowed")
                    } else {
                        if (targetFile.mkdirs()) sendResponse(out, 201, "Created", mapOf("Content-Length" to "0"), null)
                        else sendError(out, 403, "Forbidden")
                    }
                }
                "MOVE" -> {
                    val dest = headers["destination"] ?: ""
                    val destPath = java.net.URLDecoder.decode(
                        dest.substringAfter("http://").substringAfter("/").let { "/$it" }, "UTF-8"
                    )
                    val destFile = File(baseDir, destPath.removePrefix("/"))
                    if (!isWithinBaseDir(destFile)) {
                        sendError(out, 403, "Forbidden")
                    } else {
                        destFile.parentFile?.mkdirs()
                        if (targetFile.renameTo(destFile)) {
                            sendResponse(out, 201, "Created", mapOf("Content-Length" to "0"), null)
                        } else {
                            sendError(out, 403, "Forbidden")
                        }
                    }
                }
                else -> sendError(out, 501, "Not Implemented")
            }
        } catch (e: Exception) {
            log.add(Res.get(R.string.erro_x_4, (e.message)))
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private val httpDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT")
    }
    private val davDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun buildPropfindXml(file: File, path: String, depth: String): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<D:multistatus xmlns:D=\"DAV:\">\n")
        appendPropEntry(sb, file, path)
        if (depth != "0" && file.isDirectory) {
            file.listFiles()?.forEach { child ->
                val childPath = if (path.endsWith("/")) "$path${child.name}" else "$path/${child.name}"
                appendPropEntry(sb, child, childPath)
            }
        }
        sb.append("</D:multistatus>")
        return sb.toString()
    }

    private fun appendPropEntry(sb: StringBuilder, file: File, path: String) {
        val href = if (file.isDirectory && !path.endsWith("/")) "$path/" else path
        sb.append("<D:response>\n")
        sb.append("<D:href>${escapeXml(href)}</D:href>\n")
        sb.append("<D:propstat><D:prop>\n")
        sb.append("<D:displayname>${escapeXml(file.name.ifEmpty { "/" })}</D:displayname>\n")
        sb.append("<D:getlastmodified>${httpDateFormat.format(Date(file.lastModified()))}</D:getlastmodified>\n")
        sb.append("<D:getcontentlength>${if (file.isFile) file.length() else 0}</D:getcontentlength>\n")
        if (file.isDirectory) sb.append("<D:resourcetype><D:collection/></D:resourcetype>\n")
        else sb.append("<D:resourcetype/>\n")
        sb.append("<D:getcontenttype>${if (file.isDirectory) "httpd/unix-directory" else getMimeType(file.name)}</D:getcontenttype>\n")
        sb.append("</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>\n")
        sb.append("</D:response>\n")
    }

    private fun buildDirectoryListing(dir: File, path: String): String {
        val sb = StringBuilder()
        sb.append("<html><head><meta charset='utf-8'><title>WebDAV - ${dir.name}</title></head><body>")
        sb.append("<h2>/${dir.name}</h2><ul>")
        if (path.length > 1) sb.append("<li><a href='../'>..</a></li>")
        dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))?.forEach { f ->
            val name = if (f.isDirectory) "${f.name}/" else f.name
            sb.append("<li><a href='${java.net.URLEncoder.encode(f.name, "UTF-8")}${if (f.isDirectory) "/" else ""}'>$name</a></li>")
        }
        sb.append("</ul></body></html>")
        return sb.toString()
    }

    private fun sendResponse(out: OutputStream, code: Int, status: String, headers: Map<String, String>, body: ByteArray?) {
        val sb = StringBuilder()
        sb.append("HTTP/1.1 $code $status\r\n")
        headers.forEach { (k, v) -> sb.append("$k: $v\r\n") }
        sb.append("Connection: close\r\n\r\n")
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
        if (body != null) out.write(body)
        out.flush()
    }

    private fun sendResponseWithFile(out: OutputStream, code: Int, status: String, file: File, mime: String) {
        val header = "HTTP/1.1 $code $status\r\nContent-Type: $mime\r\nContent-Length: ${file.length()}\r\nConnection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        FileInputStream(file).use { it.copyTo(out) }
        out.flush()
    }

    private fun sendError(out: OutputStream, code: Int, msg: String) {
        val body = "<html><body><h1>$code $msg</h1></body></html>"
        val response = "HTTP/1.1 $code $msg\r\nContent-Type: text/html\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"
        out.write(response.toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun escapeXml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun getMimeType(name: String): String = when (name.substringAfterLast(".").lowercase()) {
        "html", "htm" -> "text/html"
        "txt", "md" -> "text/plain"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp3" -> "audio/mpeg"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "avi" -> "video/x-msvideo"
        "zip" -> "application/zip"
        "apk" -> "application/vnd.android.package-archive"
        else -> "application/octet-stream"
    }
}
